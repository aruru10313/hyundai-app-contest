package io.github.aruru10313.arrivaltale.domain

class TripAutoStartGate {
    private var wasRouteDriving = false

    fun onRouteDrivingChanged(isRouteDriving: Boolean): Boolean {
        val shouldStart = isRouteDriving && !wasRouteDriving
        wasRouteDriving = isRouteDriving
        return shouldStart
    }
}
