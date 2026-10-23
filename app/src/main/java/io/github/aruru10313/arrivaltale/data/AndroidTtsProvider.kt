package io.github.aruru10313.arrivaltale.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidTtsProvider(context: Context) : TtsProvider {
    private val engineReady = CompletableDeferred<Int>()
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private val engine = TextToSpeech(context.applicationContext) { status ->
        engineReady.complete(status)
    }

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                pending.remove(utteranceId)?.resume(Unit)
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String) {
                pending.remove(utteranceId)?.resumeWithException(
                    IllegalStateException("Android TTS failed to speak the story"),
                )
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                pending.remove(utteranceId)?.resumeWithException(
                    IllegalStateException("Android TTS failed with code $errorCode"),
                )
            }
        })
    }

    override suspend fun speak(text: String) {
        require(text.isNotBlank()) { "TTS text must not be blank" }
        check(engineReady.await() == TextToSpeech.SUCCESS) { "Android TTS initialization failed" }
        check(engine.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE) {
            "Korean TTS voice data is unavailable"
        }

        val utteranceId = UUID.randomUUID().toString()
        suspendCancellableCoroutine { continuation ->
            pending[utteranceId] = continuation
            continuation.invokeOnCancellation {
                pending.remove(utteranceId)
                engine.stop()
            }
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
                pending.remove(utteranceId)?.resumeWithException(
                    IllegalStateException("Android TTS rejected the story"),
                )
            }
        }
    }

    override fun stop() {
        engine.stop()
        pending.entries.forEach { (id, continuation) ->
            if (pending.remove(id, continuation) && continuation.isActive) continuation.resume(Unit)
        }
    }

    fun release() {
        stop()
        engine.shutdown()
    }
}
