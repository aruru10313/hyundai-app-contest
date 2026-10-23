package io.github.aruru10313.arrivaltale.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MockUxRestrictionProviderTest {
    @Test
    fun `switches between parked and driving modes`() {
        val provider = MockUxRestrictionProvider()

        assertEquals(DrivingSafetyMode.PARKED, provider.state.value.mode)

        provider.setDriving(true)
        assertEquals(DrivingSafetyMode.DRIVING, provider.state.value.mode)
        assertEquals(true, provider.state.value.requiresDistractionOptimization)

        provider.setDriving(false)
        assertEquals(DrivingSafetyMode.PARKED, provider.state.value.mode)
        assertEquals(false, provider.state.value.requiresDistractionOptimization)
    }
}
