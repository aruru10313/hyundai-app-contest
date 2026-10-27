package app.drivecare.data

import android.content.Context
import app.drivecare.domain.GeoPoint
import app.drivecare.domain.RestArea
import org.json.JSONArray

/**
 * Korean highway service areas (휴게소) and drowsy-driver shelters (졸음쉼터), bundled so the app
 * works offline. Data © OpenStreetMap contributors, ODbL (highway=services / highway=rest_area,
 * filtered to names containing 휴게소 or 쉼터).
 */
object RestAreaRepository {
    @Volatile private var cached: List<RestArea>? = null

    fun load(context: Context): List<RestArea> = cached ?: runCatching {
        val json = context.assets.open("rest_areas.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            RestArea(
                name = o.getString("name"),
                point = GeoPoint(o.getDouble("lat"), o.getDouble("lon")),
                isServiceArea = o.getString("kind") == "휴게소",
            )
        }
    }.getOrDefault(emptyList()).also { cached = it }
}
