package io.github.aruru10313.arrivaltale.data

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DrivingSafetyMode {
    PARKED,
    DRIVING,
}

data class UxRestrictionSnapshot(
    val mode: DrivingSafetyMode,
    val requiresDistractionOptimization: Boolean,
    val activeRestrictions: Int,
    val isAvailable: Boolean,
    val error: String? = null,
)

interface UxRestrictionProvider {
    val state: StateFlow<UxRestrictionSnapshot>
    val isMock: Boolean

    fun connect()
    fun release()
}

class MockUxRestrictionProvider : UxRestrictionProvider {
    private val mutableState = MutableStateFlow(
        UxRestrictionSnapshot(
            mode = DrivingSafetyMode.PARKED,
            requiresDistractionOptimization = false,
            activeRestrictions = 0,
            isAvailable = true,
        ),
    )
    override val state: StateFlow<UxRestrictionSnapshot> = mutableState.asStateFlow()
    override val isMock = true

    override fun connect() = Unit
    override fun release() = Unit

    fun setDriving(isDriving: Boolean) {
        mutableState.value = UxRestrictionSnapshot(
            mode = if (isDriving) DrivingSafetyMode.DRIVING else DrivingSafetyMode.PARKED,
            requiresDistractionOptimization = isDriving,
            activeRestrictions = 0,
            isAvailable = true,
        )
    }
}

class AndroidCarUxRestrictionProvider(context: Context) : UxRestrictionProvider {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(
        UxRestrictionSnapshot(
            mode = DrivingSafetyMode.DRIVING,
            requiresDistractionOptimization = true,
            activeRestrictions = 0,
            isAvailable = false,
            error = "차량 주행 규제 상태를 확인하는 중",
        ),
    )
    override val state: StateFlow<UxRestrictionSnapshot> = mutableState.asStateFlow()
    override val isMock = false

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var registered = false

    private val restrictionListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
        update(restrictions)
    }

    private val lifecycleListener = Car.CarServiceLifecycleListener { connectedCar, ready ->
        if (!ready) {
            unregisterListener()
            manager = null
            mutableState.value = mutableState.value.copy(
                mode = DrivingSafetyMode.DRIVING,
                requiresDistractionOptimization = true,
                activeRestrictions = 0,
                isAvailable = false,
                error = "차량 서비스 연결이 끊어졌습니다",
            )
        } else {
            connectManager(connectedCar)
        }
    }

    override fun connect() {
        if (car != null) return
        try {
            car = Car.createCar(
                appContext,
                Handler(Looper.getMainLooper()),
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                lifecycleListener,
            )
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(error = exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun connectManager(connectedCar: Car) {
        try {
            val restrictionsManager = connectedCar.getCarManager(
                Car.CAR_UX_RESTRICTION_SERVICE,
            ) as? CarUxRestrictionsManager
                ?: error("CarUxRestrictionsManager를 사용할 수 없습니다")
            manager = restrictionsManager
            restrictionsManager.registerListener(restrictionListener)
            registered = true
            update(restrictionsManager.currentCarUxRestrictions)
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(
                mode = DrivingSafetyMode.DRIVING,
                requiresDistractionOptimization = true,
                isAvailable = false,
                error = exception.message ?: exception.javaClass.simpleName,
            )
        }
    }

    private fun update(restrictions: CarUxRestrictions) {
        val requiresDo = restrictions.isRequiresDistractionOptimization
        mutableState.value = UxRestrictionSnapshot(
            mode = if (requiresDo) DrivingSafetyMode.DRIVING else DrivingSafetyMode.PARKED,
            requiresDistractionOptimization = requiresDo,
            activeRestrictions = if (requiresDo) restrictions.activeRestrictions else 0,
            isAvailable = true,
        )
    }

    private fun unregisterListener() {
        if (!registered) return
        try {
            manager?.unregisterListener()
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(
                error = exception.message ?: exception.javaClass.simpleName,
            )
        } finally {
            registered = false
        }
    }

    override fun release() {
        unregisterListener()
        try {
            car?.disconnect()
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(
                error = exception.message ?: exception.javaClass.simpleName,
            )
        } finally {
            car = null
            manager = null
        }
    }
}
