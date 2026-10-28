package app.drivecare.data

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pleos TextToSpeech in ON_DEVICE mode, which the docs say works offline and needs no app
 * registration. Call order follows the docs: initialize → addEventListener → setLocale → speak.
 */
class PleosTtsProvider(context: Context, private val audioAttributes: AudioAttributes) : TtsProvider {
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val ready = CompletableDeferred<Boolean>()
    @Volatile private var pending: CancellableContinuation<Unit>? = null

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = finish(null)
        override fun onStop() = finish(null)
        override fun onError(errorType: ErrorType) = finish(IllegalStateException("Pleos TTS: $errorType"))
    }

    init {
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.addEventListener(events)
                    tts.setLocale(Locale.KOREAN)
                    tts.setAudioAttributes(audioAttributes)
                    ready.complete(true)
                }

                override fun onFailure(errorType: ErrorType) {
                    ready.complete(false)
                }
            })
        } catch (exception: Exception) {
            ready.complete(false)
        }
    }

    /** The Pleos service may simply not exist (e.g. a phone), so give up after a few seconds. */
    suspend fun isAvailable(): Boolean = withTimeoutOrNull(3_000L) { ready.await() } ?: false

    override suspend fun speak(text: String) {
        require(text.isNotBlank()) { "TTS text must not be blank" }
        check(isAvailable()) { "Pleos TTS is not available" }
        suspendCancellableCoroutine { continuation ->
            pending = continuation
            continuation.invokeOnCancellation { tts.stop() }
            tts.speak(text, true)
        }
    }

    private fun finish(error: Exception?) {
        val continuation = pending ?: return
        pending = null
        if (!continuation.isActive) return
        if (error == null) continuation.resume(Unit) else continuation.resumeWithException(error)
    }

    override fun stop() {
        tts.stop()
        finish(null)
    }

    fun release() {
        stop()
        try {
            tts.removeEventListener(events)
            tts.release()
        } catch (ignored: Exception) {
        }
    }
}

/** Uses the Pleos voice when the head unit provides it, otherwise Android's built-in TTS. */
class FallbackTtsProvider(
    private val primary: PleosTtsProvider?,
    private val fallback: AndroidTtsProvider,
    private val audio: GuidanceAudio,
) : TtsProvider {
    override suspend fun speak(text: String) = audio.withFocus { speakWithoutFocus(text) }

    private suspend fun speakWithoutFocus(text: String) {
        if (primary != null && primary.isAvailable()) {
            try {
                primary.speak(text)
                return
            } catch (ignored: IllegalStateException) {
                // Fall through to the Android voice.
            }
        }
        fallback.speak(text)
    }

    override fun stop() {
        primary?.stop()
        fallback.stop()
    }

    fun release() {
        primary?.release()
        fallback.release()
    }
}
