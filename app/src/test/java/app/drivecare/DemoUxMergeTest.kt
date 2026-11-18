package app.drivecare

import app.drivecare.data.DemoOnCarUxRestrictionProvider
import app.drivecare.data.DrivingSafetyMode
import app.drivecare.data.UxRestrictionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoUxMergeTest {
    private fun snap(mode: DrivingSafetyMode, available: Boolean = true) = UxRestrictionSnapshot(
        mode = mode,
        requiresDistractionOptimization = mode == DrivingSafetyMode.DRIVING,
        activeRestrictions = 0,
        isAvailable = available,
    )

    @Test
    fun `real car driving wins over a parked demo`() {
        val merged = DemoOnCarUxRestrictionProvider.merge(snap(DrivingSafetyMode.DRIVING), snap(DrivingSafetyMode.PARKED))
        assertEquals(DrivingSafetyMode.DRIVING, merged.mode)
    }

    @Test
    fun `unknown car state keeps the driving view`() {
        val merged = DemoOnCarUxRestrictionProvider.merge(snap(DrivingSafetyMode.DRIVING, available = false), snap(DrivingSafetyMode.PARKED))
        assertEquals(DrivingSafetyMode.DRIVING, merged.mode)
    }

    @Test
    fun `parked car follows the demo trip`() {
        assertEquals(DrivingSafetyMode.DRIVING, DemoOnCarUxRestrictionProvider.merge(snap(DrivingSafetyMode.PARKED), snap(DrivingSafetyMode.DRIVING)).mode)
        assertEquals(DrivingSafetyMode.PARKED, DemoOnCarUxRestrictionProvider.merge(snap(DrivingSafetyMode.PARKED), snap(DrivingSafetyMode.PARKED)).mode)
    }
}
