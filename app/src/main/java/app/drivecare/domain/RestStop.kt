package app.drivecare.domain

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double)

/**
 * A place to take a break: a highway service area (휴게소) or a drowsy-driver shelter (졸음쉼터).
 * [hasFuel] / [hasCharger] are positive-only: true means map data confirms a gas station / EV
 * charger nearby; false means unknown, not "none".
 */
data class RestArea(
    val name: String,
    val point: GeoPoint,
    val isServiceArea: Boolean,
    val hasFuel: Boolean = false,
    val hasCharger: Boolean = false,
) {
    fun canRefuel(type: EnergyType?): Boolean = if (type == EnergyType.EV) hasCharger else hasFuel
}

data class RestStopSuggestion(
    val area: RestArea,
    val distanceMeters: Int,
    /** The stop was picked because the car can also refuel or charge there. */
    val refuelHere: Boolean = false,
) {
    /** "12km 앞 대관령휴게소" */
    val spokenPlace: String get() = "${formatKm(distanceMeters)} 앞 ${area.name}"
}

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** Initial bearing from a to b, degrees clockwise from north (0..360). */
    fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun angleBetween(a: Double, b: Double): Double {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }
}

/**
 * Picks the next rest stop ahead of the car without route geometry: close enough to reach soon,
 * roughly in the direction of travel, and nearer to the destination than the car is now.
 */
class RestStopFinder(
    private val areas: List<RestArea>,
    private val minMeters: Double = 2_000.0,
    private val maxMeters: Double = 40_000.0,
    private val coneDegrees: Double = 35.0,
) {
    fun nextAhead(
        position: GeoPoint,
        headingDegrees: Double,
        destination: GeoPoint? = null,
        destinationName: String? = null,
        /** Set when range is tight: stops where this energy type can be topped up win. */
        refuel: EnergyType? = null,
    ): RestStopSuggestion? {
        val wantsEnergy = refuel != null
        val toDestination = destination?.let { Geo.distanceMeters(position, it) }
        // A twin service area on the other carriageway is named for the other direction.
        val towards = destinationName?.trim()?.take(2)?.takeIf { it.length == 2 }
        return areas.asSequence()
            .map { it to Geo.distanceMeters(position, it.point) }
            .filter { (_, d) -> d in minMeters..maxMeters }
            .filter { (area, _) ->
                Geo.angleBetween(Geo.bearingDegrees(position, area.point), headingDegrees) <= coneDegrees
            }
            .filter { (area, _) ->
                destination == null || toDestination == null ||
                    Geo.distanceMeters(area.point, destination) < toDestination
            }
            .minByOrNull { (area, d) ->
                var score = d
                if (!area.isServiceArea) score += 5_000  // a 휴게소 has food and toilets; worth a little detour
                if (towards != null && "방향" in area.name && towards !in area.name) score += 20_000
                // Resting and refuelling in one stop beats two stops when range is tight, so any stop
                // within reach that can refuel wins over one that can't.
                if (wantsEnergy && area.canRefuel(refuel)) score -= maxMeters + 20_000
                score
            }
            ?.let { (area, d) ->
                RestStopSuggestion(area, d.roundToInt(), refuelHere = wantsEnergy && area.canRefuel(refuel))
            }
    }
}

/**
 * Tracks breaks taken mid-trip. A stop of at least [restSeconds] (15 min, the length Korea
 * Expressway Corporation counts as a rest) restarts the continuous-driving count; shorter stops
 * don't. The car's trip timer keeps running across stops, so the rest is recorded as a baseline.
 */
class RestTracker(private val restSeconds: Int = 15 * 60) {
    private var parkedAtMillis: Long? = null
    private var tripAtPark: Int? = null
    private var baseline = 0

    val isResting: Boolean get() = parkedAtMillis != null

    fun onParked(nowMillis: Long, tripDrivingSeconds: Int?) {
        if (parkedAtMillis != null) return
        parkedAtMillis = nowMillis
        tripAtPark = tripDrivingSeconds
    }

    /** Back on the road. Returns true if the stop counted as a full rest. */
    fun onDriving(nowMillis: Long): Boolean {
        val rested = isComplete(nowMillis)
        if (rested) tripAtPark?.let { baseline = it }
        parkedAtMillis = null
        tripAtPark = null
        return rested
    }

    fun restedSeconds(nowMillis: Long): Int =
        parkedAtMillis?.let { ((nowMillis - it) / 1000).toInt().coerceAtLeast(0) } ?: 0

    fun remainingSeconds(nowMillis: Long): Int = (restSeconds - restedSeconds(nowMillis)).coerceAtLeast(0)

    fun isComplete(nowMillis: Long): Boolean = isResting && restedSeconds(nowMillis) >= restSeconds

    /** Driving time since the last full rest. */
    fun continuousSeconds(tripDrivingSeconds: Int?): Int? = tripDrivingSeconds?.let {
        // The car's trip timer went back (new ignition cycle): the old rest no longer applies.
        if (it < baseline) baseline = 0
        it - baseline
    }

    /** New trip: forget earlier rests. */
    fun reset() {
        parkedAtMillis = null
        tripAtPark = null
        baseline = 0
    }
}
