package io.github.aruru10313.arrivaltale.data

import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NavigationSnapshot(
    val status: String,
    val isRouteDriving: Boolean,
    val error: String? = null,
)

interface NavigationStateProvider {
    val state: StateFlow<NavigationSnapshot>
    val isMock: Boolean

    fun connect()
    fun release()
}

class MockNavigationStateProvider : NavigationStateProvider {
    private val mutableState = MutableStateFlow(
        NavigationSnapshot(status = "INACTIVE", isRouteDriving = false),
    )
    override val state: StateFlow<NavigationSnapshot> = mutableState.asStateFlow()
    override val isMock = true

    override fun connect() = Unit
    override fun release() = Unit

    fun setRouteDriving(isDriving: Boolean) {
        mutableState.value = NavigationSnapshot(
            status = if (isDriving) NavigationStatus.ROUTE_DRIVE.name else NavigationStatus.INACTIVE.name,
            isRouteDriving = isDriving,
        )
    }
}

class PleosNavigationStateProvider(context: Context) : NavigationStateProvider {
    private val helper = NaviHelper(context.applicationContext)
    private val mutableState = MutableStateFlow(
        NavigationSnapshot(status = "연결 중", isRouteDriving = false),
    )
    override val state: StateFlow<NavigationSnapshot> = mutableState.asStateFlow()
    override val isMock = false

    private var initialized = false
    private var listenerRegistered = false

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            mutableState.value = NavigationSnapshot(
                status = status.name,
                isRouteDriving = status == NavigationStatus.ROUTE_DRIVE,
            )
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
                },
                onFailed = { throwable ->
                    mutableState.value = mutableState.value.copy(
                        error = throwable.message ?: throwable.javaClass.simpleName,
                    )
                },
            )
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(
                error = exception.message ?: exception.javaClass.simpleName,
            )
        }
    }

    override fun release() {
        if (!initialized) return
        try {
            if (listenerRegistered) helper.removeListener(listener)
            helper.release()
        } catch (exception: Exception) {
            mutableState.value = mutableState.value.copy(
                error = exception.message ?: exception.javaClass.simpleName,
            )
        } finally {
            initialized = false
            listenerRegistered = false
        }
    }
}
