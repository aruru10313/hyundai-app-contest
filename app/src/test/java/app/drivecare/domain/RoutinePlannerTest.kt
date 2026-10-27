package app.drivecare.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePlannerTest {
    private val planner = RoutinePlanner()
    private val allLocked = DoorPosition.entries.associateWith { true }

    @Test
    fun `suggests exit only after arriving`() {
        assertEquals(Routine.DEPARTURE, planner.suggested(TripSnapshot()))
        assertEquals(Routine.EXIT, planner.suggested(TripSnapshot(arrived = true)))
    }

    @Test
    fun `departure turns on climate, closes windows, locks and lights`() {
        val car = CarSnapshot(
            doorLocked = allLocked + (DoorPosition.REAR_LEFT to false),
            headlightsOn = false,
            climateOn = false,
            windowsOpen = true,
        )
        val plan = planner.plan(Routine.DEPARTURE, car)
        assertEquals(
            listOf(
                VehicleAction.Climate(on = true, targetC = 22f),
                VehicleAction.CloseWindows,
                VehicleAction.LockDoors(true),
                VehicleAction.Headlights(true),
            ),
            plan.actions,
        )
        assertEquals("출발 준비 완료. 공조 22도, 창문 닫힘, 문 잠금, 전조등 켜짐. 안전 운전하세요.", plan.summary)
    }

    @Test
    fun `departure is idempotent`() {
        val ready = CarSnapshot(
            doorLocked = allLocked,
            headlightsOn = true,
            climateOn = true,
            targetTempC = 22f,
            windowsOpen = false,
        )
        val plan = planner.plan(Routine.DEPARTURE, ready)
        assertTrue(plan.actions.isEmpty())
        assertEquals("출발 준비가 이미 끝났어요. 안전 운전하세요.", plan.summary)
    }

    @Test
    fun `exit turns things off and names unlocked doors`() {
        val car = CarSnapshot(
            doorLocked = allLocked + (DoorPosition.REAR_RIGHT to false),
            headlightsOn = true,
            climateOn = true,
            windowsOpen = false,
        )
        val plan = planner.plan(Routine.EXIT, car)
        assertEquals(listOf(VehicleAction.Headlights(false), VehicleAction.Climate(on = false)), plan.actions)
        assertEquals("정리 완료. 전조등 꺼짐, 공조 꺼짐. 내린 뒤 뒷좌석 오른쪽 문을 잠가 주세요.", plan.summary)
    }
}
