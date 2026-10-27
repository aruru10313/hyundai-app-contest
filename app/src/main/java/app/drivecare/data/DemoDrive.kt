package app.drivecare.data

import app.drivecare.domain.CarSnapshot
import app.drivecare.domain.DoorPosition
import app.drivecare.domain.EnergyType
import app.drivecare.domain.TripSnapshot
import app.drivecare.domain.VehicleAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Scripted trip for demos without a Pleos vehicle: a long drive that is short on fuel, crosses the
 * two-hour rest mark, and arrives with a rear door unlocked and headlights on. Time runs
 * [speedUp] times faster than real time so the whole story fits in about a minute and a half.
 */
class DemoDrive(
    private val scope: CoroutineScope,
    private val speedUp: Int = 30,
    private val onDrivingChanged: (Boolean) -> Unit = {},
) {
    private val routeMeters = 52_000
    private val tripSeconds = 40 * 60
    private val mutableVehicle = MutableStateFlow(VehicleSnapshot(source = "시연 데이터", car = parkedCar()))
    private val mutableNavigation = MutableStateFlow(
        NavigationSnapshot(status = "INACTIVE", trip = TripSnapshot()),
    )
    val vehicle: StateFlow<VehicleSnapshot> = mutableVehicle.asStateFlow()
    val navigation: StateFlow<NavigationSnapshot> = mutableNavigation.asStateFlow()

    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        // Keep whatever the driver set up while parked (climate, lights, doors).
        mutableVehicle.update { snapshot ->
            snapshot.copy(
                car = snapshot.car.copy(
                    tripDrivingSeconds = 92 * 60,
                    rangeRemainingMeters = 70_000,
                    energyPercent = 14,
                    energyType = EnergyType.FUEL,
                ),
            )
        }
        setTrip(TripSnapshot(tripSeconds, routeMeters, "강릉역", isRouteDriving = true), "ROUTE_DRIVE")
        onDrivingChanged(true)
        job = scope.launch {
            while (isActive) {
                delay(1_000L)
                if (advance(speedUp)) break
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        mutableVehicle.update { it.copy(car = parkedCar()) }
        setTrip(TripSnapshot(), "INACTIVE")
        onDrivingChanged(false)
    }

    /** Stand-in for the driver locking up after the arrival reminder. */
    fun completeExitChecks() {
        mutableVehicle.update { it.copy(car = it.car.copy(doorLocked = allLocked(), headlightsOn = false)) }
    }

    fun perform(action: VehicleAction) {
        mutableVehicle.update { snapshot ->
            val car = snapshot.car
            snapshot.copy(
                controlMessage = null,
                car = when (action) {
                    is VehicleAction.LockDoors -> car.copy(doorLocked = DoorPosition.entries.associateWith { action.locked })
                    is VehicleAction.Headlights -> car.copy(headlightsOn = action.on)
                    is VehicleAction.Climate -> car.copy(climateOn = action.on, targetTempC = action.targetC ?: car.targetTempC)
                    VehicleAction.CloseWindows -> car.copy(windowsOpen = false)
                },
            )
        }
    }

    /** Cabin temperature drifts 0.5° per simulated minute toward the target while climate runs. */
    private fun towardTarget(cabin: Float, car: CarSnapshot): Float {
        val target = car.targetTempC
        if (car.climateOn != true || target == null) return cabin
        val step = 0.5f * speedUp / 60f
        return if (cabin > target) maxOf(target, cabin - step) else minOf(target, cabin + step)
    }

    /** @return true when the trip has arrived. */
    private fun advance(seconds: Int): Boolean {
        val trip = mutableNavigation.value.trip
        val eta = ((trip.etaSeconds ?: 0) - seconds).coerceAtLeast(0)
        val metersPerSecond = routeMeters.toDouble() / tripSeconds
        val driven = (metersPerSecond * seconds).toInt()
        val route = ((trip.remainingRouteMeters ?: 0) - driven).coerceAtLeast(0)
        mutableVehicle.update { snapshot ->
            val car = snapshot.car
            snapshot.copy(
                car = car.copy(
                    tripDrivingSeconds = (car.tripDrivingSeconds ?: 0) + seconds,
                    rangeRemainingMeters = ((car.rangeRemainingMeters ?: 0) - driven * 21 / 20).coerceAtLeast(0),
                    odometerMeters = car.odometerMeters?.plus(driven),
                    cabinTempC = car.cabinTempC?.let { cabin -> towardTarget(cabin, car) },
                ),
            )
        }
        if (eta > 0) {
            setTrip(trip.copy(etaSeconds = eta, remainingRouteMeters = route), "ROUTE_DRIVE")
            return false
        }
        setTrip(trip.copy(etaSeconds = 0, remainingRouteMeters = 0, isRouteDriving = false, arrived = true), "INACTIVE")
        mutableVehicle.update { snapshot ->
            snapshot.copy(car = snapshot.car.copy(doorLocked = allLocked() + (DoorPosition.REAR_RIGHT to false)))
        }
        onDrivingChanged(false)
        return true
    }

    private fun setTrip(trip: TripSnapshot, status: String) {
        mutableNavigation.value = NavigationSnapshot(status = status, trip = trip)
    }

    private fun parkedCar() = CarSnapshot(
        rangeRemainingMeters = 70_000,
        energyPercent = 14,
        energyType = EnergyType.FUEL,
        doorLocked = allLocked(),
        headlightsOn = false,
        odometerMeters = 23_418_000,
        climateOn = false,
        cabinTempC = 27.5f,
        targetTempC = 24f,
        windowsOpen = true,
    )

    private fun allLocked() = DoorPosition.entries.associateWith { true }
}
