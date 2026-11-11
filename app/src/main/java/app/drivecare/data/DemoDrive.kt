package app.drivecare.data

import app.drivecare.domain.CarSnapshot
import app.drivecare.domain.DoorPosition
import app.drivecare.domain.EnergyType
import app.drivecare.domain.Geo
import app.drivecare.domain.GeoPoint
import app.drivecare.domain.RestArea
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
 * Scripted trip for demos without a Pleos vehicle: a long drive with tight fuel that crosses the
 * two-hour rest mark, stops for a 15-minute break at an added rest area, and arrives with a rear
 * door unlocked and headlights on. Time runs [speedUp] times faster than real time so the whole
 * story fits in about two and a half minutes.
 */
class DemoDrive(
    private val scope: CoroutineScope,
    private val speedUp: Int = 40,
    private val onDrivingChanged: (Boolean) -> Unit = {},
) {
    private val routeMeters = 112_000
    private val tripSeconds = 75 * 60
    private val restStopSeconds = 15 * 60
    // Yeongdong Expressway west of Munmak, heading for Gangneung Station (real coordinates), so the
    // route passes service areas that have gas stations (Munmak, Hoengseong).
    private val startPoint = GeoPoint(37.33, 127.75)
    private val destinationPoint = GeoPoint(37.7637, 128.8996)
    private val heading = Geo.bearingDegrees(startPoint, destinationPoint)
    private val mutableVehicle = MutableStateFlow(VehicleSnapshot(source = "시연 데이터", car = parkedCar()))
    private val mutableNavigation = MutableStateFlow(
        NavigationSnapshot(status = "INACTIVE", trip = TripSnapshot()),
    )
    val vehicle: StateFlow<VehicleSnapshot> = mutableVehicle.asStateFlow()
    val navigation: StateFlow<NavigationSnapshot> = mutableNavigation.asStateFlow()

    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    /** Route progress (0..1) at which the added rest stop sits, and when the break there ends. */
    private var waypointProgress: Double? = null
    private var restUntilMillis: Long? = null
    private var refuelAtStop = false

    private val clockStart = System.currentTimeMillis()

    /** Demo clock: runs [speedUp] times faster than real time, like the trip itself. */
    fun nowMillis(): Long = clockStart + (System.currentTimeMillis() - clockStart) * speedUp

    fun start() {
        if (isRunning) return
        // Keep whatever the driver set up while parked (climate, lights, doors).
        mutableVehicle.update { snapshot ->
            snapshot.copy(
                car = snapshot.car.copy(
                    tripDrivingSeconds = 98 * 60,
                    rangeRemainingMeters = 135_000,
                    energyPercent = 20,
                    energyType = EnergyType.FUEL,
                ),
            )
        }
        setTrip(
            TripSnapshot(
                tripSeconds,
                routeMeters,
                "강릉역",
                isRouteDriving = true,
                position = startPoint,
                headingDegrees = heading,
                destination = destinationPoint,
            ),
            "ROUTE_DRIVE",
        )
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
        waypointProgress = null
        restUntilMillis = null
        refuelAtStop = false
        mutableVehicle.update { it.copy(car = parkedCar()) }
        setTrip(TripSnapshot(), "INACTIVE")
        onDrivingChanged(false)
    }

    /** Stand-in for NaviHelper.addWaypoint: the car will stop there for a break. */
    fun addWaypoint(area: RestArea) {
        val total = Geo.distanceMeters(startPoint, destinationPoint)
        waypointProgress = (Geo.distanceMeters(startPoint, area.point) / total).coerceIn(0.0, 1.0)
        refuelAtStop = area.canRefuel(mutableVehicle.value.car.energyType)
        mutableNavigation.update { it.copy(trip = it.trip.copy(waypointName = area.name)) }
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
        // Parked at the rest stop: nothing moves until the break is over, then drive on.
        restUntilMillis?.let { until ->
            if (nowMillis() < until) return false
            restUntilMillis = null
            waypointProgress = null
            if (refuelAtStop) {
                // Filled up during the break.
                mutableVehicle.update { it.copy(car = it.car.copy(rangeRemainingMeters = 620_000, energyPercent = 95)) }
                refuelAtStop = false
            }
            setTrip(trip.copy(waypointName = null), "ROUTE_DRIVE")
            onDrivingChanged(true)
            return false
        }
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
            val progress = 1.0 - route.toDouble() / routeMeters
            val position = GeoPoint(
                startPoint.lat + (destinationPoint.lat - startPoint.lat) * progress,
                startPoint.lon + (destinationPoint.lon - startPoint.lon) * progress,
            )
            val atStop = waypointProgress?.let { progress >= it } == true
            setTrip(
                trip.copy(etaSeconds = eta, remainingRouteMeters = route, position = position),
                if (atStop) "WAYPOINT_ARRIVED" else "ROUTE_DRIVE",
            )
            if (atStop) {
                waypointProgress = null
                restUntilMillis = nowMillis() + restStopSeconds * 1000L + 2_000L
                onDrivingChanged(false)
            }
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
        rangeRemainingMeters = 135_000,
        energyPercent = 20,
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
