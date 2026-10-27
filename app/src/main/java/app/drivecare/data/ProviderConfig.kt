package app.drivecare.data

object ProviderConfig {
    const val usePleosVehicleSdk = true
    const val usePleosUxRestrictions = true
    const val usePleosNavigationSdk = true

    /** Forces the scripted demo trip even on a Pleos head unit, e.g. for judging without a route. */
    const val forceDemoDrive = false
}
