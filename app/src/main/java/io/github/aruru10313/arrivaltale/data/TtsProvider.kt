package io.github.aruru10313.arrivaltale.data

interface TtsProvider {
    suspend fun speak(text: String)
    fun stop()
}
