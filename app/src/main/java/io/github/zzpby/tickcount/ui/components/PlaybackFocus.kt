package io.github.zzpby.tickcount.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * The manners of a house that is not the only one making sound: before a
 * player sounds, it asks; when something with a stronger claim speaks — a
 * call, a navigation voice, another app's song — the player is told, and
 * it is the caller's [onLost] that pauses it. A ducked moment (someone
 * speaking briefly over the music) is not a loss: [onDuck] says whether
 * the volume should crouch or stand.
 *
 * Hold one per player; [gain] before every start, [release] when the
 * player leaves.
 */
class PlaybackFocus(
    context: Context,
    private val onLost: () -> Unit,
    private val onDuck: (ducked: Boolean) -> Unit = {},
) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    /** Asks for the floor. False means the room declined — do not start. */
    fun gain(): Boolean {
        val built = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    -> onLost()

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(true)
                    AudioManager.AUDIOFOCUS_GAIN -> onDuck(false)
                }
            }
            .build()
        request = built
        return manager.requestAudioFocus(built) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Lets the floor go — on leaving, or when the loss already ended it. */
    fun release() {
        request?.let { manager.abandonAudioFocusRequest(it) }
        request = null
    }
}
