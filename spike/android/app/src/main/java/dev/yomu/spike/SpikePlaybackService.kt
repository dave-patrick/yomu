package dev.yomu.spike

import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.media.AudioManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult

class SpikePlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val handler = Handler(Looper.getMainLooper())
    private var telephonyManager: TelephonyManager? = null
    private var callStateCallback: TelephonyCallback? = null
    private var legacyCallStateListener: PhoneStateListener? = null
    private var callActive = false
    private var resumeAfterCall = false
    private var pausingForCall = false
    private var callResumeAttempts = 0
    private val callResumeRetry = object : Runnable {
        override fun run() {
            if (!resumeAfterCall || callActive || player.currentMediaItem == null) return
            if (player.isPlaying) {
                scheduleCallResumeConfirmation()
                return
            }
            if (callResumeAttempts >= MAX_CALL_RESUME_ATTEMPTS) {
                Evidence.write(this@SpikePlaybackService, "call_resume_timeout", mapOf("attempts" to callResumeAttempts))
                resumeAfterCall = false
                return
            }
            callResumeAttempts++
            Evidence.write(this@SpikePlaybackService, "call_resume_retry", mapOf("attempt" to callResumeAttempts,
                "play_when_ready" to player.playWhenReady))
            player.play()
            handler.postDelayed(this, CALL_RESUME_RETRY_MS)
        }
    }
    private val callResumeConfirmation = object : Runnable {
        override fun run() {
            if (resumeAfterCall && !callActive && player.isPlaying) {
                handler.removeCallbacks(callResumeRetry)
                resumeAfterCall = false
                Evidence.write(this@SpikePlaybackService, "call_resume_confirmed", mapOf("position_ms" to player.currentPosition,
                    "stable_ms" to CALL_RESUME_STABLE_MS))
            }
        }
    }
    private val prefs by lazy { getSharedPreferences("player", MODE_PRIVATE) }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Evidence.write(context, "audio_becoming_noisy", mapOf("position_ms" to player.currentPosition))
        }
    }
    private val checkpoint = object : Runnable {
        override fun run() { save(); handler.postDelayed(this, 1000) }
    }
    private val callMonitorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { startCallStateMonitor() }
    }
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Evidence.write(this@SpikePlaybackService, "playing_changed", mapOf("playing" to isPlaying, "position_ms" to currentPosition))
                    if (isPlaying && !callActive && resumeAfterCall) {
                        scheduleCallResumeConfirmation()
                    }
                    save()
                }
                override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                    val internalCallPause = callActive && !ready && pausingForCall
                    Evidence.write(this@SpikePlaybackService, "play_when_ready", mapOf("ready" to ready, "reason" to reason,
                        "is_playing" to isPlaying, "call_active" to callActive, "position_ms" to currentPosition))
                    if (internalCallPause) {
                        // The player callback can be delivered after pause() returns. Keep
                        // this marker until that callback so our own pause is not mistaken
                        // for an explicit user pause.
                        pausingForCall = false
                    } else if (callActive && ready && !pausingForCall) {
                        resumeAfterCall = false
                        pausingForCall = true
                        pause()
                    }
                }
            })
        }
        session = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            override fun onPlayerCommandRequest(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                playerCommand: Int): Int {
                if (resumeAfterCall && playerCommand == Player.COMMAND_PLAY_PAUSE && player.playWhenReady) {
                    handler.removeCallbacks(callResumeRetry)
                    handler.removeCallbacks(callResumeConfirmation)
                    resumeAfterCall = false
                    Evidence.write(this@SpikePlaybackService, "call_resume_cancelled_by_user", mapOf("source" to "media_session",
                        "position_ms" to player.currentPosition))
                }
                return SessionResult.RESULT_SUCCESS
            }
        }).build()
        val uri = prefs.getString("uri", null)
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.seekTo(prefs.getLong("position", 0))
            player.prepare() // restore paused: Android force-stop must never auto-resume
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        else registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(callMonitorReceiver,
            IntentFilter(ACTION_ENABLE_CALL_MONITOR), RECEIVER_NOT_EXPORTED)
        else registerReceiver(callMonitorReceiver, IntentFilter(ACTION_ENABLE_CALL_MONITOR))
        Evidence.write(this, "service_created", mapOf("restored_uri" to (uri != null), "restored_position_ms" to prefs.getLong("position", 0)))
        handler.post(checkpoint)
    }
    private fun save() {
        val uri = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        prefs.edit().putString("uri", uri).putLong("position", player.currentPosition).apply()
    }
    @Suppress("MissingPermission")
    private fun startCallStateMonitor() {
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Evidence.write(this, "call_monitor_unavailable", mapOf("reason" to "permission_not_granted")); return
        }
        if (telephonyManager != null) return
        val manager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager = manager
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onPhoneCallState(state)
            }
            callStateCallback = callback
            manager.registerTelephonyCallback(mainExecutor, callback)
        } else {
            val listener = object : PhoneStateListener() {
                @Deprecated("Legacy API for pre-Android 12 diagnostic devices")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onPhoneCallState(state)
            }
            legacyCallStateListener = listener
            @Suppress("DEPRECATION")
            manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
        Evidence.write(this, "call_monitor_enabled", mapOf("phone_number_accessed" to false))
    }
    private fun onPhoneCallState(state: Int) {
        val busy = state != TelephonyManager.CALL_STATE_IDLE
        val name = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> "ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
            else -> "idle"
        }
        Evidence.write(this, "phone_call_state", mapOf("state" to name, "position_ms" to player.currentPosition))
        if (busy && !callActive) {
            callActive = true
            resumeAfterCall = player.playWhenReady
            if (resumeAfterCall) {
                pausingForCall = true
                player.pause()
            }
            save()
            Evidence.write(this, "paused_for_call", mapOf("resume_after_call" to resumeAfterCall, "position_ms" to player.currentPosition))
        } else if (!busy && callActive) {
            callActive = false
            pausingForCall = false
            val resume = resumeAfterCall
            if (resume && player.currentMediaItem != null) {
                callResumeAttempts = 0
                // The telephony callback can report IDLE just before the call's audio-focus
                // owner releases focus. Delay the first retry, then retry briefly if Android
                // still rejects focus; stop early on explicit user pause.
                handler.removeCallbacks(callResumeRetry)
                handler.removeCallbacks(callResumeConfirmation)
                handler.postDelayed(callResumeRetry, CALL_RESUME_RETRY_MS)
            } else {
                resumeAfterCall = false
            }
            Evidence.write(this, "call_ended", mapOf("resumed" to resume, "position_ms" to player.currentPosition))
        }
    }
    private fun stopCallStateMonitor() {
        val manager = telephonyManager ?: return
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            callStateCallback?.let(manager::unregisterTelephonyCallback)
            callStateCallback = null
        } else {
            legacyCallStateListener?.let {
                @Suppress("DEPRECATION")
                manager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
            legacyCallStateListener = null
        }
        telephonyManager = null
        callActive = false
        resumeAfterCall = false
    }
    private fun scheduleCallResumeConfirmation() {
        handler.removeCallbacks(callResumeConfirmation)
        handler.postDelayed(callResumeConfirmation, CALL_RESUME_STABLE_MS)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session
    override fun onDestroy() {
        save(); handler.removeCallbacksAndMessages(null); unregisterReceiver(noisy); unregisterReceiver(callMonitorReceiver); stopCallStateMonitor()
        Evidence.write(this, "service_destroyed", mapOf("position_ms" to player.currentPosition))
        session.release(); player.release(); super.onDestroy()
    }
    companion object {
        const val ACTION_ENABLE_CALL_MONITOR = "dev.yomu.spike.ENABLE_CALL_MONITOR"
        const val CALL_RESUME_RETRY_MS = 750L
        const val CALL_RESUME_STABLE_MS = 1500L
        const val MAX_CALL_RESUME_ATTEMPTS = 12
    }
}
