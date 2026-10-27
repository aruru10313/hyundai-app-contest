package app.drivecare.data

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ParkedSpot(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val placeName: String? = null,
)

/** Remembers where the car was parked last, so the driver can find it or share it later. */
interface ParkingLocationProvider {
    val lastSpot: StateFlow<ParkedSpot?>

    /** Records the current position, tagged with the destination name when navigation knew it. */
    fun recordParking(placeName: String?)
    fun release()
}

/** Uses Pleos FusedLocation.getLastKnownLocation; the last spot survives app restarts. */
class PleosParkingLocationProvider(context: Context) : ParkingLocationProvider {
    private val prefs = context.applicationContext.getSharedPreferences("parking", Context.MODE_PRIVATE)
    private val location = FusedLocation(context.applicationContext)
    private val mutableSpot = MutableStateFlow(load())
    override val lastSpot: StateFlow<ParkedSpot?> = mutableSpot.asStateFlow()
    private var initialized = false

    init {
        try {
            location.initialize()
            initialized = location.isSupported()
        } catch (ignored: Exception) {
            initialized = false
        }
    }

    override fun recordParking(placeName: String?) {
        if (!initialized) return
        try {
            location.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(data: LocationData?) {
                    if (data == null) return
                    save(ParkedSpot(data.latitude, data.longitude, System.currentTimeMillis(), placeName))
                }

                override fun onError(exception: Exception) = Unit
            })
        } catch (ignored: Exception) {
        }
    }

    private fun save(spot: ParkedSpot) {
        prefs.edit()
            .putString("lat", spot.latitude.toString())
            .putString("lng", spot.longitude.toString())
            .putLong("time", spot.timeMillis)
            .putString("name", spot.placeName)
            .apply()
        mutableSpot.value = spot
    }

    private fun load(): ParkedSpot? {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString("lng", null)?.toDoubleOrNull() ?: return null
        return ParkedSpot(lat, lng, prefs.getLong("time", 0L), prefs.getString("name", null))
    }

    override fun release() {
        if (!initialized) return
        try {
            location.release()
        } catch (ignored: Exception) {
        }
    }
}

/** Demo stand-in: the scripted trip ends at Gangneung Station. */
class DemoParkingLocationProvider : ParkingLocationProvider {
    private val mutableSpot = MutableStateFlow<ParkedSpot?>(null)
    override val lastSpot: StateFlow<ParkedSpot?> = mutableSpot.asStateFlow()

    override fun recordParking(placeName: String?) {
        mutableSpot.value = ParkedSpot(37.7637, 128.8996, System.currentTimeMillis(), placeName)
    }

    override fun release() = Unit
}
