package app.gyrolet.mpvrx.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import app.gyrolet.mpvrx.R
import com.composables.icons.materialsymbols.roundedfilled.R as MaterialSymbolsR
import app.gyrolet.mpvrx.preferences.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val PIP_INTENTS_FILTER = "pip_action"
private const val PIP_ACTION_PLAY    = "pip_action_play"
private const val PIP_ACTION_PAUSE   = "pip_action_pause"
private const val PIP_ACTION_REWIND  = "pip_action_rewind"
private const val PIP_ACTION_FORWARD = "pip_action_forward"

class MPVPipHelper(
  private val activity: AppCompatActivity,
  private val mpvView: MPVView,
) : KoinComponent {
  private val playerPreferences: PlayerPreferences by inject()
  private var pipReceiver: BroadcastReceiver? = null

  fun onPictureInPictureModeChanged(isInPipMode: Boolean) {
    // Receiver stays registered for full lifecycle — don't unregister on PiP exit
    // This prevents missing button presses when mode changes happen mid-action
  }

  @Suppress("UnspecifiedRegisterReceiverFlag")
  fun registerPipReceiver() {
    if (pipReceiver != null) return // already registered
    pipReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(
          context: Context?,
          intent: Intent?,
        ) {
          val duration = MPVLib.getPropertyInt("duration") ?: 0
          val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || duration < 120
          val seekMode = if (shouldUsePreciseSeeking) "relative+exact" else "relative+keyframes"
          when (intent?.action) {
            PIP_ACTION_PLAY    -> MPVLib.setPropertyBoolean("pause", false)
            PIP_ACTION_PAUSE   -> MPVLib.setPropertyBoolean("pause", true)
            PIP_ACTION_REWIND  -> MPVLib.command("seek", "-10", seekMode)
            PIP_ACTION_FORWARD -> MPVLib.command("seek", "10", seekMode)
          }
          updatePictureInPictureParams()
        }
      }

    val filter = IntentFilter().apply {
      addAction(PIP_ACTION_PLAY)
      addAction(PIP_ACTION_PAUSE)
      addAction(PIP_ACTION_REWIND)
      addAction(PIP_ACTION_FORWARD)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      activity.registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      activity.registerReceiver(pipReceiver, filter)
    }
  }

  fun unregisterPipReceiver() {
    pipReceiver?.let {
      runCatching { activity.unregisterReceiver(it) }
      pipReceiver = null
    }
  }

  fun updatePictureInPictureParams() {
    if (activity.isFinishing || activity.isDestroyed) return

    val params = buildPipParams()
    runCatching { activity.setPictureInPictureParams(params) }
  }

  private fun buildPipParams(): PictureInPictureParams =
    PictureInPictureParams
      .Builder()
      .apply {
        getVideoAspectRatio()?.let { aspectRatio ->
          setAspectRatio(aspectRatio)
          setSourceRectHint(calculateSourceRect(aspectRatio))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          setAutoEnterEnabled(playerPreferences.autoPiPOnNavigation.get())
        }

        setActions(createPipActions())
      }.build()

  private fun getVideoAspectRatio(): Rational? {
    val width = MPVLib.getPropertyInt("video-out-params/dw") ?: 0
    val height = MPVLib.getPropertyInt("video-out-params/dh") ?: 0

    if (width == 0 || height == 0) return null

    return Rational(width, height).takeIf { it.toFloat() in 0.5f..2.39f }
  }

  private fun calculateSourceRect(aspectRatio: Rational): Rect {
    val viewWidth = mpvView.width.toFloat()
    val viewHeight = mpvView.height.toFloat()
    val videoAspect = aspectRatio.toFloat()
    val viewAspect = viewWidth / viewHeight

    return if (viewAspect < videoAspect) {
      // Letterboxed (black bars top/bottom)
      val height = viewWidth / videoAspect
      val top = ((viewHeight - height) / 2).toInt()
      Rect(0, top, viewWidth.toInt(), (height + top).toInt())
    } else {
      // Pillarboxed (black bars left/right)
      val width = viewHeight * videoAspect
      val left = ((viewWidth - width) / 2).toInt()
      Rect(left, 0, (width + left).toInt(), viewHeight.toInt())
    }
  }

  private fun createPipActions(): List<RemoteAction> {
    val isPlaying = MPVLib.getPropertyBoolean("pause") == false

    return listOf(
      createRemoteAction("rewind", android.R.drawable.ic_media_rew, PIP_ACTION_REWIND),
      if (isPlaying) {
        createRemoteAction("pause", MaterialSymbolsR.drawable.materialsymbols_ic_pause_rounded_filled, PIP_ACTION_PAUSE)
      } else {
        createRemoteAction("play", MaterialSymbolsR.drawable.materialsymbols_ic_play_arrow_rounded_filled, PIP_ACTION_PLAY)
      },
      createRemoteAction("forward", android.R.drawable.ic_media_ff, PIP_ACTION_FORWARD),
    )
  }

  private fun createRemoteAction(
    title: String,
    @DrawableRes icon: Int,
    action: String,
  ): RemoteAction {
    val intent = Intent(action).apply {
      setPackage(activity.packageName)
    }

    val pendingIntent =
      PendingIntent.getBroadcast(
        activity,
        action.hashCode(),
        intent,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
          PendingIntent.FLAG_MUTABLE
        else
          PendingIntent.FLAG_UPDATE_CURRENT,
      )

    return RemoteAction(
      Icon.createWithResource(activity, icon),
      title,
      title,
      pendingIntent,
    )
  }

  fun enterPipMode() {
    runCatching {
      activity.enterPictureInPictureMode(buildPipParams())
    }.onFailure {
      Log.e("MPVPipHelper", "Failed to enter PiP mode", it)
    }
  }

  fun onStop() {
    unregisterPipReceiver()
  }
}

