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
}
