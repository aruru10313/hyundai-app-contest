package io.github.aruru10313.arrivaltale.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface EtaProvider {
    /** Remaining travel time in seconds. */
    fun remainingSeconds(): Flow<Int>
}

class MockEtaProvider(
    private val initialSeconds: Int = 10 * 60,
    private val tickSeconds: Int = 1,
    private val tickMillis: Long = 1_000L,
) : EtaProvider {
    init {
        require(initialSeconds >= 0)
        require(tickSeconds > 0)
        require(tickMillis > 0)
    }

    override fun remainingSeconds(): Flow<Int> = flow {
        var remaining = initialSeconds
        emit(remaining)
        while (remaining > 0) {
            delay(tickMillis)
            remaining = (remaining - tickSeconds).coerceAtLeast(0)
            emit(remaining)
        }
    }
}

/**
 * UNVERIFIED: NaviHelper ETA time units are not documented. Do not convert its values to seconds
 * until the unit is confirmed; the current demo deliberately uses MockEtaProvider.
 */
