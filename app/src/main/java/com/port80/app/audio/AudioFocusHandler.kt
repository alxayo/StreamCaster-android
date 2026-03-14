package com.port80.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.port80.app.util.RedactingLogger

/**
 * Handles audio focus changes for the streaming service.
 *
 * When another app (like a phone call) needs audio, Android sends
 * an audio focus change. We respond by muting our microphone.
 * We do NOT auto-unmute — the user must explicitly unmute.
 *
 * Why? Because auto-unmuting after a phone call could accidentally
 * broadcast private conversation audio to the stream viewers.
 */
class AudioFocusHandler(
    private val context: Context,
    private val onMuteRequired: () -> Unit
) {
    companion object {
        private const val TAG = "AudioFocusHandler"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    // Listener called when audio focus changes
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Another app took audio focus (e.g., phone call)
                // Mute our microphone to avoid broadcasting the call
                RedactingLogger.i(TAG, "Audio focus lost — muting microphone")
                onMuteRequired()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // We got focus back, but DON'T auto-unmute
                // User must manually unmute for safety
                RedactingLogger.d(TAG, "Audio focus regained (user must unmute manually)")
            }
        }
    }

    /** Request audio focus for streaming. */
    fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        RedactingLogger.d(TAG, "Audio focus requested")
    }

    /** Release audio focus when streaming stops. */
    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        RedactingLogger.d(TAG, "Audio focus released")
    }
}
