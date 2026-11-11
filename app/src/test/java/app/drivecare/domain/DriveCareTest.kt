package app.drivecare.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCoachTest {
    private val coach = RestCoach()

    @Test
    fun `unknown without trip driving time`() {
        assertEquals(RestAdvice.Unknown, coach.advise(null, 600))
    }

    @Test
    fun `on track early in the trip`() {
        assertEquals(RestAdvice.OnTrack(90 * 60), coach.advise(30 * 60, null))
    }

    @Test
    fun `arriving before the two hour mark needs no break`() {
        assertEquals(RestAdvice.ArriveBeforeRest(30 * 60), coach.advise(90 * 60, 20 * 60))
    }

    @Test
    fun `warns in the last fifteen minutes when the destination is further`() {
        assertEquals(RestAdvice.RestSoon(10 * 60), coach.advise(110 * 60, 60 * 60))
    }

    @Test
    fun `overdue after two hours`() {
        assertEquals(RestAdvice.Overdue(125 * 60), coach.advise(125 * 60, 5 * 60))
    }
}

class RangeCheckTest {
    private val check = RangeCheck()

    @Test
    fun `reserve is at least thirty km and grows with long routes`() {
        assertEquals(30_000, check.reserveFor(50_000))
        assertEquals(60_000, check.reserveFor(400_000))
    }

    @Test
    fun `unknown when either value is missing`() {
        assertEquals(RangeAdvice.Unknown, check.advise(null, 10_000))
        assertEquals(RangeAdvice.Unknown, check.advise(10_000, null))
    }

    @Test
    fun `enough tight and short`() {
        assertEquals(RangeAdvice.Enough(100_000), check.advise(150_000, 50_000))
        assertEquals(RangeAdvice.Tight(14_000), check.advise(62_000, 48_000))
        assertEquals(RangeAdvice.Short(8_000), check.advise(40_000, 48_000))
    }
}

class DriveCareAdvisorTest {
    private val advisor = DriveCareAdvisor()

    @Test
    fun `no take-a-break alert while the driver is already resting`() {
        val trip = TripSnapshot(etaSeconds = 3600, remainingRouteMeters = 80_000, isRouteDriving = true)
        val car = CarSnapshot(tripDrivingSeconds = 125 * 60, rangeRemainingMeters = 500_000)
        assertEquals(listOf("rest:overdue"), advisor.evaluate(trip, car, isParked = true).alerts.map { it.key })
        assertTrue(advisor.evaluate(trip, car, isParked = true, resting = true).alerts.isEmpty())
    }

    @Test
    fun `range shortfall outranks rest advice`() {
        val report = advisor.evaluate(
            trip = TripSnapshot(etaSeconds = 3600, remainingRouteMeters = 80_000, isRouteDriving = true),
            car = CarSnapshot(tripDrivingSeconds = 110 * 60, rangeRemainingMeters = 50_000),
            isParked = false,
        )
        assertEquals(listOf("range:short", "rest:soon"), report.alerts.map { it.key })
        assertEquals(Severity.WARNING, report.headline?.severity)
    }

    @Test
    fun `exit checks only appear when parked`() {
        val car = CarSnapshot(
            doorLocked = mapOf(DoorPosition.FRONT_LEFT to true, DoorPosition.REAR_RIGHT to false),
            headlightsOn = true,
        )
        val driving = advisor.evaluate(TripSnapshot(), car, isParked = false)
        assertTrue(driving.alerts.isEmpty())

        val parked = advisor.evaluate(TripSnapshot(arrived = true), car, isParked = true)
        assertEquals(listOf(DoorPosition.REAR_RIGHT), parked.unlockedDoors)
        assertTrue(parked.headlightsLeftOn)
        assertFalse(parked.exitChecklistDone)
        assertEquals(listOf("doors:REAR_RIGHT", "headlights"), parked.alerts.map { it.key })
    }

    @Test
    fun `ev wording uses charging`() {
        val report = advisor.evaluate(
            trip = TripSnapshot(remainingRouteMeters = 80_000),
            car = CarSnapshot(rangeRemainingMeters = 50_000, energyType = EnergyType.EV, energyPercent = 12),
            isParked = false,
        )
        assertEquals("이대로는 도착 전에 충전이 필요해요", report.alerts.first().title)
        assertTrue(report.alerts.any { it.key == "energy:low" && it.spoken.startsWith("배터리가") })
    }
}

class AnnouncementGateTest {
    private fun alert(key: String, severity: Severity) = CareAlert(key, severity, key, key)

    @Test
    fun `announces once, again on escalation, and again after clearing`() {
        val gate = AnnouncementGate()
        val soon = alert("rest", Severity.CAUTION)
        val overdue = alert("rest", Severity.WARNING)

        assertEquals(listOf(soon), gate.toAnnounce(listOf(soon)))
        assertEquals(emptyList<CareAlert>(), gate.toAnnounce(listOf(soon)))
        assertEquals(listOf(overdue), gate.toAnnounce(listOf(overdue)))
        assertEquals(emptyList<CareAlert>(), gate.toAnnounce(emptyList()))
        assertEquals(listOf(soon), gate.toAnnounce(listOf(soon)))
    }
}

class FormattingTest {
    @Test
    fun `durations round up to the minute`() {
        assertEquals("1분", formatDuration(30))
        assertEquals("45분", formatDuration(45 * 60))
        assertEquals("2시간", formatDuration(120 * 60))
        assertEquals("1시간 5분", formatDuration(65 * 60))
    }

    @Test
    fun `km keeps one decimal below ten`() {
        assertEquals("8.0km", formatKm(8_000))
        assertEquals("48km", formatKm(48_400))
    }

    @Test
    fun `briefing mentions destination and remaining time`() {
        val trip = TripSnapshot(etaSeconds = 38 * 60, destinationName = "강릉역", remainingRouteMeters = 48_000)
        val car = CarSnapshot(tripDrivingSeconds = 30 * 60, rangeRemainingMeters = 300_000)
        val report = DriveCareAdvisor().evaluate(trip, car, isParked = false)
        assertEquals(
            "강릉역까지 38분 남았어요. 쉬지 않아도 2시간 안에 도착해요. 주행 가능 거리는 충분해요.",
            briefing(trip, car, report),
        )
    }
}
