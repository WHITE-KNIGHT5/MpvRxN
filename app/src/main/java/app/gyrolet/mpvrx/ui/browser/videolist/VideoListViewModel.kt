package app.gyrolet.mpvrx.ui.browser.videolist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.domain.playbackstate.repository.PlaybackStateRepository
import app.gyrolet.mpvrx.repository.MediaFileRepository
import app.gyrolet.mpvrx.ui.browser.base.BaseBrowserViewModel
import app.gyrolet.mpvrx.utils.history.RecentlyPlayedOps
import app.gyrolet.mpvrx.utils.media.MediaLibraryEvents
import app.gyrolet.mpvrx.utils.media.MetadataRetrieval
import app.gyrolet.mpvrx.utils.media.PlaybackStateEvents
import app.gyrolet.mpvrx.utils.storage.FolderViewScanner
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import androidx.compose.runtime.Immutable

@Immutable
data class VideoWithPlaybackInfo(
  val video: Video,
  val timeRemaining: Long? = null, // in seconds
  val progressPercentage: Float? = null, // 0.0 to 1.0
  val isOldAndUnplayed: Boolean = false, // true if video is older than threshold and never played
  val isWatched: Boolean = false, // true if video has any playback history
)

class VideoListViewModel(
  application: Application,
  private val bucketId: String,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val appearancePreferences: app.gyrolet.mpvrx.preferences.AppearancePreferences by inject()
  private val browserPreferences: app.gyrolet.mpvrx.preferences.BrowserPreferences by inject()
  private val recentlyPlayedRepository: app.gyrolet.mpvrx.domain.recentlyplayed.repository.RecentlyPlayedRepository by inject()
  // Using MediaFileRepository singleton directly

  private val _videos = MutableStateFlow<List<Video>>(emptyList())
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _videosWithPlaybackInfo = MutableStateFlow<List<VideoWithPlaybackInfo>>(emptyList())
  val videosWithPlaybackInfo: StateFlow<List<VideoWithPlaybackInfo>> = _videosWithPlaybackInfo.asStateFlow()

  private val _isLoading = MutableStateFlow(true)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  // Track if items were deleted/moved leaving folder empty
  private val _videosWereDeletedOrMoved = MutableStateFlow(false)
  val videosWereDeletedOrMoved: StateFlow<Boolean> = _videosWereDeletedOrMoved.asStateFlow()

  val lastPlayedInFolderPath: StateFlow<String?> =
    recentlyPlayedRepository
      .observeRecentlyPlayed(limit = 100)
      .map { recentlyPlayedList ->
        val folderPath = _videos.value.firstOrNull()?.path?.let { File(it).parent }
        if (folderPath != null) {
          recentlyPlayedList.firstOrNull { entity ->
            try {
              File(entity.filePath).parent == folderPath
            } catch (_: Exception) {
              false
            }
          }?.filePath
        } else {
          null
        }
      }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  // Track previous video count to detect if folder became empty
  private var previousVideoCount = 0

  private val tag = "VideoListViewModel"

  init {
    // Check cache immediately — hide loading indicator if cached data exists
    val cached = videoCache[bucketId] ?: loadFromDiskCache(bucketId)
    if (cached != null) {
      videoCache[bucketId] = cached
      _videos.value = cached
      _isLoading.value = false
    }

    loadVideos()

    // Listen for global media library changes and refresh this list when they occur
    viewModelScope.launch(Dispatchers.IO) {
        MediaLibraryEvents.changes.collectLatest {
          // Clear cache when media library changes
          MediaFileRepository.clearCache()
          loadVideos()
        }
      }

    viewModelScope.launch(Dispatchers.IO) {
      PlaybackStateEvents.changes.collectLatest {
        if (_videos.value.isNotEmpty()) {
          loadPlaybackInfo(_videos.value)
        }
      }
    }
  }

  override fun refresh() {
    Log.d(tag, "Hard refreshing video list for bucket: $bucketId")

    // Set loading state
    _isLoading.value = true

    // Clear cache to force fresh data from filesystem
    MediaFileRepository.clearCache()
    FolderViewScanner.clearCache()

    // Trigger media scan before loading to ensure MediaStore is up-to-date
    triggerMediaScan()

    loadVideos(forceFileSystemCheck = true)
  }

  private fun loadVideos(forceFileSystemCheck: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Show cached data instantly — no loading flash
        val cached = videoCache[bucketId] ?: loadFromDiskCache(bucketId)
        if (cached != null && !forceFileSystemCheck) {
          videoCache[bucketId] = cached
          _videos.value = cached
          _isLoading.value = false
          loadPlaybackInfo(cached)
        }

        // First attempt to load videos (basic info from MediaStore)
        var videoList = MediaFileRepository.getVideosInFolder(
          getApplication(),
          bucketId,
          forceFileSystemCheck = forceFileSystemCheck,
        )

        // Keep only video files — removes images, audio, docs that may appear in camera/downloads folders
        videoList = videoList.filter { video ->
          video.mimeType.isBlank() || video.mimeType.startsWith("video/")
        }

        // Enrich with metadata only if chips are enabled
        if (MetadataRetrieval.isVideoMetadataNeeded(browserPreferences)) {
          Log.d(tag, "Metadata chips enabled, enriching ${videoList.size} videos")
          videoList = MetadataRetrieval.enrichVideosIfNeeded(
            context = getApplication(),
            videos = videoList,
            browserPreferences = browserPreferences,
            metadataCache = metadataCache
          )
        } else {
          Log.d(tag, "Metadata chips disabled, skipping metadata extraction")
        }

        // Check if folder became empty after having videos
        if (previousVideoCount > 0 && videoList.isEmpty()) {
          _videosWereDeletedOrMoved.value = true
          Log.d(tag, "Folder became empty (had $previousVideoCount videos before)")
        } else if (videoList.isNotEmpty()) {
          // Reset flag if folder now has videos
          _videosWereDeletedOrMoved.value = false
        }

        // Update previous count
        previousVideoCount = videoList.size

        if (videoList.isEmpty()) {
          Log.d(tag, "No videos found for bucket $bucketId - attempting media rescan")
          triggerMediaScan()
          delay(1000)
          var retryVideoList = MediaFileRepository.getVideosInFolder(
            getApplication(),
            bucketId,
            forceFileSystemCheck = true,
          )

          // Enrich retry list if needed
          if (MetadataRetrieval.isVideoMetadataNeeded(browserPreferences)) {
            retryVideoList = MetadataRetrieval.enrichVideosIfNeeded(
              context = getApplication(),
              videos = retryVideoList,
              browserPreferences = browserPreferences,
              metadataCache = metadataCache
            )
          }

          // Update count after retry
          if (previousVideoCount > 0 && retryVideoList.isEmpty()) {
            _videosWereDeletedOrMoved.value = true
          } else if (retryVideoList.isNotEmpty()) {
            _videosWereDeletedOrMoved.value = false
          }
          previousVideoCount = retryVideoList.size

          _videos.value = retryVideoList
          videoCache[bucketId] = retryVideoList
          saveToDiskCache(bucketId, retryVideoList)
          loadPlaybackInfo(retryVideoList)
        } else {
          _videos.value = videoList
          videoCache[bucketId] = videoList
          saveToDiskCache(bucketId, videoList)
          loadPlaybackInfo(videoList)
        }
      } catch (e: Exception) {
        Log.e(tag, "Error loading videos for bucket $bucketId", e)
        _videos.value = emptyList()
        _videosWithPlaybackInfo.value = emptyList()
      } finally {
        _isLoading.value = false
      }
    }
  }

  /**
   * Set flag indicating videos were deleted or moved
   */
  fun setVideosWereDeletedOrMoved() {
    _videosWereDeletedOrMoved.value = true
  }

  private suspend fun loadPlaybackInfo(videos: List<Video>) {
    val watchedThreshold = browserPreferences.watchedThreshold.get()

    // ONE single DB call for all videos instead of one per video
    val allPlaybackStates = playbackStateRepository.getAllPlaybackStates()
      .associateBy { it.mediaTitle }

    val videosWithInfo = videos.map { video ->
      val playbackState = allPlaybackStates[video.displayName]

      val progress = if (playbackState != null && video.duration > 0) {
        val durationSeconds = video.duration / 1000
        val timeRemaining = playbackState.timeRemaining.toLong()
        val watched = durationSeconds - timeRemaining
        val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        if (progressValue in 0.01f..0.99f) progressValue else null
      } else null

      val isOldAndUnplayed = playbackState == null

      val isWatched = if (playbackState != null && video.duration > 0) {
        val durationSeconds = video.duration / 1000
        val timeRemaining = playbackState.timeRemaining.toLong()
        val watched = durationSeconds - timeRemaining
        val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        val calculatedWatched = progressValue >= (watchedThreshold / 100f)
        playbackState.hasBeenWatched || calculatedWatched
      } else false

      VideoWithPlaybackInfo(
        video = video,
        timeRemaining = playbackState?.timeRemaining?.toLong(),
        progressPercentage = progress,
        isOldAndUnplayed = isOldAndUnplayed,
        isWatched = isWatched,
      )
    }
    _videosWithPlaybackInfo.value = videosWithInfo
  }

  private fun triggerMediaScan() {
    try {
      // Trigger a targeted media scan for the specific folder
      val folder = File(bucketId)
      
      if (folder.exists() && folder.isDirectory) {
        // Scan all video files in the folder
        val videoFiles = folder.listFiles { file ->
          file.isFile && file.extension.lowercase() in listOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpg", "mpeg", "ts", "m2ts"
          )
        }
        
        if (!videoFiles.isNullOrEmpty()) {
          val filePaths = videoFiles.map { it.absolutePath }.toTypedArray()
          
          android.media.MediaScannerConnection.scanFile(
            getApplication(),
            filePaths,
            null, // Let MediaScanner detect MIME types
          ) { path, uri ->
            Log.d(tag, "Media scan completed for: $path -> $uri")
          }
          
          Log.d(tag, "Triggered media scan for ${filePaths.size} files in: $bucketId")
        } else {
          Log.d(tag, "No video files found in folder: $bucketId")
        }
      } else {
        // Fallback to scanning external storage root
        val externalStorage = android.os.Environment.getExternalStorageDirectory()
        android.media.MediaScannerConnection.scanFile(
          getApplication(),
          arrayOf(externalStorage.absolutePath),
          arrayOf("video/*"),
        ) { path, uri ->
          Log.d(tag, "Media scan completed for: $path -> $uri")
        }
        Log.d(tag, "Triggered media scan for: ${externalStorage.absolutePath}")
      }
    } catch (e: Exception) {
      Log.e(tag, "Failed to trigger media scan", e)
    }
  }

  companion object {
    // In-memory cache — instant within same app session
    private val videoCache = mutableMapOf<String, List<Video>>()
    private const val PREFS_NAME = "video_list_cache"
    private const val CACHE_VERSION = 1

    fun clearCache(bucketId: String? = null) {
      if (bucketId != null) videoCache.remove(bucketId)
      else videoCache.clear()
    }

    fun factory(
      application: Application,
      bucketId: String,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T = VideoListViewModel(application, bucketId) as T
    }
  }

  // Save videos to disk cache
  private fun saveToDiskCache(bucketId: String, videos: List<Video>) {
    try {
      val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val array = org.json.JSONArray()
      for (v in videos) {
        val obj = org.json.JSONObject().apply {
          put("id", v.id)
          put("title", v.title)
          put("displayName", v.displayName)
          put("path", v.path)
          put("uri", v.uri.toString())
          put("duration", v.duration)
          put("durationFormatted", v.durationFormatted)
          put("size", v.size)
          put("sizeFormatted", v.sizeFormatted)
          put("dateModified", v.dateModified)
          put("dateAdded", v.dateAdded)
          put("mimeType", v.mimeType)
          put("bucketId", v.bucketId)
          put("bucketDisplayName", v.bucketDisplayName)
          put("width", v.width)
          put("height", v.height)
          put("fps", v.fps.toDouble())
          put("resolution", v.resolution)
          put("hasEmbeddedSubtitles", v.hasEmbeddedSubtitles)
          put("subtitleCodec", v.subtitleCodec)
        }
        array.put(obj)
      }
      prefs.edit()
        .putString("videos_$bucketId", array.toString())
        .putInt("version_$bucketId", CACHE_VERSION)
        .commit()
    } catch (e: Exception) {
      // Cache write failed — not critical
    }
  }

  // Load videos from disk cache
  private fun loadFromDiskCache(bucketId: String): List<Video>? {
    return try {
      val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val version = prefs.getInt("version_$bucketId", 0)
      if (version != CACHE_VERSION) return null
      val json = prefs.getString("videos_$bucketId", null) ?: return null
      val array = org.json.JSONArray(json)
      val videos = mutableListOf<Video>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        videos.add(Video(
          id = obj.getLong("id"),
          title = obj.getString("title"),
          displayName = obj.getString("displayName"),
          path = obj.getString("path"),
          uri = Uri.parse(obj.getString("uri")),
          duration = obj.getLong("duration"),
          durationFormatted = obj.getString("durationFormatted"),
          size = obj.getLong("size"),
          sizeFormatted = obj.getString("sizeFormatted"),
          dateModified = obj.getLong("dateModified"),
          dateAdded = obj.getLong("dateAdded"),
          mimeType = obj.getString("mimeType"),
          bucketId = obj.getString("bucketId"),
          bucketDisplayName = obj.getString("bucketDisplayName"),
          width = obj.getInt("width"),
          height = obj.getInt("height"),
          fps = obj.getDouble("fps").toFloat(),
          resolution = obj.getString("resolution"),
          hasEmbeddedSubtitles = obj.getBoolean("hasEmbeddedSubtitles"),
          subtitleCodec = obj.getString("subtitleCodec"),
        ))
      }
      if (videos.isEmpty()) null else videos
    } catch (e: Exception) {
      null
    }
  }
}

