package app.drivecare.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Audio focus for spoken guidance. Pleos' driver-distraction guide asks apps to follow the audio
 * focus policy when playing driving-relevant audio, so speech is played as navigation guidance and
 * asks other audio (music) to duck while it talks.
 */
class GuidanceAudio(context: Context) {
    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()

    /** Runs [block] while holding transient, duck-others focus. Speaks anyway if focus is refused. */
    suspend fun <T> withFocus(block: suspend () -> T): T {
        runCatching { audioManager?.requestAudioFocus(request) }
        return try {
            block()
        } finally {
            runCatching { audioManager?.abandonAudioFocusRequest(request) }
        }
    }
}
