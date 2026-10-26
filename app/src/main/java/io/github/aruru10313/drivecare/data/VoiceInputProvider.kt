package io.github.aruru10313.drivecare.data

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class VoiceInputState(
    val listening: Boolean = false,
    /** Live transcript while listening, then the final sentence. */
    val transcript: String? = null,
    val error: String? = null,
)

interface VoiceInputProvider {
    val state: StateFlow<VoiceInputState>

    /** Listens for one sentence and calls [onFinal] with it. */
    fun listen(onFinal: (String) -> Unit)
    fun cancel()
    fun release()
}

/**
 * Pleos SpeechToText in ON_DEVICE mode (offline, no app registration per the docs). Falls back to
 * Android's SpeechRecognizer if the Pleos service is missing, e.g. on a phone used for demos.
 */
class PleosVoiceInputProvider(context: Context) : VoiceInputProvider {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(VoiceInputState())
    override val state: StateFlow<VoiceInputState> = mutableState.asStateFlow()

    private var stt: SpeechToText? = null
    private var pleosReady = false
    private var pleosFailed = false
    private var onFinal: ((String) -> Unit)? = null
    private var androidRecognizer: SpeechRecognizer? = null

    private val results = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() {
            mutableState.value = VoiceInputState(listening = true)
        }

        override fun onUpdated(text: String, isFinal: Boolean) {
            mutableState.value = mutableState.value.copy(transcript = text)
            if (isFinal) deliver(text)
        }

        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) = Unit
        override fun onEndedRecognition() {
            mutableState.value = mutableState.value.copy(listening = false)
        }

        override fun onError(errorType: ErrorType) {
            mutableState.value = VoiceInputState(error = "음성 인식 오류 ($errorType)")
        }

        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        try {
            val speech = SpeechToText(appContext, Mode.ON_DEVICE)
            stt = speech
            speech.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    speech.addListener(results)
                    speech.setLocale(Locale.KOREAN)
                    pleosReady = true
                }

                override fun onFailure(errorType: ErrorType) {
                    pleosFailed = true
                }
            })
        } catch (ignored: Exception) {
            pleosFailed = true
        }
    }

    override fun listen(onFinal: (String) -> Unit) {
        this.onFinal = onFinal
        mutableState.value = VoiceInputState(listening = true)
        val speech = stt
        if (pleosReady && speech != null) {
            try {
                speech.request()
                return
            } catch (ignored: Exception) {
                pleosFailed = true
            }
        }
        listenWithAndroid()
    }

    private fun listenWithAndroid() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            mutableState.value = VoiceInputState(error = "이 기기에서는 음성 인식을 쓸 수 없어요")
            return
        }
        val recognizer = androidRecognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            androidRecognizer = it
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                mutableState.value = mutableState.value.copy(listening = false)
            }

            override fun onError(error: Int) {
                mutableState.value = VoiceInputState(
                    error = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        "잘 듣지 못했어요. 다시 말씀해 주세요"
                    } else {
                        "음성 인식 오류 ($error)"
                    },
                )
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) onError(SpeechRecognizer.ERROR_NO_MATCH) else deliver(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    mutableState.value = mutableState.value.copy(transcript = it)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
        )
    }

    private fun deliver(text: String) {
        mutableState.value = VoiceInputState(transcript = text)
        onFinal?.invoke(text)
        onFinal = null
    }

    override fun cancel() {
        onFinal = null
        try {
            stt?.stop()
        } catch (ignored: Exception) {
        }
        androidRecognizer?.cancel()
        mutableState.value = VoiceInputState()
    }

    override fun release() {
        cancel()
        try {
            stt?.removeListener(results)
            stt?.release()
        } catch (ignored: Exception) {
        }
        androidRecognizer?.destroy()
        androidRecognizer = null
    }
}
