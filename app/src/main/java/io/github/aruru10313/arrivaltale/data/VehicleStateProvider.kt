package io.github.aruru10313.arrivaltale.data

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VehicleSnapshot(
    val source: String,
    val drivingMode: String? = null,
    val brakePedalPressed: Boolean? = null,
    val error: String? = null,
)

interface VehicleStateProvider {
    val state: StateFlow<VehicleSnapshot>

    fun connect()
    fun release()
}

class MockVehicleStateProvider : VehicleStateProvider {
    override val state = MutableStateFlow(
        VehicleSnapshot(
            source = "시연 데이터",
            drivingMode = "NORMAL",
            brakePedalPressed = false,
        ),
    ).asStateFlow()

    override fun connect() = Unit
    override fun release() = Unit
}

class PleosVehicleStateProvider(context: Context) : VehicleStateProvider {
    private val vehicle = Vehicle(context.applicationContext)
    private val mutableState = MutableStateFlow(VehicleSnapshot(source = "Pleos 차량"))
    override val state: StateFlow<VehicleSnapshot> = mutableState.asStateFlow()

    private var initialized = false

    override fun connect() {
        if (initialized) return
        try {
            vehicle.initialize()
            initialized = true
            readDrivingMode()
            readBrakePedalStatus()
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(error = exception.toVehicleError())
        }
    }

    private fun readDrivingMode() {
        vehicle.getDrivingMode().getCurrentDrivingModeState(
            onComplete = { mode: DrivingModeType? ->
                mutableState.value = mutableState.value.copy(drivingMode = mode?.name ?: "알 수 없음")
            },
            onFailed = { exception: Exception ->
                mutableState.value = mutableState.value.copy(error = exception.toVehicleError())
            },
        )
    }

    private fun readBrakePedalStatus() {
        vehicle.getBrake().getCurrentBrakePedalStatus(
            onComplete = { pressed: Boolean? ->
                mutableState.value = mutableState.value.copy(brakePedalPressed = pressed)
            },
            onFailed = { exception: Exception ->
                mutableState.value = mutableState.value.copy(error = exception.toVehicleError())
            },
        )
    }

    override fun release() {
        if (!initialized) return
        try {
            vehicle.release()
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(error = exception.toVehicleError())
        } finally {
            initialized = false
        }
    }

    private fun Exception.toVehicleError(): String =
        message?.takeIf(String::isNotBlank) ?: javaClass.simpleName
}
