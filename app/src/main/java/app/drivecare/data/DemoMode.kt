package app.drivecare.data

import android.content.Context
import android.content.Intent

/**
 * Runtime switch for the scripted demo trip, so an on-site demo on a Pleos head unit needs no
 * rebuild. Off by default: a Pleos car reads its real navigation and vehicle state.
 *
 * Turn it on or off while parked (long-press the app title on the dashboard), or from adb:
 * `adb shell am start -S --user current -n app.drivecare/.MainActivity --ez demo true`
 */
object DemoMode {
    const val EXTRA = "demo"
    private const val PREFS = "drivecare"
    private const val KEY = "demo_mode"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    /**
     * Applies a `--ez demo true|false` launch extra, if there is one, then drops it: recreate()
     * keeps the same intent, so a stale extra would undo a later switch on the dashboard.
     */
    fun applyLaunchExtra(context: Context, intent: Intent?) {
        if (intent?.hasExtra(EXTRA) != true) return
        set(context, intent.getBooleanExtra(EXTRA, false))
        intent.removeExtra(EXTRA)
    }
}
