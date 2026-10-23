package io.github.aruru10313.arrivaltale.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StoryPlannerTest {

    private val planner = StoryPlanner()

    @Test
    fun `zero seconds yields no segments`() {
        assertEquals(0, planner.segmentCount(0, 60))
    }

    @Test
    fun `negative seconds yields no segments`() {
        assertEquals(0, planner.segmentCount(-10, 60))
    }

    @Test
    fun `exact multiple yields exact count`() {
        assertEquals(3, planner.segmentCount(180, 60))
    }

    @Test
    fun `remainder rounds up`() {
        assertEquals(4, planner.segmentCount(181, 60))
    }

    @Test
    fun `non positive segment length is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            planner.segmentCount(60, 0)
        }
    }

    @Test
    fun `ten minute ETA keeps full chapters and a closing chapter`() {
        val plan = planner.plan(10 * 60, sampleChapters())

        assertEquals(
            listOf("chapter-1", "chapter-2", "chapter-3", "chapter-4", "ending"),
            plan.map { it.chapter.id },
        )
        assertEquals("ending", plan.last().chapter.id)
        assertEquals(300, plan.sumOf { it.estimatedSeconds })
    }

    @Test
    fun `five minute ETA compresses a chapter and keeps the ending`() {
        val plan = planner.plan(5 * 60, sampleChapters())

        assertEquals(listOf("chapter-1", "chapter-2", "chapter-3", "ending"), plan.map { it.chapter.id })
        assertEquals(true, plan[2].isCompressed)
        assertEquals(210, plan.sumOf { it.estimatedSeconds })
    }

    @Test
    fun `shortened ETA recalculates a shorter plan with an ending`() {
        val chapters = sampleChapters()

        val tenMinutePlan = planner.plan(10 * 60, chapters)
        val fiveMinutePlan = planner.plan(5 * 60, chapters)

        assertEquals("ending", fiveMinutePlan.last().chapter.id)
        assertEquals(true, fiveMinutePlan.size < tenMinutePlan.size)
    }

    private fun sampleChapters() = listOf(
        StoryChapter("chapter-1", "첫 장", "본문", 60, "짧은 본문", 30),
        StoryChapter("chapter-2", "둘째 장", "본문", 60, "짧은 본문", 30),
        StoryChapter("chapter-3", "셋째 장", "본문", 60, "짧은 본문", 30),
        StoryChapter("chapter-4", "넷째 장", "본문", 60, "짧은 본문", 30),
        StoryChapter("ending", "결말", "끝", 60, isEnding = true),
    )
}
