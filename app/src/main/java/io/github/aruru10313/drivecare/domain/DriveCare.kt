package io.github.aruru10313.drivecare.domain

/** Route data from the head unit's own navigation. Null means "not provided right now". */
data class TripSnapshot(
    val etaSeconds: Int? = null,
    val remainingRouteMeters: Int? = null,
    val destinationName: String? = null,
    val isRouteDriving: Boolean = false,
    val arrived: Boolean = false,
)

enum class EnergyType { FUEL, EV }

enum class DoorPosition(val label: String) {
    FRONT_LEFT("운전석"),
    FRONT_RIGHT("조수석"),
    REAR_LEFT("뒷좌석 왼쪽"),
    REAR_RIGHT("뒷좌석 오른쪽"),
}

/** Vehicle readings. Null means the vehicle did not (or could not) report the value. */
data class CarSnapshot(
    val tripDrivingSeconds: Int? = null,
    val rangeRemainingMeters: Int? = null,
    val energyPercent: Int? = null,
    val energyType: EnergyType? = null,
    val doorLocked: Map<DoorPosition, Boolean> = emptyMap(),
    val headlightsOn: Boolean? = null,
)

sealed interface RestAdvice {
    data object Unknown : RestAdvice
    data class OnTrack(val secondsUntilRest: Int) : RestAdvice
    data class ArriveBeforeRest(val secondsUntilRest: Int) : RestAdvice
    data class RestSoon(val secondsUntilRest: Int) : RestAdvice
    data class Overdue(val drivenSeconds: Int) : RestAdvice
}

/**
 * Suggests a break after two hours of continuous driving, the guideline Korean road-safety
 * campaigns use ("2시간마다 15분 휴식"). If the destination comes first, no break is needed.
 */
class RestCoach(
    private val intervalSeconds: Int = 2 * 60 * 60,
    private val warnBeforeSeconds: Int = 15 * 60,
) {
    init {
        require(intervalSeconds > 0)
        require(warnBeforeSeconds in 0..intervalSeconds)
    }

    fun advise(tripDrivingSeconds: Int?, etaSeconds: Int?): RestAdvice {
        if (tripDrivingSeconds == null || tripDrivingSeconds < 0) return RestAdvice.Unknown
        if (tripDrivingSeconds >= intervalSeconds) return RestAdvice.Overdue(tripDrivingSeconds)
        val untilRest = intervalSeconds - tripDrivingSeconds
        return when {
            etaSeconds != null && etaSeconds in 0..untilRest -> RestAdvice.ArriveBeforeRest(untilRest)
            untilRest <= warnBeforeSeconds -> RestAdvice.RestSoon(untilRest)
            else -> RestAdvice.OnTrack(untilRest)
        }
    }
}

sealed interface RangeAdvice {
    data object Unknown : RangeAdvice
    data class Enough(val spareMeters: Int) : RangeAdvice
    /** Reaches the destination, but with less than the safety reserve left. */
    data class Tight(val spareMeters: Int) : RangeAdvice
    data class Short(val missingMeters: Int) : RangeAdvice
}

/** Compares the car's remaining range with the remaining route plus a safety reserve. */
class RangeCheck(
    private val minReserveMeters: Int = 30_000,
    private val reserveRatio: Double = 0.15,
) {
    init {
        require(minReserveMeters >= 0)
        require(reserveRatio >= 0.0)
    }

    fun reserveFor(routeMeters: Int): Int =
        maxOf(minReserveMeters, (routeMeters * reserveRatio).toInt())

    fun advise(rangeMeters: Int?, routeMeters: Int?): RangeAdvice {
        if (rangeMeters == null || routeMeters == null || rangeMeters < 0 || routeMeters < 0) {
            return RangeAdvice.Unknown
        }
        val spare = rangeMeters - routeMeters
        return when {
            spare < 0 -> RangeAdvice.Short(-spare)
            spare < reserveFor(routeMeters) -> RangeAdvice.Tight(spare)
            else -> RangeAdvice.Enough(spare)
        }
    }
}

enum class Severity { INFO, CAUTION, WARNING }

data class CareAlert(
    /** Stable identity, so the same situation is announced only once. */
    val key: String,
    val severity: Severity,
    val title: String,
    val spoken: String,
)

data class DriveCareReport(
    val rest: RestAdvice,
    val range: RangeAdvice,
    val unlockedDoors: List<DoorPosition>,
    val headlightsLeftOn: Boolean,
    /** Highest severity first. */
    val alerts: List<CareAlert>,
) {
    val headline: CareAlert? get() = alerts.firstOrNull()
    val exitChecklistDone: Boolean get() = unlockedDoors.isEmpty() && !headlightsLeftOn
}

class DriveCareAdvisor(
    private val restCoach: RestCoach = RestCoach(),
    private val rangeCheck: RangeCheck = RangeCheck(),
    private val lowEnergyPercent: Int = 15,
) {
    /**
     * @param isParked exit checks (doors, headlights) only matter once the car is parked,
     *   so they are suppressed while driving.
     */
    fun evaluate(trip: TripSnapshot, car: CarSnapshot, isParked: Boolean): DriveCareReport {
        val rest = if (trip.arrived) RestAdvice.Unknown else restCoach.advise(car.tripDrivingSeconds, trip.etaSeconds)
        val range = if (trip.arrived) RangeAdvice.Unknown else rangeCheck.advise(car.rangeRemainingMeters, trip.remainingRouteMeters)
        val unlocked = if (isParked) car.doorLocked.filterValues { locked -> !locked }.keys.sorted() else emptyList()
        val lightsOn = isParked && car.headlightsOn == true

        val alerts = buildList {
            rangeAlert(range, car.energyType)?.let(::add)
            lowEnergyAlert(car)?.let(::add)
            restAlert(rest)?.let(::add)
            if (unlocked.isNotEmpty()) {
                val names = unlocked.joinToString(", ") { it.label }
                add(
                    CareAlert(
                        key = "doors:${unlocked.joinToString(",")}",
                        severity = Severity.WARNING,
                        title = "잠기지 않은 문: $names",
                        spoken = "$names 문이 잠기지 않았어요. 내리기 전에 확인해 주세요.",
                    ),
                )
            }
            if (lightsOn) {
                add(
                    CareAlert(
                        key = "headlights",
                        severity = Severity.CAUTION,
                        title = "전조등이 켜져 있어요",
                        spoken = "전조등이 켜져 있어요. 배터리 방전에 주의하세요.",
                    ),
                )
            }
        }.sortedByDescending { it.severity }

        return DriveCareReport(rest, range, unlocked, lightsOn, alerts)
    }

    private fun rangeAlert(range: RangeAdvice, type: EnergyType?): CareAlert? {
        val ev = type == EnergyType.EV
        val refuel = if (ev) "충전" else "주유"
        val refuelSubject = if (ev) "충전이" else "주유가"
        val refuelObject = if (ev) "충전을" else "주유를"
        return when (range) {
            is RangeAdvice.Short -> CareAlert(
                key = "range:short",
                severity = Severity.WARNING,
                title = "이대로는 도착 전에 $refuelSubject 필요해요",
                spoken = "주행 가능 거리가 목적지까지 ${formatKm(range.missingMeters)} 부족해요. 가까운 곳에서 ${refuel}해 주세요.",
            )
            is RangeAdvice.Tight -> CareAlert(
                key = "range:tight",
                severity = Severity.CAUTION,
                title = "도착 후 ${formatKm(range.spareMeters)} 남아요",
                spoken = "목적지까지는 갈 수 있지만 도착하면 ${formatKm(range.spareMeters)}만 남아요. 여유 있게 $refuelObject 계획하세요.",
            )
            else -> null
        }
    }

    private fun lowEnergyAlert(car: CarSnapshot): CareAlert? {
        val percent = car.energyPercent ?: return null
        if (percent > lowEnergyPercent) return null
        val what = if (car.energyType == EnergyType.EV) "배터리" else "연료"
        return CareAlert(
            key = "energy:low",
            severity = Severity.CAUTION,
            title = "$what ${percent}%",
            spoken = "${what}가 ${percent}퍼센트 남았어요.",
        )
    }

    private fun restAlert(rest: RestAdvice): CareAlert? = when (rest) {
        is RestAdvice.Overdue -> CareAlert(
            key = "rest:overdue",
            severity = Severity.WARNING,
            title = "${formatDuration(rest.drivenSeconds)}째 운전 중",
            spoken = "${formatDuration(rest.drivenSeconds)}째 쉬지 않고 운전하고 있어요. 다음 휴게소에서 15분 쉬어 가세요.",
        )
        is RestAdvice.RestSoon -> CareAlert(
            key = "rest:soon",
            severity = Severity.CAUTION,
            title = "${formatDuration(rest.secondsUntilRest)} 뒤 휴식 권장",
            spoken = "곧 운전 2시간이 돼요. ${formatDuration(rest.secondsUntilRest)} 안에 휴게소에 들러 쉬어 가세요.",
        )
        else -> null
    }
}

/**
 * Decides which alerts should be spoken: a new situation, or one that got more severe.
 * An alert that clears and later returns is announced again.
 */
class AnnouncementGate {
    private val announced = mutableMapOf<String, Severity>()

    fun toAnnounce(alerts: List<CareAlert>): List<CareAlert> {
        val current = alerts.associateBy { it.key }
        announced.keys.retainAll(current.keys)
        return alerts.filter { alert ->
            val previous = announced[alert.key]
            (previous == null || alert.severity > previous).also { isNew ->
                if (isNew) announced[alert.key] = alert.severity
            }
        }
    }
}

/** Spoken summary for the one big "브리핑" button. */
fun briefing(trip: TripSnapshot, car: CarSnapshot, report: DriveCareReport): String = buildList {
    if (trip.arrived) {
        add("목적지에 도착했어요.")
    } else if (trip.etaSeconds != null) {
        val where = trip.destinationName?.let { "${it}까지" } ?: "목적지까지"
        add("$where ${formatDuration(trip.etaSeconds)} 남았어요.")
    }
    when (val rest = report.rest) {
        is RestAdvice.ArriveBeforeRest -> add("쉬지 않아도 2시간 안에 도착해요.")
        is RestAdvice.OnTrack -> add("휴식까지 ${formatDuration(rest.secondsUntilRest)} 남았어요.")
        else -> Unit
    }
    if (report.range is RangeAdvice.Enough) add("주행 가능 거리는 충분해요.")
    report.alerts.forEach { add(it.spoken) }
    if (trip.arrived && report.exitChecklistDone && car.doorLocked.isNotEmpty()) {
        add("문과 전조등 모두 확인됐어요.")
    }
}.ifEmpty { listOf("차량 정보를 확인하고 있어요.") }.joinToString(" ")

fun formatDuration(seconds: Int): String {
    val totalMinutes = (seconds.coerceAtLeast(0) + 59) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "${minutes}분"
        minutes == 0 -> "${hours}시간"
        else -> "${hours}시간 ${minutes}분"
    }
}

fun formatKm(meters: Int): String {
    val km = meters.coerceAtLeast(0) / 1000.0
    return if (km < 10) String.format(java.util.Locale.ROOT, "%.1fkm", km) else "${km.toInt()}km"
}
