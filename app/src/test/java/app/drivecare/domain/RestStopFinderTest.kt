package app.drivecare.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test
    fun `distance and bearing match known values`() {
        val seoul = GeoPoint(37.5665, 126.9780)
        val busan = GeoPoint(35.1796, 129.0756)
        val km = Geo.distanceMeters(seoul, busan) / 1000
        assertTrue("was $km", km in 320.0..330.0)
        val bearing = Geo.bearingDegrees(seoul, busan)
        assertTrue("was $bearing", bearing in 130.0..150.0)
    }

    @Test
    fun `angle between wraps around north`() {
        assertEquals(20.0, Geo.angleBetween(350.0, 10.0), 1e-9)
        assertEquals(180.0, Geo.angleBetween(0.0, 180.0), 1e-9)
    }
}

class RestStopFinderTest {
    // Real coordinates (OpenStreetMap) on the Yeongdong Expressway towards Gangneung.
    private val start = GeoPoint(37.55, 128.30)
    private val gangneung = GeoPoint(37.7637, 128.8996)
    private val pyeongchangEast = RestArea("평창 휴게소 (강릉 방향)", GeoPoint(37.60574, 128.45341), true)
    private val pyeongchangWest = RestArea("평창 휴게소 (인천 방향)", GeoPoint(37.61036, 128.4637), true)
    private val daegwallyeong = RestArea("대관령휴게소", GeoPoint(37.6844, 128.75445), true)
    private val shelter = RestArea("대관령졸음쉼터", GeoPoint(37.67459, 128.72134), false)
    private val behind = RestArea("횡성휴게소", GeoPoint(37.46486, 128.13462), true)
    private val heading = Geo.bearingDegrees(start, gangneung)

    @Test
    fun `picks the stop ahead in the direction of the destination`() {
        val finder = RestStopFinder(listOf(behind, pyeongchangWest, pyeongchangEast))
        val pick = finder.nextAhead(start, heading, gangneung, "강릉역")
        assertEquals("평창 휴게소 (강릉 방향)", pick?.area?.name)
        assertTrue(pick!!.distanceMeters in 10_000..20_000)
    }

    @Test
    fun `ignores stops behind the car`() {
        assertNull(RestStopFinder(listOf(behind)).nextAhead(start, heading, gangneung))
    }

    @Test
    fun `prefers a service area over a nearby shelter`() {
        val near = GeoPoint(37.64, 128.62)
        val pick = RestStopFinder(listOf(shelter, daegwallyeong)).nextAhead(near, Geo.bearingDegrees(near, gangneung), gangneung)
        assertEquals("대관령휴게소", pick?.area?.name)
    }

    @Test
    fun `nothing within range returns null`() {
        val far = RestStopFinder(listOf(daegwallyeong), maxMeters = 5_000.0)
        assertNull(far.nextAhead(start, heading, gangneung))
    }

    @Test
    fun `spoken place reads distance then name`() {
        assertEquals("12km 앞 대관령휴게소", RestStopSuggestion(daegwallyeong, 12_300).spokenPlace)
    }
}
