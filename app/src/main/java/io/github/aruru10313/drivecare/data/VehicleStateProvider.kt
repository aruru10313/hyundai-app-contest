package io.github.aruru10313.drivecare.data

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.LightState
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.aruru10313.drivecare.domain.CarSnapshot
import io.github.aruru10313.drivecare.domain.DoorPosition
import io.github.aruru10313.drivecare.domain.EnergyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VehicleSnapshot(
    val source: String,
    val car: CarSnapshot = CarSnapshot(),
    /** Vehicle domains that failed or are unsupported on this car, e.g. "Odometer". */
    val unavailable: Set<String> = emptySet(),
    val error: String? = null,
)

interface VehicleStateProvider {
    val state: StateFlow<VehicleSnapshot>

    fun connect()
    fun release()
}

class MockVehicleStateProvider(private val demo: DemoDrive) : VehicleStateProvider {
    override val state: StateFlow<VehicleSnapshot> get() = demo.vehicle

    override fun connect() = Unit
    override fun release() = Unit
}

/**
 * Reads the Vehicle State APIs verified in the Vehicle 2.3.2 artifact. Every getter is a one-shot
 * async call, so the provider polls them on a fixed interval.
 *
 * Permissions (document.pleos.ai .../Vehicle/vehicle-permission): Odometer → CAR_DRIVE,
 * Fuel/EvBattery → CAR_ENERGY, Door → CAR_DOORS, Light → CAR_LIGHTS.
 * Tire is deliberately not used: the official sample's PublicApiPolicy blocks Tire.getTirePressure.
 */
class PleosVehicleStateProvider(
    context: Context,
    private val pollMillis: Long = 15_000L,
) : VehicleStateProvider {
    private val vehicle = Vehicle(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(VehicleSnapshot(source = "Pleos 차량"))
    override val state: StateFlow<VehicleSnapshot> = mutableState.asStateFlow()

    private var initialized = false
    private val poll = object : Runnable {
        override fun run() {
            readAll()
            handler.postDelayed(this, pollMillis)
        }
    }

    override fun connect() {
        if (initialized) return
        try {
            vehicle.initialize()
            initialized = true
            handler.post(poll)
        } catch (exception: Exception) {
            mutableState.update { it.copy(error = exception.toVehicleError()) }
        }
    }

    private fun readAll() {
        val odometer = vehicle.getOdometer()
        // UNVERIFIED: units are not documented. Assumed seconds, matching AAOS trip properties.
        odometer.getTripCurrentDrivingTime(
            { seconds -> updateCar("Odometer") { copy(tripDrivingSeconds = seconds) } },
            { markUnavailable("Odometer") },
        )
        // UNVERIFIED: assumed meters, matching AAOS RANGE_REMAINING.
        odometer.getRangeRemainingDistance(
            { meters ->
                updateCar("Odometer") { copy(rangeRemainingMeters = meters?.toInt()) }
            },
            { markUnavailable("Odometer") },
        )

        // An EV reports state of charge; a combustion car reports fuel percent. Prefer EV data
        // when it arrives so a hybrid that reports both shows its battery.
        vehicle.getEvBattery().getEvBatteryStateOfCharge(
            { percent ->
                if (percent != null) {
                    updateCar("EvBattery") { copy(energyPercent = percent, energyType = EnergyType.EV) }
                }
            },
            { markUnavailable("EvBattery") },
        )
        vehicle.getFuel().getFuelPercent(
            { percent ->
                updateCar("Fuel") {
                    if (energyType == EnergyType.EV || percent == null) this
                    else copy(energyPercent = percent, energyType = EnergyType.FUEL)
                }
            },
            { markUnavailable("Fuel") },
        )

        val door = vehicle.getDoor()
        doorAreas.forEach { (position, value) ->
            // UNVERIFIED: assumed true = locked, from the API name getDoorLockState.
            door.getDoorLockState(
                DoorArea(value),
                { locked ->
                    updateCar("Door") {
                        if (locked == null) copy(doorLocked = doorLocked - position)
                        else copy(doorLocked = doorLocked + (position to locked))
                    }
                },
                { markUnavailable("Door") },
            )
        }

        vehicle.getLight().getCurrentLowBeamLightState(
            { light ->
                updateCar("Light") { copy(headlightsOn = light?.let { it == LightState.ON }) }
            },
            { markUnavailable("Light") },
        )
    }

    private fun updateCar(domain: String, change: CarSnapshot.() -> CarSnapshot) {
        mutableState.update { it.copy(car = it.car.change(), unavailable = it.unavailable - domain) }
    }

    private fun markUnavailable(domain: String) {
        mutableState.update { it.copy(unavailable = it.unavailable + domain) }
    }

    override fun release() {
        handler.removeCallbacks(poll)
        if (!initialized) return
        try {
            vehicle.release()
        } catch (exception: Exception) {
            mutableState.update { it.copy(error = exception.toVehicleError()) }
        } finally {
            initialized = false
        }
    }

    private fun Exception.toVehicleError(): String =
        message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

    private companion object {
        val doorAreas = listOf(
            DoorPosition.FRONT_LEFT to DoorValue.ROW_1_LEFT,
            DoorPosition.FRONT_RIGHT to DoorValue.ROW_1_RIGHT,
            DoorPosition.REAR_LEFT to DoorValue.ROW_2_LEFT,
            DoorPosition.REAR_RIGHT to DoorValue.ROW_2_RIGHT,
        )
    }
}
