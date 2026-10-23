package io.github.aruru10313.arrivaltale.domain

enum class StoryTheme(val label: String) {
    ADVENTURE("모험"),
    ANIMALS("동물"),
    SPACE("우주"),
}

enum class AgeGroup(val label: String) {
    PRESCHOOL("미취학"),
    ELEMENTARY("초등"),
}

data class Story(
    val id: String,
    val title: String,
    val theme: StoryTheme,
    val chapters: List<StoryChapter>,
)

data class StoryChapter(
    val id: String,
    val title: String,
    val text: String,
    val estimatedSeconds: Int,
    val compressedText: String? = null,
    val compressedSeconds: Int? = null,
    val isEnding: Boolean = false,
)

data class PlannedChapter(
    val chapter: StoryChapter,
    val text: String,
    val estimatedSeconds: Int,
    val isCompressed: Boolean,
)
