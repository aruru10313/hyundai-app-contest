package io.github.aruru10313.arrivaltale.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripAutoStartGateTest {
    @Test
    fun `starts once when route enters driving and resets after route ends`() {
        val gate = TripAutoStartGate()

        assertFalse(gate.onRouteDrivingChanged(false))
        assertTrue(gate.onRouteDrivingChanged(true))
        assertFalse(gate.onRouteDrivingChanged(true))
        assertFalse(gate.onRouteDrivingChanged(false))
        assertTrue(gate.onRouteDrivingChanged(true))
    }
}
