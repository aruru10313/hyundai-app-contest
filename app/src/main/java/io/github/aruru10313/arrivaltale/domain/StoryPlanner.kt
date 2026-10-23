package io.github.aruru10313.arrivaltale.domain

class StoryPlanner(
    private val arrivalBufferSeconds: Int = 90,
) {
    init {
        require(arrivalBufferSeconds > 0)
    }

    fun plan(remainingSeconds: Int, chapters: List<StoryChapter>): List<PlannedChapter> {
        if (remainingSeconds <= 0) return emptyList()

        val endings = chapters.filter(StoryChapter::isEnding)
        require(endings.size == 1) { "A story must contain exactly one ending chapter" }
        val ending = endings.single()
        require(ending.estimatedSeconds > 0) { "Ending duration must be > 0" }

        var budget = remainingSeconds - ending.estimatedSeconds - arrivalBufferSeconds
        if (budget < 0) return listOf(ending.toPlan())

        val plan = mutableListOf<PlannedChapter>()
        chapters.filterNot(StoryChapter::isEnding).forEach { chapter ->
            require(chapter.estimatedSeconds > 0) { "Chapter duration must be > 0" }
            when {
                chapter.estimatedSeconds <= budget -> {
                    plan += chapter.toPlan()
                    budget -= chapter.estimatedSeconds
                }
                chapter.compressedSeconds != null && chapter.compressedText != null &&
                    chapter.compressedSeconds in 1..budget -> {
                    plan += PlannedChapter(
                        chapter = chapter,
                        text = chapter.compressedText,
                        estimatedSeconds = chapter.compressedSeconds,
                        isCompressed = true,
                    )
                    budget -= chapter.compressedSeconds
                }
            }
        }
        plan += ending.toPlan()
        return plan
    }

    fun segmentCount(totalSeconds: Int, targetSegmentSeconds: Int): Int {
        require(targetSegmentSeconds > 0) { "targetSegmentSeconds must be > 0" }
        if (totalSeconds <= 0) return 0
        return (totalSeconds + targetSegmentSeconds - 1) / targetSegmentSeconds
    }

    private fun StoryChapter.toPlan() = PlannedChapter(
        chapter = this,
        text = text,
        estimatedSeconds = estimatedSeconds,
        isCompressed = false,
    )
}
