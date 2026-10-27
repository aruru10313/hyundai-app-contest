package app.drivecare.domain

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double)

/** A place to take a break: a highway service area (휴게소) or a drowsy-driver shelter (졸음쉼터). */
data class RestArea(val name: String, val point: GeoPoint, val isServiceArea: Boolean)

data class RestStopSuggestion(val area: RestArea, val distanceMeters: Int) {
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
    ): RestStopSuggestion? {
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
                score
            }
            ?.let { (area, d) -> RestStopSuggestion(area, d.roundToInt()) }
    }
}
