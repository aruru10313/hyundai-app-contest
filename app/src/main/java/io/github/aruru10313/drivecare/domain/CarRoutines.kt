package io.github.aruru10313.drivecare.domain

/** One vehicle control request. The data layer maps each to a Pleos Vehicle Control call. */
sealed interface VehicleAction {
    data class LockDoors(val locked: Boolean) : VehicleAction
    data class Headlights(val on: Boolean) : VehicleAction
    data class Climate(val on: Boolean, val targetC: Float? = null) : VehicleAction
    data object CloseWindows : VehicleAction
}

enum class Routine(val label: String) {
    DEPARTURE("출발 준비"),
    EXIT("하차 모드"),
}

data class RoutinePlan(
    val routine: Routine,
    val actions: List<VehicleAction>,
    /** Spoken and shown after the routine runs. */
    val summary: String,
)

/**
 * One-touch routines behind the home screen's power button. Only actions that would change
 * something are planned, so pressing the button twice does nothing the second time.
 */
class RoutinePlanner(private val comfortTempC: Float = 22f) {

    /** Departure while parked; exit after arriving. */
    fun suggested(trip: TripSnapshot): Routine = if (trip.arrived) Routine.EXIT else Routine.DEPARTURE

    fun plan(routine: Routine, car: CarSnapshot): RoutinePlan = when (routine) {
        Routine.DEPARTURE -> departure(car)
        Routine.EXIT -> exit(car)
    }

    private fun departure(car: CarSnapshot): RoutinePlan {
        val actions = buildList {
            if (car.climateOn != true || car.targetTempC != comfortTempC) {
                add(VehicleAction.Climate(on = true, targetC = comfortTempC))
            }
            if (car.windowsOpen == true) add(VehicleAction.CloseWindows)
            if (car.doorLocked.values.any { locked -> !locked }) add(VehicleAction.LockDoors(true))
            if (car.headlightsOn != true) add(VehicleAction.Headlights(true))
        }
        val parts = actions.map {
            when (it) {
                is VehicleAction.Climate -> "공조 ${comfortTempC.toInt()}도"
                VehicleAction.CloseWindows -> "창문 닫힘"
                is VehicleAction.LockDoors -> "문 잠금"
                is VehicleAction.Headlights -> "전조등 켜짐"
            }
        }
        val summary = if (parts.isEmpty()) {
            "출발 준비가 이미 끝났어요. 안전 운전하세요."
        } else {
            "출발 준비 완료. ${parts.joinToString(", ")}. 안전 운전하세요."
        }
        return RoutinePlan(Routine.DEPARTURE, actions, summary)
    }

    private fun exit(car: CarSnapshot): RoutinePlan {
        val actions = buildList {
            if (car.windowsOpen == true) add(VehicleAction.CloseWindows)
            if (car.headlightsOn == true) add(VehicleAction.Headlights(false))
            if (car.climateOn == true) add(VehicleAction.Climate(on = false))
        }
        val done = actions.map {
            when (it) {
                VehicleAction.CloseWindows -> "창문 닫힘"
                is VehicleAction.Headlights -> "전조등 꺼짐"
                is VehicleAction.Climate -> "공조 꺼짐"
                is VehicleAction.LockDoors -> "문 잠금"
            }
        }
        val unlocked = DoorPosition.entries.filter { car.doorLocked[it] == false }
        val summary = buildString {
            append(if (done.isEmpty()) "꺼야 할 것이 없어요." else "정리 완료. ${done.joinToString(", ")}.")
            if (unlocked.isNotEmpty()) {
                append(" 내린 뒤 ${unlocked.joinToString(", ") { it.label }} 문을 잠가 주세요.")
            } else {
                append(" 내린 뒤 문을 잠가 주세요.")
            }
        }
        return RoutinePlan(Routine.EXIT, actions, summary)
    }
}
