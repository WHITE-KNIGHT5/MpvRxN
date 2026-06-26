package app.gyrolet.mpvrx.domain.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.domain.network.NetworkConnection
import app.gyrolet.mpvrx.ui.browser.networkstreaming.proxy.NetworkStreamingProxy
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.concurrent.ConcurrentHashMap

class ThumbnailRepository(
  private val context: Context,
  private val imageLoader: ImageLoader,
) {
  private val appearancePreferences by lazy {
    KoinJavaComponent.get<app.gyrolet.mpvrx.preferences.AppearancePreferences>(
      app.gyrolet.mpvrx.preferences.AppearancePreferences::class.java,
    )
  }
  private val browserPreferences by lazy {
    KoinJavaComponent.get<app.gyrolet.mpvrx.preferences.BrowserPreferences>(
      app.gyrolet.mpvrx.preferences.BrowserPreferences::class.java,
    )
  }

  private val memoryCache: LruCache<String, Bitmap>
  private val ongoingOperations = ConcurrentHashMap<String, Deferred<Bitmap?>>()
  private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val maxConcurrentFolders = 3
  private val generationSemaphore = Semaphore(1)
  private val maxFolderBatchSize = 48
  private val generationFrameDelayMs = 12L

  private data class FolderState(
    val signature: String,
    @Volatile var nextIndex: Int = 0,
  )

  private val folderStates = ConcurrentHashMap<String, FolderState>()
  private val folderJobs = ConcurrentHashMap<String, Job>()

  // Track network URLs where all extraction strategies have failed – avoids endless retries while scrolling
  private val networkThumbnailFailed = ConcurrentHashMap<String, Boolean>()

  private val _thumbnailReadyKeys =
    MutableSharedFlow<String>(
      extraBufferCapacity = 256,
    )
  val thumbnailReadyKeys: SharedFlow<String> = _thumbnailReadyKeys.asSharedFlow()

  init {
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    val cacheSizeKb = maxMemoryKb / 6
    memoryCache =
      object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(
          key: String,
          value: Bitmap,
        ): Int = value.byteCount / 1024
      }
  }

  suspend fun getThumbnail(
    video: Video,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      val key = thumbnailKey(video, widthPx, heightPx)

      if (isNetworkUrl(video.path) && !appearancePreferences.showNetworkThumbnails.get()) {
        return@withContext null
      }

      synchronized(memoryCache) {
        memoryCache.get(key)
      }?.let { return@withContext it }

      ongoingOperations[key]?.let { return@withContext it.await() }

      val deferred =
        async {
          try {
            getCachedThumbnail(video, widthPx, heightPx)?.let { cached ->
              android.util.Log.d("ThumbnailRepository", "Using CACHED thumbnail (no fresh attempt) for ${video.path}")
              synchronized(memoryCache) {
                memoryCache.put(key, cached)
              }
              _thumbnailReadyKeys.tryEmit(key)
              return@async cached
            }
            android.util.Log.d("ThumbnailRepository", "No cache hit, attempting fresh extraction for ${video.path}")

            val bitmap = generationSemaphore.withPermit {
              // Coil's video thumbnail decoder only supports file/content sources. For network videos,
              // extract a frame directly via MediaMetadataRetriever's HTTP data source.
              if (isHttpUrl(video.path)) {
                getOrCreateNetworkVideoThumbnail(video, widthPx, heightPx)?.let { bmp ->
                  synchronized(memoryCache) { memoryCache.put(key, bmp) }
                  _thumbnailReadyKeys.tryEmit(key)
                  return@withPermit bmp
                }
              }

              // Audio embedded album art is a fork feature (not in upstream) —
              // keep using MediaMetadataRetriever for that, it's the right tool
              // for reading ID3/embedded pictures.
              val ext = video.path.substringAfterLast('.').lowercase()
              val isAudio = video.mimeType.startsWith("audio/") ||
                app.gyrolet.mpvrx.utils.storage.FileTypeUtils.AUDIO_EXTENSIONS.contains(ext)

              if (isAudio) {
                val localBitmap = extractLocalMediaThumbnail(video, isAudio = true)
                return@withPermit localBitmap?.let { scaleBitmap(it, widthPx, heightPx) }
              }

              // Local VIDEO: use Coil first, exactly like upstream does — this
              // is what picks a sensible frame (e.g. the intro) instead of a
              // fixed 10%-into-the-file position, and appears to handle
              // HEVC/AV1 more reliably than calling MediaMetadataRetriever
              // directly. MediaMetadataRetriever is now only a fallback for
              // the rare case Coil itself fails.
              val coilResult =
                runCatching {
                  imageLoader.execute(buildRequest(video))
                }.getOrNull() as? SuccessResult
              if (coilResult != null) {
                return@withPermit scaleBitmap(coilResult.image.toBitmap(), widthPx, heightPx)
              }

              android.util.Log.d("ThumbnailRepository", "Coil failed for local video, trying MediaMetadataRetriever fallback: ${video.path}")
              val fallbackBitmap = extractLocalMediaThumbnail(video, isAudio = false)
              return@withPermit fallbackBitmap?.let { scaleBitmap(it, widthPx, heightPx) }
              val result =
                runCatching {
                  imageLoader.execute(buildRequest(video))
                }.getOrNull() as? SuccessResult ?: return@withPermit null

              scaleBitmap(result.image.toBitmap(), widthPx, heightPx)
            } ?: return@async null

            synchronized(memoryCache) {
              memoryCache.put(key, bitmap)
            }
            _thumbnailReadyKeys.tryEmit(key)
            bitmap
          } finally {
            ongoingOperations.remove(key)
          }
        }

      ongoingOperations[key] = deferred
      deferred.await()
    }

  suspend fun getCachedThumbnail(
    video: Video,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      if (isNetworkUrl(video.path) && !appearancePreferences.showNetworkThumbnails.get()) {
        return@withContext null
      }

      val key = thumbnailKey(video, widthPx, heightPx)
      synchronized(memoryCache) {
        memoryCache.get(key)
      }?.let { return@withContext it }

      val snapshot = imageLoader.diskCache?.openSnapshot(diskCacheKey(video)) ?: return@withContext null
      snapshot.use {
        val file = it.data.toFile()
        val decoded =
          runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val sampleSize = calculateThumbnailSampleSize(options.outWidth, options.outHeight)
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
              inSampleSize = sampleSize
              inPreferredConfig = Bitmap.Config.RGB_565
            })
          }.getOrNull() ?: return@withContext null

        val scaled = scaleBitmap(decoded, widthPx, heightPx)
        synchronized(memoryCache) {
          memoryCache.put(key, scaled)
        }
        return@withContext scaled
      }
    }

  fun getThumbnailFromMemory(
    video: Video,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? {
    if (isNetworkUrl(video.path) && !appearancePreferences.showNetworkThumbnails.get()) {
      return null
    }

    val key = thumbnailKey(video, widthPx, heightPx)
    return synchronized(memoryCache) {
      memoryCache.get(key)
    }
  }

  fun clearThumbnailCache() {
    folderJobs.values.forEach { it.cancel() }
    folderJobs.clear()
    folderStates.clear()
    ongoingOperations.clear()
    networkThumbnailFailed.clear()

    synchronized(memoryCache) {
      memoryCache.evictAll()
    }

    imageLoader.memoryCache?.clear()
    imageLoader.diskCache?.clear()
    runCatching { File(context.cacheDir, "thumbnails").deleteRecursively() }
    runCatching { File(context.filesDir, "thumbnails").deleteRecursively() }
  }

  fun startFolderThumbnailGeneration(
    folderId: String,
    videos: List<Video>,
    widthPx: Int,
    heightPx: Int,
  ) {
    val filteredVideos =
      if (appearancePreferences.showNetworkThumbnails.get()) {
        videos
      } else {
        videos.filterNot { isNetworkUrl(it.path) }
      }.take(maxFolderBatchSize)

    if (filteredVideos.isEmpty()) {
      return
    }

    folderJobs.entries.removeAll { !it.value.isActive }

    if (folderJobs.size >= maxConcurrentFolders && !folderJobs.containsKey(folderId)) {
      folderJobs.entries.firstOrNull()?.let { (oldestId, job) ->
        job.cancel()
        folderJobs.remove(oldestId)
        folderStates.remove(oldestId)
      }
    }

    val signature = folderSignature(filteredVideos, widthPx, heightPx)
    val existingState = folderStates[folderId]
    val shouldRestart = existingState == null || existingState.signature != signature

    val state =
      folderStates.compute(folderId) { _, existing ->
        if (existing == null || existing.signature != signature) {
          FolderState(signature = signature, nextIndex = 0)
        } else {
          existing
        }
      }!!

    // Only cancel and restart if the signature changed (video list changed)
    // Otherwise, let the existing job continue to avoid overhead
    if (shouldRestart) {
      folderJobs.remove(folderId)?.cancel()
      folderJobs[folderId] =
        repositoryScope.launch {
          var i = state.nextIndex
          while (i < filteredVideos.size) {
            getThumbnail(filteredVideos[i], widthPx, heightPx)
            i++
            state.nextIndex = i
            delay(generationFrameDelayMs)
          }
        }
    }
  }

  fun thumbnailKey(
    video: Video,
    width: Int,
    height: Int,
  ): String = "${videoBaseKey(video)}|$width|$height|${thumbnailModeKey()}"

  fun diskCacheKey(video: Video): String = "video-thumb|${videoBaseKey(video)}|${thumbnailModeKey()}"

  private fun videoBaseKey(video: Video): String {
    if (isNetworkUrl(video.path)) {
      val base = video.path.ifBlank { video.uri.toString() }
      return "$base|network"
    }

    val artworkSignature =
      EmbeddedArtworkCandidates.forVideoPath(video.path)
        .asSequence()
        .map(::File)
        .firstOrNull { it.isFile && it.canRead() }
        ?.let { artwork -> "|art:${artwork.name}:${artwork.length()}:${artwork.lastModified()}" }
        .orEmpty()

    return "${video.size}|${video.dateModified}|${video.duration}$artworkSignature"
  }

  private fun buildRequest(video: Video): ImageRequest =
    ImageRequest.Builder(context)
      .data(requestData(video))
      .memoryCacheKey(diskCacheKey(video))
      .diskCacheKey(diskCacheKey(video))
      .build()

  private fun requestData(video: Video): Any =
    when {
      isNetworkUrl(video.path) -> video.path
      video.uri.scheme == "content" || video.uri.scheme == "file" -> video.uri
      video.path.isNotBlank() -> File(video.path)
      else -> video.uri
    }

  private fun scaleBitmap(
    bitmap: Bitmap,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap {
    if (widthPx <= 0 || heightPx <= 0 || bitmap.isRecycled) {
      return bitmap
    }

    val scale = max(widthPx / bitmap.width.toFloat(), heightPx / bitmap.height.toFloat())
    if (scale >= 1f && bitmap.width <= widthPx * 2 && bitmap.height <= heightPx * 2) {
      return bitmap
    }

    val scaledWidth = max(1, (bitmap.width * scale).roundToInt())
    val scaledHeight = max(1, (bitmap.height * scale).roundToInt())
    val scaled = try {
      Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    } catch (_: IllegalArgumentException) {
      // Bitmap was recycled between the check and the scale call
      return bitmap
    }
    if (scaled != bitmap && !bitmap.isRecycled) {
      bitmap.recycle()
    }
    return scaled
  }

  private fun isNetworkUrl(path: String): Boolean =
    path.startsWith("http://", ignoreCase = true) ||
      path.startsWith("https://", ignoreCase = true) ||
      path.startsWith("rtmp://", ignoreCase = true) ||
      path.startsWith("rtsp://", ignoreCase = true) ||
      path.startsWith("ftp://", ignoreCase = true) ||
      path.startsWith("sftp://", ignoreCase = true) ||
      path.startsWith("smb://", ignoreCase = true)

  private fun isHttpUrl(path: String): Boolean =
    path.startsWith("http://", ignoreCase = true) ||
      path.startsWith("https://", ignoreCase = true)

  private fun getOrCreateNetworkVideoThumbnail(
    video: Video,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? {
    // Disk cache hit (reuses the same key as local thumbnails).
    imageLoader.diskCache?.openSnapshot(diskCacheKey(video))?.use { snapshot ->
      BitmapFactory.decodeStream(snapshot.data.toFile().inputStream())?.let { bmp ->
        return scaleBitmap(bmp, widthPx, heightPx)
      }
    }

    val strategy =
      browserPreferences.thumbnailMode.get().toThumbnailStrategy(
        browserPreferences.thumbnailFramePosition.get(),
      )

    val rotated =
      extractNetworkVideoFrame(
        url = video.path,
        strategy = strategy,
        targetWidth = widthPx.takeIf { it > 0 },
        targetHeight = heightPx.takeIf { it > 0 },
      ) ?: return null

    // Write full-size (or retriever-scaled) bitmap to disk cache, then return a per-request scaled copy.
    imageLoader.diskCache?.openEditor(diskCacheKey(video))?.let { editor ->
      try {
        editor.data.toFile().outputStream().use { out ->
          rotated.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        editor.commit()
      } catch (_: Exception) {
        runCatching { editor.abort() }
      }
    }

    return scaleBitmap(rotated, widthPx, heightPx)
  }

  private fun extractNetworkVideoFrame(
    url: String,
    strategy: ThumbnailStrategy,
    targetWidth: Int?,
    targetHeight: Int?,
  ): Bitmap? =
    runCatching {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(url, networkVideoHeaders())

        val embeddedPicture =
          if (strategy.prefersEmbeddedPicture()) {
            EmbeddedArtworkResolver.decodeRetrieverArtwork(retriever)
          } else {
            null
          }

        val timeUs =
          when (strategy) {
            ThumbnailStrategy.FirstFrame -> 0L
            is ThumbnailStrategy.FrameAtPercentage -> frameTimeMicros(retriever, strategy.percentage)
            is ThumbnailStrategy.Hybrid -> 0L
            is ThumbnailStrategy.EmbeddedOrHybrid -> 0L
            ThumbnailStrategy.EmbeddedOrFirstFrame -> 0L
          }

        var shouldRotate = true
        val raw =
          when (strategy) {
            ThumbnailStrategy.EmbeddedOrFirstFrame ->
              embeddedPicture?.also { shouldRotate = false } ?: getFrameAt(retriever, timeUs, targetWidth, targetHeight)
            ThumbnailStrategy.FirstFrame -> getFrameAt(retriever, 0L, targetWidth, targetHeight)
            is ThumbnailStrategy.FrameAtPercentage -> getFrameAt(retriever, timeUs, targetWidth, targetHeight)
            is ThumbnailStrategy.Hybrid -> {
              val first = getFrameAt(retriever, 0L, targetWidth, targetHeight) ?: return@runCatching null
              if (isMostlySolidThumbnail(first)) {
                first.recycle()
                getFrameAt(retriever, frameTimeMicros(retriever, strategy.percentage), targetWidth, targetHeight)
              } else {
                first
              }
            }
            is ThumbnailStrategy.EmbeddedOrHybrid ->
              embeddedPicture?.also { shouldRotate = false } ?: run {
                val first = getFrameAt(retriever, 0L, targetWidth, targetHeight) ?: return@runCatching null
                if (isMostlySolidThumbnail(first)) {
                  first.recycle()
                  getFrameAt(retriever, frameTimeMicros(retriever, strategy.percentage), targetWidth, targetHeight)
                } else {
                  first
                }
              }
          } ?: return@runCatching null

        if (shouldRotate) rotateBitmapIfNeeded(retriever, raw) else raw
      } finally {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) retriever.close() else retriever.release()
      }
    }.getOrNull()

  private fun getFrameAt(
    retriever: MediaMetadataRetriever,
    timeUs: Long,
    targetWidth: Int?,
    targetHeight: Int?,
  ): Bitmap? {
    val w = targetWidth ?: return retriever.getFrameAtTime(timeUs)
    val h = targetHeight ?: return retriever.getFrameAtTime(timeUs)
    if (w <= 0 || h <= 0) {
      return retriever.getFrameAtTime(timeUs)
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      runCatching { retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h) }.getOrNull()
        ?: retriever.getFrameAtTime(timeUs)
    } else {
      retriever.getFrameAtTime(timeUs)
    }
  }

  private fun frameTimeMicros(
    retriever: MediaMetadataRetriever,
    percentage: Float,
  ): Long {
    val durationMs =
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    return (durationMs * percentage.coerceIn(0f, 1f) * 1000).toLong()
  }

  private fun rotateBitmapIfNeeded(
    retriever: MediaMetadataRetriever,
    bitmap: Bitmap,
  ): Bitmap {
    val rotation =
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
        ?: return bitmap
    if (rotation == 0) {
      return bitmap
    }

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) {
      bitmap.recycle()
    }
    return rotated
  }

  private fun networkVideoHeaders(): Map<String, String> =
    mapOf(
      // Some servers refuse requests without a UA. MediaMetadataRetriever handles the rest.
      "User-Agent" to "Mozilla/5.0 (Android) MpvRx",
      "Accept" to "*/*",
    )

  /**
   * Retrieve a thumbnail for a raw network file path (for use from [NetworkVideoCard]).
   * For HTTP/HTTPS URLs, uses [MediaMetadataRetriever]'s built-in HTTP streaming.
   * For other protocols (SMB, FTP, WebDAV), uses [NetworkStreamingProxy] to create
   * a local HTTP stream and then extracts the frame.
   * Respects the [showNetworkThumbnails] preference gate.
   */
  suspend fun getThumbnailForNetworkPath(
    path: String,
    widthPx: Int,
    heightPx: Int,
    connection: NetworkConnection? = null,
  ): Bitmap? = withContext(Dispatchers.IO) {
    if (!appearancePreferences.showNetworkThumbnails.get()) return@withContext null

    // For non-HTTP paths (SMB, FTP, WebDAV), use the proxy to create a local HTTP stream
    if (!isHttpUrl(path)) {
      if (connection == null) return@withContext null
      return@withContext getNonHttpNetworkThumbnail(path, connection, widthPx, heightPx)
    }

    // Check if this network URL has previously failed all extraction strategies
    val videoKey = path.hashCode().toString()
    if (networkThumbnailFailed.containsKey(videoKey)) {
      android.util.Log.d("ThumbnailRepository", "Skipping network thumbnail (previously failed): $path")
      return@withContext null
    }

    val memKey  = "$path|network|$widthPx|$heightPx|${thumbnailModeKey()}"
    val diskKey = "video-thumb|$path|network|${thumbnailModeKey()}"

    // Memory cache hit
    synchronized(memoryCache) { memoryCache.get(memKey) }?.let { return@withContext it }

    // Disk cache hit
    imageLoader.diskCache?.openSnapshot(diskKey)?.use { snapshot ->
      BitmapFactory.decodeStream(snapshot.data.toFile().inputStream())?.let { bmp ->
        val scaled = scaleBitmap(bmp, widthPx, heightPx)
        synchronized(memoryCache) { memoryCache.put(memKey, scaled) }
        return@withContext scaled
      }
    }

    val strategy =
      browserPreferences.thumbnailMode.get().toThumbnailStrategy(
        browserPreferences.thumbnailFramePosition.get(),
      )

    // Extract directly via MediaMetadataRetriever HTTP streaming (efficient — only seeks header bytes)
    val bitmap = generationSemaphore.withPermit {
      extractNetworkVideoFrame(
        url = path,
        strategy = strategy,
        targetWidth = widthPx.takeIf { it > 0 },
        targetHeight = heightPx.takeIf { it > 0 },
      )?.let { scaleBitmap(it, widthPx, heightPx) }
    }

    if (bitmap == null) {
      android.util.Log.w("ThumbnailRepository", "All strategies failed for network stream $path")
      networkThumbnailFailed[videoKey] = true
      return@withContext null
    }

    // Write to disk cache
    imageLoader.diskCache?.openEditor(diskKey)?.let { editor ->
      try {
        editor.data.toFile().outputStream().use { out ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        editor.commit()
      } catch (_: Exception) {
        runCatching { editor.abort() }
      }
    }

    synchronized(memoryCache) { memoryCache.put(memKey, bitmap) }
    _thumbnailReadyKeys.tryEmit(memKey)
    bitmap
  }

  private suspend fun getNonHttpNetworkThumbnail(
    path: String,
    connection: NetworkConnection,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? {
    val videoKey = path.hashCode().toString()
    if (networkThumbnailFailed.containsKey(videoKey)) {
      android.util.Log.d("ThumbnailRepository", "Skipping network thumbnail (previously failed): $path")
      return null
    }

    val memKey = "$path|network|$widthPx|$heightPx|${thumbnailModeKey()}"
    val diskKey = "video-thumb|$path|network|${thumbnailModeKey()}"

    // Memory cache hit
    synchronized(memoryCache) { memoryCache.get(memKey) }?.let { return it }

    // Disk cache hit
    imageLoader.diskCache?.openSnapshot(diskKey)?.use { snapshot ->
      BitmapFactory.decodeStream(snapshot.data.toFile().inputStream())?.let { bmp ->
        val scaled = scaleBitmap(bmp, widthPx, heightPx)
        synchronized(memoryCache) { memoryCache.put(memKey, scaled) }
        return scaled
      }
    }

    val strategy =
      browserPreferences.thumbnailMode.get().toThumbnailStrategy(
        browserPreferences.thumbnailFramePosition.get(),
      )

    val bitmap = generationSemaphore.withPermit {
      extractNetworkVideoFrameViaProxy(path, connection, strategy, widthPx, heightPx)
        ?.let { scaleBitmap(it, widthPx, heightPx) }
    }

    if (bitmap == null) {
      android.util.Log.w("ThumbnailRepository", "All strategies failed for network path $path")
      networkThumbnailFailed[videoKey] = true
      return null
    }

    // Write to disk cache
    imageLoader.diskCache?.openEditor(diskKey)?.let { editor ->
      try {
        editor.data.toFile().outputStream().use { out ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        editor.commit()
      } catch (_: Exception) {
        runCatching { editor.abort() }
      }
    }

    synchronized(memoryCache) { memoryCache.put(memKey, bitmap) }
    _thumbnailReadyKeys.tryEmit(memKey)
    return bitmap
  }

  private fun extractNetworkVideoFrameViaProxy(
    path: String,
    connection: NetworkConnection,
    strategy: ThumbnailStrategy,
    targetWidth: Int,
    targetHeight: Int,
  ): Bitmap? {
    val proxy = NetworkStreamingProxy.getInstance()
    val streamId = "thumb_${path.hashCode()}_${System.nanoTime()}"

    return try {
      val localUrl = proxy.registerStream(
        streamId = streamId,
        connection = connection,
        filePath = path,
      )

      extractNetworkVideoFrame(
        url = localUrl,
        strategy = strategy,
        targetWidth = targetWidth.takeIf { it > 0 },
        targetHeight = targetHeight.takeIf { it > 0 },
      )
    } catch (_: Exception) {
      null
    } finally {
      proxy.unregisterStream(streamId)
    }
  }

  /** The memory-cache key used by [getThumbnailForNetworkPath]. */
  fun thumbnailKeyForNetworkPath(path: String, widthPx: Int, heightPx: Int): String =
    "$path|network|$widthPx|$heightPx|${thumbnailModeKey()}"

  /**
   * Get a thumbnail for a folder using the first video in the folder.
   * Returns null if the folder has no videos or thumbnail generation fails.
   */
  suspend fun getFolderThumbnail(
    folderId: String,
    videos: List<Video>,
    widthPx: Int,
    heightPx: Int,
  ): Bitmap? = withContext(Dispatchers.IO) {
    if (videos.isEmpty()) return@withContext null

    // Filter out network videos if network thumbnails are disabled
    val filteredVideos =
      if (appearancePreferences.showNetworkThumbnails.get()) {
        videos
      } else {
        videos.filterNot { isNetworkUrl(it.path) }
      }

    if (filteredVideos.isEmpty()) return@withContext null

    // Use the first video as the folder thumbnail
    getThumbnail(filteredVideos.first(), widthPx, heightPx)
  }

  private fun folderSignature(
    videos: List<Video>,
    widthPx: Int,
    heightPx: Int,
  ): String {
    val md = MessageDigest.getInstance("MD5")
    md.update("$widthPx|$heightPx|${thumbnailModeKey()}|".toByteArray())
    for (video in videos) {
      md.update(video.path.toByteArray())
      md.update("|".toByteArray())
      md.update(video.size.toString().toByteArray())
      md.update("|".toByteArray())
      md.update(video.dateModified.toString().toByteArray())
      md.update(";".toByteArray())
    }
    return md.digest().joinToString("") { byte -> "%02x".format(byte) }
  }

  private fun thumbnailModeKey(): String =
    browserPreferences.thumbnailMode.get().thumbnailModeCacheKey(browserPreferences.thumbnailFramePosition.get())

  /**
   * Extracts thumbnail directly via MediaMetadataRetriever for:
   * - Audio files → embedded album art (ID3 tags, FLAC art, etc.)
   * - MKV files → video frame at 10% of duration
   */
  private fun extractLocalMediaThumbnail(video: Video, isAudio: Boolean): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
      // Use file path if available, otherwise fall back to URI
      if (video.path.isNotBlank() && java.io.File(video.path).exists()) {
        retriever.setDataSource(video.path)
      } else {
        retriever.setDataSource(context, video.uri)
      }
      android.util.Log.d("ThumbnailRepository", "setDataSource OK: ${video.path}")

      if (isAudio) {
        // Extract embedded album art (works for mp3, flac, m4a, ogg, opus, etc.)
        val pic = retriever.embeddedPicture
        pic?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
      } else {
        // Extract video frame at 10% of duration
        val durationMs = retriever
          .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
          ?.toLongOrNull() ?: 0L
        val timeUs = (durationMs * 1000L * 0.1).toLong()
        android.util.Log.d("ThumbnailRepository", "durationMs=$durationMs timeUs=$timeUs for ${video.path}")

        fun frameAt(time: Long, option: Int): Bitmap? = runCatching {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(time, option, 512, 512)
          } else {
            retriever.getFrameAtTime(time, option)
          }
        }.fold(
          onSuccess = { result ->
            android.util.Log.d("ThumbnailRepository", "frameAt time=$time option=$option result=${if (result != null) "bitmap ${result.width}x${result.height}" else "null"} for ${video.path}")
            result
          },
          onFailure = { e ->
            android.util.Log.w("ThumbnailRepository", "frameAt time=$time option=$option threw for ${video.path}", e)
            null
          },
        )

        // OPTION_CLOSEST_SYNC only seeks to the nearest keyframe — fails on
        // many HEVC/AV1 files with sparse keyframes or device decoder quirks,
        // even though mpv plays them fine. OPTION_CLOSEST forces an exact
        // decode to the target time instead — slower, far more reliable.
        // Falls back to frame 0 as a last resort for files that only
        // decode cleanly from the very start.
        val result = frameAt(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
          ?: frameAt(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
          ?: frameAt(0L, MediaMetadataRetriever.OPTION_CLOSEST)
        if (result == null) {
          android.util.Log.w("ThumbnailRepository", "ALL frame extraction tiers returned null for ${video.path}")
        }
        result
      }
    } catch (e: Exception) {
      android.util.Log.w("ThumbnailRepository", "extractLocalMediaThumbnail failed: ${video.path}", e)
      null
    } finally {
      retriever.release()
    }
  }
}


