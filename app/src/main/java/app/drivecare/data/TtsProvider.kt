package app.drivecare.data

interface TtsProvider {
    suspend fun speak(text: String)
    fun stop()
}
