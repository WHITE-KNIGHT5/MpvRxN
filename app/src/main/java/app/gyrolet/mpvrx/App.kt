package app.gyrolet.mpvrx

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import app.gyrolet.mpvrx.database.repository.VideoMetadataCacheRepository
import app.gyrolet.mpvrx.di.DatabaseModule
import app.gyrolet.mpvrx.di.FileManagerModule
import app.gyrolet.mpvrx.di.PreferencesModule
import app.gyrolet.mpvrx.preferences.PlayerPreferences
import app.gyrolet.mpvrx.presentation.crash.CrashActivity
import app.gyrolet.mpvrx.presentation.crash.GlobalExceptionHandler
import app.gyrolet.mpvrx.utils.media.MediaLibraryEvents
import app.gyrolet.mpvrx.utils.storage.FileTypeUtils
import coil.ImageLoader
import coil.SingletonImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.startKoin

/**
 * Custom Coil fetcher that extracts thumbnails for:
 * - Audio files (mp3, flac, aac, etc.) → embedded album art
 * - MKV and other video files → frame at 10% duration
 *
 * Coil's default fetcher doesn't support MKV thumbnails on all devices
 * and has no album art support for audio files.
 */
private class MediaThumbnailFetcher(
  private val uri: Uri,
  private val context: Context,
) : Fetcher {

  override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
    val path = uri.path ?: return@withContext null
    val extension = path.substringAfterLast('.').lowercase()
    val isAudio = FileTypeUtils.AUDIO_EXTENSIONS.contains(extension)
    val isVideo = FileTypeUtils.VIDEO_EXTENSIONS.contains(extension)
    if (!isAudio && !isVideo) return@withContext null

    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(context, uri)
      val bitmap = if (isAudio) {
        // Extract embedded album art (ID3 tags etc.)
        val pic = retriever.embeddedPicture
        pic?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
      } else {
        // Extract video frame at 10% of duration
        val durationMs = retriever
          .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
          ?.toLongOrNull() ?: 0L
        val timeUs = (durationMs * 1000L * 0.1).toLong()
        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
      }

      bitmap?.let {
        DrawableResult(
          drawable = BitmapDrawable(context.resources, it),
          isSampled = false,
          dataSource = DataSource.DISK,
        )
      }
    } catch (e: Exception) {
      Log.w("MediaThumbnailFetcher", "Failed to extract thumbnail for $uri", e)
      null
    } finally {
      retriever.release()
    }
  }

  class Factory : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
      val path = data.path ?: return null
      val extension = path.substringAfterLast('.').lowercase()
      val isAudio = FileTypeUtils.AUDIO_EXTENSIONS.contains(extension)
      val isVideo = FileTypeUtils.VIDEO_EXTENSIONS.contains(extension)
      if (!isAudio && !isVideo) return null
      return MediaThumbnailFetcher(data, options.context)
    }
  }
}

@OptIn(KoinExperimentalAPI::class)
class App : Application(), SingletonImageLoader.Factory {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val metadataCache: VideoMetadataCacheRepository by inject()

  companion object {
    private const val LAUNCH_SCAN_PREFS = "launch_media_scan"
    private const val LAST_LAUNCH_SCAN_MS = "last_launch_scan_ms"
    private const val LAUNCH_SCAN_INTERVAL_MS = 24L * 60L * 60L * 1000L
  }

  // Register custom Coil ImageLoader with MKV + audio thumbnail support
  override fun newImageLoader(): ImageLoader =
    ImageLoader.Builder(this)
      .components {
        // Custom fetcher takes priority — handles audio album art + MKV frames
        add(MediaThumbnailFetcher.Factory())
      }
      .crossfade(true)
      .build()

  override fun onCreate() {
    super.onCreate()

    // Initialize Koin
    startKoin {
      androidContext(this@App)
      modules(
        PreferencesModule,
        DatabaseModule,
        FileManagerModule,
        app.gyrolet.mpvrx.di.domainModule,
      )
    }

    Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))

    applicationScope.launch {
      runCatching {
        val preferences: PlayerPreferences by inject()
        val enableMediaInfo = preferences.enableMediaInfoIntent.get()
        val componentName = ComponentName(this@App, "app.gyrolet.mpvrx.ui.mediainfo.MediaInfoActivityAlias")
        val newState = if (enableMediaInfo) {
          PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
          PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(
          componentName,
          newState,
          PackageManager.DONT_KILL_APP
        )
      }.onFailure { error ->
        Log.e("App", "Failed to initialize MediaInfoActivityAlias setting on launch", error)
      }
    }

    // Perform cache maintenance on app startup (non-blocking)
    applicationScope.launch {
      runCatching {
        metadataCache.performMaintenance()
      }
    }

    applicationScope.launch {
      initializeScriptEditorAssets()
    }

    applicationScope.launch {
      runCatching {
        triggerMediaScanOnLaunch()
      }
    }
  }

  private fun triggerMediaScanOnLaunch() {
    try {
      if (!shouldRunLaunchMediaScan()) {
        android.util.Log.d("App", "Skipped launch media scan; last scan was recent")
        return
      }

      val externalStorage = android.os.Environment.getExternalStorageDirectory()

      android.media.MediaScannerConnection.scanFile(
        this,
        arrayOf(externalStorage.absolutePath),
        null,
      ) { path, _ ->
        android.util.Log.d("App", "Launch media scan completed for: $path")
        MediaLibraryEvents.notifyChanged()
      }

      android.util.Log.d("App", "Triggered media scan on app launch")
    } catch (error: Exception) {
      android.util.Log.e("App", "Failed to trigger media scan on launch", error)
    }
  }

  private fun shouldRunLaunchMediaScan(): Boolean {
    val now = System.currentTimeMillis()
    val prefs = getSharedPreferences(LAUNCH_SCAN_PREFS, MODE_PRIVATE)
    val lastScan = prefs.getLong(LAST_LAUNCH_SCAN_MS, 0L)
    if (now - lastScan < LAUNCH_SCAN_INTERVAL_MS) {
      return false
    }
    prefs.edit().putLong(LAST_LAUNCH_SCAN_MS, now).apply()
    return true
  }

  private fun initializeScriptEditorAssets() {
    runCatching {
      FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))
      GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

      val themeRegistry = ThemeRegistry.getInstance()
      listOf("darcula", "quietlight").forEach { themeName ->
        val path = "textmate/$themeName.json"
        themeRegistry.loadTheme(
          ThemeModel(
            IThemeSource.fromInputStream(
              FileProviderRegistry.getInstance().tryGetInputStream(path),
              path,
              null,
            ),
            themeName,
          ),
        )
      }
      themeRegistry.setTheme("darcula")
    }.onFailure { error ->
      Log.w("App", "Failed to initialize script editor assets", error)
    }
  }
}
