package app.gyrolet.mpvrx.ui.player

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import app.gyrolet.mpvrx.R

/**
 * Floating overlay service that shows play/pause, seek-back-10s, and seek-forward-10s
 * buttons on top of other apps while MpvRxN is in PiP mode.
 *
 * Requires SYSTEM_ALERT_WINDOW permission (display over other apps).
 * Only active while in PiP — started by PlayerActivity.onPictureInPictureModeChanged(true)
 * and stopped when PiP exits or the player is destroyed.
 */
class PipOverlayService : Service() {

  companion object {
    const val ACTION_START = "app.gyrolet.mpvrx.PIP_OVERLAY_START"
    const val ACTION_STOP = "app.gyrolet.mpvrx.PIP_OVERLAY_STOP"
    const val ACTION_UPDATE_PLAYBACK = "app.gyrolet.mpvrx.PIP_OVERLAY_UPDATE_PLAYBACK"
    const val EXTRA_IS_PLAYING = "is_playing"

    // These actions are sent back to PlayerActivity to control playback
    const val ACTION_PLAY_PAUSE = "app.gyrolet.mpvrx.pip.PLAY_PAUSE"
    const val ACTION_SEEK_BACK = "app.gyrolet.mpvrx.pip.SEEK_BACK"
    const val ACTION_SEEK_FORWARD = "app.gyrolet.mpvrx.pip.SEEK_FORWARD"

    private const val TAG = "PipOverlayService"
  }

  private var windowManager: WindowManager? = null
  private var overlayView: View? = null
  private var isPlaying = true

  private val playbackUpdateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action == ACTION_UPDATE_PLAYBACK) {
        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
        updatePlayPauseButton()
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    registerReceiver(
      playbackUpdateReceiver,
      IntentFilter(ACTION_UPDATE_PLAYBACK),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Context.RECEIVER_EXPORTED else 0,
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
        showOverlay()
      }
      ACTION_STOP -> {
        removeOverlay()
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  private fun showOverlay() {
    if (overlayView != null) return

    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER
      setBackgroundResource(android.R.color.transparent)
    }

    val buttonSize = (56 * resources.displayMetrics.density).toInt()
    val margin = (8 * resources.displayMetrics.density).toInt()

    fun makeButton(iconRes: Int, action: String): ImageButton {
      return ImageButton(this).apply {
        setImageResource(iconRes)
        background = ContextCompat.getDrawable(
          this@PipOverlayService,
          android.R.drawable.btn_default,
        )
        alpha = 0.85f
        layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
          setMargins(margin, margin, margin, margin)
        }
        setOnClickListener {
          sendBroadcast(Intent(action).setPackage(packageName))
        }
      }
    }

    val seekBackBtn = makeButton(android.R.drawable.ic_media_previous, ACTION_SEEK_BACK)
    val playPauseBtn = makeButton(
      if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
      ACTION_PLAY_PAUSE,
    ).also { it.tag = "playPause" }
    val seekFwdBtn = makeButton(android.R.drawable.ic_media_next, ACTION_SEEK_FORWARD)

    layout.addView(seekBackBtn)
    layout.addView(playPauseBtn)
    layout.addView(seekFwdBtn)

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      else
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      y = (80 * resources.displayMetrics.density).toInt()
    }

    try {
      windowManager?.addView(layout, params)
      overlayView = layout
      Log.d(TAG, "PiP overlay shown")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to show PiP overlay", e)
    }
  }

  private fun updatePlayPauseButton() {
    val layout = overlayView as? LinearLayout ?: return
    for (i in 0 until layout.childCount) {
      val child = layout.getChildAt(i)
      if (child.tag == "playPause" && child is ImageButton) {
        child.setImageResource(
          if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        break
      }
    }
  }

  private fun removeOverlay() {
    overlayView?.let {
      try {
        windowManager?.removeView(it)
        Log.d(TAG, "PiP overlay removed")
      } catch (e: Exception) {
        Log.e(TAG, "Error removing PiP overlay", e)
      }
    }
    overlayView = null
  }

  override fun onDestroy() {
    removeOverlay()
    runCatching { unregisterReceiver(playbackUpdateReceiver) }
    super.onDestroy()
  }
}
