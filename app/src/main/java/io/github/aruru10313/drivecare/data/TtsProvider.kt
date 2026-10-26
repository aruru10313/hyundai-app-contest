package io.github.aruru10313.drivecare.data

interface TtsProvider {
    suspend fun speak(text: String)
    fun stop()
}
