package io.github.aruru10313.drivecare.data

import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import io.github.aruru10313.drivecare.domain.TripSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NavigationSnapshot(
    val status: String,
    val trip: TripSnapshot = TripSnapshot(),
    val error: String? = null,
) {
    val isRouteDriving: Boolean get() = trip.isRouteDriving
}

interface NavigationStateProvider {
    val state: StateFlow<NavigationSnapshot>
    val isMock: Boolean

    fun connect()
    fun release()
}

class MockNavigationStateProvider(private val demo: DemoDrive) : NavigationStateProvider {
    override val state: StateFlow<NavigationSnapshot> get() = demo.navigation
    override val isMock = true

    override fun connect() = Unit
    override fun release() = Unit
}

/**
 * Follows the head unit's built-in navigation. Uses only listener callbacks and getters verified in
 * NaviHelper 3.0.5 / navi-data 3.0.4.
 *
 * UNVERIFIED: the docs do not state units for DrivingState.duration/distance or
 * DestinationInfo.remainTime/remainDistance. They are treated as seconds and meters.
 */
class PleosNavigationStateProvider(context: Context) : NavigationStateProvider {
    private val helper = NaviHelper(context.applicationContext)
    private val mutableState = MutableStateFlow(NavigationSnapshot(status = "연결 중"))
    override val state: StateFlow<NavigationSnapshot> = mutableState.asStateFlow()
    override val isMock = false

    private var initialized = false
    private var listenerRegistered = false

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            val driving = status == NavigationStatus.ROUTE_DRIVE
            mutableState.update { snapshot ->
                val trip = when {
                    driving -> snapshot.trip.copy(isRouteDriving = true, arrived = false)
                    snapshot.trip.arrived -> snapshot.trip.copy(isRouteDriving = false)
                    else -> TripSnapshot()
                }
                snapshot.copy(status = status.name, trip = trip)
            }
            if (driving) helper.getDestinationInfo()
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val destination = drivingInfo.destination ?: return
            mutableState.update { snapshot ->
                snapshot.copy(
                    trip = snapshot.trip.copy(
                        etaSeconds = destination.duration,
                        remainingRouteMeters = destination.distance,
                        destinationName = destination.name?.takeIf(String::isNotBlank) ?: snapshot.trip.destinationName,
                    ),
                )
            }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            mutableState.update { snapshot ->
                snapshot.copy(
                    trip = snapshot.trip.copy(
                        etaSeconds = snapshot.trip.etaSeconds ?: destinationInfo.remainTime,
                        remainingRouteMeters = snapshot.trip.remainingRouteMeters ?: destinationInfo.remainDistance,
                        destinationName = destinationInfo.name?.takeIf(String::isNotBlank),
                    ),
                )
            }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            mutableState.update { snapshot ->
                snapshot.copy(
                    trip = snapshot.trip.copy(
                        etaSeconds = 0,
                        remainingRouteMeters = 0,
                        isRouteDriving = false,
                        arrived = true,
                    ),
                )
            }
        }
    }

    override fun connect() {
        if (initialized) return
        try {
            helper.initialize(
                onConnected = {
                    helper.addListener(listener)
                    listenerRegistered = true
                    initialized = true
                    helper.getDestinationInfo()
                },
                onFailed = { throwable ->
                    mutableState.update { it.copy(error = throwable.message ?: throwable.javaClass.simpleName) }
                },
            )
        } catch (exception: Exception) {
            mutableState.update { it.copy(error = exception.message ?: exception.javaClass.simpleName) }
        }
    }

    override fun release() {
        if (!initialized) return
        try {
            if (listenerRegistered) helper.removeListener(listener)
            helper.release()
        } catch (exception: Exception) {
            mutableState.update { it.copy(error = exception.message ?: exception.javaClass.simpleName) }
        } finally {
            initialized = false
            listenerRegistered = false
        }
    }
}
