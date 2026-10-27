package app.drivecare.data

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.content.Context
import android.os.Handler
import android.os.Looper
import app.drivecare.domain.CarSnapshot
import app.drivecare.domain.DoorPosition
import app.drivecare.domain.EnergyType
import app.drivecare.domain.VehicleAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VehicleSnapshot(
    val source: String,
    val car: CarSnapshot = CarSnapshot(),
    /** Vehicle domains that failed or are unsupported on this car, e.g. "Odometer". */
    val unavailable: Set<String> = emptySet(),
    /** Result of the last control request, shown briefly on the home screen. */
    val controlMessage: String? = null,
    val error: String? = null,
)

interface VehicleStateProvider {
    val state: StateFlow<VehicleSnapshot>

    fun connect()
    fun perform(action: VehicleAction)
    fun release()
}

class MockVehicleStateProvider(private val demo: DemoDrive) : VehicleStateProvider {
    override val state: StateFlow<VehicleSnapshot> get() = demo.vehicle

    override fun connect() = Unit
    override fun perform(action: VehicleAction) = demo.perform(action)
    override fun release() = Unit
}

/**
 * Reads Vehicle State and sends Vehicle Control requests using APIs verified in the Vehicle 2.3.2
 * artifact. Every getter is a one-shot async call, so state is polled on a fixed interval and
 * refreshed right after each control request.
 *
 * Permissions (vehicle-permission doc / PleosPermission constants): Odometer → CAR_DRIVE,
 * Fuel/EvBattery → CAR_ENERGY, Door → CAR_DOORS, Light → CAR_LIGHTS, HVAC → CAR_CLIMATE,
 * Window → CAR_WINDOWS; control uses the CONTROL_CAR_* counterparts.
 * Tire is deliberately not used: the official sample's PublicApiPolicy blocks Tire.getTirePressure.
 *
 * UNVERIFIED: Vehicle Control is listed as "준비 중" in the Playground, so set calls may be refused
 * until the app is approved; failures are reported through [VehicleSnapshot.controlMessage].
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
    /**
     * Door areas this car reports lock state for. Cars differ: the Pleos emulator exposes one area
     * covering all doors rather than one per door, so per-door reads fail there.
     */
    @Volatile private var lockAreas: List<DoorArea>? = null
    private val windowPositions = java.util.concurrent.ConcurrentHashMap<WindowValue, Int>()
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
            { meters -> updateCar("Odometer") { copy(rangeRemainingMeters = meters?.toInt()) } },
            { markUnavailable("Odometer") },
        )
        // UNVERIFIED: assumed kilometers, matching AAOS PERF_ODOMETER.
        odometer.getCurrentDistanceDriven(
            { km -> updateCar("Odometer") { copy(odometerMeters = km?.let { (it * 1000).toInt() }) } },
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
        val areas = lockAreas
        if (areas == null) {
            door.checkDoorLockCapabilities(
                { supported: List<DoorArea> ->
                    lockAreas = supported.filter { it.positions().isNotEmpty() }.ifEmpty { perDoorAreas }
                    readDoorLocks()
                },
                { lockAreas = perDoorAreas; readDoorLocks() },
            )
        } else {
            readDoorLocks()
        }

        vehicle.getLight().getCurrentLowBeamLightState(
            { light -> updateCar("Light") { copy(headlightsOn = light?.let { it == LightState.ON }) } },
            { markUnavailable("Light") },
        )

        val hvac = vehicle.getHVAC()
        hvac.getHvacPowerState(
            driverSeat,
            { on -> updateCar("HVAC") { copy(climateOn = on) } },
            { markUnavailable("HVAC") },
        )
        hvac.getHvacTemperature(
            driverSeat,
            TemperatureUnit.CELSIUS,
            { celsius -> updateCar("HVAC") { copy(targetTempC = celsius) } },
            { markUnavailable("HVAC") },
        )
        hvac.getHvacTemperatureCurrent(
            driverSeat,
            TemperatureUnit.CELSIUS,
            { celsius -> updateCar("HVAC") { copy(cabinTempC = celsius) } },
            { markUnavailable("HVAC") },
        )

        val window = vehicle.getWindow()
        sideWindows.forEach { value ->
            // UNVERIFIED: assumed 0 = fully closed.
            window.getCurrentWindowPosition(
                WindowArea(value),
                { position ->
                    if (position != null) windowPositions[value] = position else windowPositions.remove(value)
                    updateCar("Window") {
                        copy(windowsOpen = windowPositions.takeIf { it.isNotEmpty() }?.values?.any { it > 0 })
                    }
                },
                { markUnavailable("Window") },
            )
        }
    }

    private fun readDoorLocks() {
        val door = vehicle.getDoor()
        (lockAreas ?: perDoorAreas).forEach { area ->
            val positions = area.positions()
            if (positions.isEmpty()) return@forEach
            // true = locked: verified on the Pleos emulator, where Virtual Console "Door Lock: On"
            // reads back as true.
            door.getDoorLockState(
                area,
                { locked ->
                    updateCar("Door") {
                        if (locked == null) copy(doorLocked = doorLocked - positions.toSet())
                        else copy(doorLocked = doorLocked + positions.associateWith { locked })
                    }
                },
                { markUnavailable("Door") },
            )
        }
    }

    override fun perform(action: VehicleAction) {
        if (!initialized) {
            reportControl("차량에 연결되지 않았어요")
            return
        }
        val onFailed: (Exception) -> Unit = {
            reportControl("이 차량에서는 아직 원격 제어를 쓸 수 없어요 (Pleos Vehicle Control 승인 필요)")
        }
        try {
            when (action) {
                is VehicleAction.LockDoors -> (lockAreas ?: perDoorAreas).forEach { area ->
                    vehicle.getDoor().setDoorLock(area, action.locked, { _, _ -> refreshSoon() }, onFailed)
                }
                is VehicleAction.Headlights -> vehicle.getLight().setLowBeamLightSwitch(
                    if (action.on) LightSwitch.ON else LightSwitch.OFF,
                    { refreshSoon() },
                    onFailed,
                )
                is VehicleAction.Climate -> {
                    val hvac = vehicle.getHVAC()
                    hvac.setHvacPower(driverSeat, action.on, { _, _ -> refreshSoon() }, onFailed)
                    action.targetC?.let { celsius ->
                        hvac.setHvacTemperature(
                            driverSeat,
                            celsius,
                            TemperatureUnit.CELSIUS,
                            { _, _ -> refreshSoon() },
                            onFailed,
                        )
                    }
                }
                VehicleAction.CloseWindows -> sideWindows.forEach { value ->
                    vehicle.getWindow().setWindowPosition(WindowArea(value), 0, { _, _ -> refreshSoon() }, onFailed)
                }
            }
        } catch (exception: Exception) {
            onFailed(exception)
        }
    }

    private fun refreshSoon() {
        reportControl(null)
        handler.postDelayed({ readAll() }, 500L)
    }

    private fun reportControl(message: String?) {
        mutableState.update { it.copy(controlMessage = message) }
    }

    private fun updateCar(domain: String, change: CarSnapshot.() -> CarSnapshot) {
        mutableState.update { it.copy(car = it.car.change(), unavailable = it.unavailable - domain) }
    }

    private fun markUnavailable(domain: String) {
        mutableState.update { it.copy(unavailable = it.unavailable + domain) }
    }

    override fun release() {
        handler.removeCallbacksAndMessages(null)
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
        val doorPositions = mapOf(
            DoorValue.ROW_1_LEFT to DoorPosition.FRONT_LEFT,
            DoorValue.ROW_1_RIGHT to DoorPosition.FRONT_RIGHT,
            DoorValue.ROW_2_LEFT to DoorPosition.REAR_LEFT,
            DoorValue.ROW_2_RIGHT to DoorPosition.REAR_RIGHT,
        )
        val perDoorAreas = doorPositions.keys.map { DoorArea(it) }

        /** Passenger doors an area covers (empty for hood or tailgate areas). */
        fun DoorArea.positions(): List<DoorPosition> = values.mapNotNull { doorPositions[it] }
        val sideWindows = listOf(
            WindowValue.ROW_1_LEFT,
            WindowValue.ROW_1_RIGHT,
            WindowValue.ROW_2_LEFT,
            WindowValue.ROW_2_RIGHT,
        )
        val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    }
}
