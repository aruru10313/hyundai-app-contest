package io.github.aruru10313.drivecare.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.aruru10313.drivecare.domain.Severity

/*
 * Pleos Connect UI Design Guide colors (document.pleos.ai .../docs-design/foundations/colors).
 * The system UI is near-monochrome; color is reserved for status (informative_*), so the app follows
 * the same rule to sit naturally next to the built-in apps.
 */
/** One theme's worth of Pleos tokens. Light values from the same guide pages as dark. */
internal class PleosPalette(
    val background: Color,
    val panel: Color,
    val panelRaised: Color,
    val panelAccent: Color,
    val switchOn: Color,
    val switchOnContent: Color,
    val textMain: Color,
    val muted: Color,
    val faint: Color,
    val good: Color,
    val accent: Color,
    val danger: Color,
    val caution: Color,
)

private val DarkPalette = PleosPalette(
    background = Color(0xFF131417), // background_secondary = basic_00
    panel = Color(0xFF1D1E21), // surface_basic = basic_50
    panelRaised = Color(0xFF313236), // surface_high = basic_100
    panelAccent = Color(0xFF44464E), // surface_accent = basic_200
    switchOn = Color(0xFFE0E1E6), // button_switch_enabled: a selected switch turns light
    switchOnContent = Color(0xFF131417),
    textMain = Color(0xFFFFFFFF), // text_primary
    muted = Color(0xFFA4A8AE), // basic_400
    faint = Color(0xFF686A72), // basic_300
    good = Color(0xFF00E074), // informative_active
    accent = Color(0xFF0082FF), // informative_positive
    danger = Color(0xFFFF4C28), // informative_negative
    caution = Color(0xFFFFB547), // no amber token in Pleos; kept distinct from negative
)

private val LightPalette = PleosPalette(
    background = Color(0xFFF7F8FA), // surface_low = basic_50
    panel = Color(0xFFFFFFFF), // surface_basic = basic_00
    panelRaised = Color(0xFFEDEEF2), // surface_high = basic_100
    panelAccent = Color(0xFFE0E1E6), // surface_accent = basic_200
    switchOn = Color(0xFF313236), // a selected switch inverts to dark in light mode
    switchOnContent = Color(0xFFFFFFFF),
    textMain = Color(0xFF131417),
    muted = Color(0xFF686A72),
    faint = Color(0xFFA4A8AE),
    good = Color(0xFF02C265),
    accent = Color(0xFF0064FF),
    danger = Color(0xFFFE3D16),
    caution = Color(0xFFE59400),
)

/**
 * The active palette. Set once at the root from the system theme (a theme change recreates the
 * activity), so leaf composables can keep reading plain values.
 */
internal object Pleos {
    var dark = true
    val palette get() = if (dark) DarkPalette else LightPalette
}

internal val Background get() = Pleos.palette.background
internal val Panel get() = Pleos.palette.panel
internal val PanelRaised get() = Pleos.palette.panelRaised
internal val PanelAccent get() = Pleos.palette.panelAccent
internal val SwitchOn get() = Pleos.palette.switchOn
internal val SwitchOnContent get() = Pleos.palette.switchOnContent
internal val TextMain get() = Pleos.palette.textMain
internal val Muted get() = Pleos.palette.muted
internal val Faint get() = Pleos.palette.faint
internal val Good get() = Pleos.palette.good
internal val Accent get() = Pleos.palette.accent
internal val Danger get() = Pleos.palette.danger
internal val Caution get() = Pleos.palette.caution

internal fun Severity.color() = when (this) {
    Severity.INFO -> Good
    Severity.CAUTION -> Caution
    Severity.WARNING -> Danger
}

/**
 * Pleos font hierarchy (Headline / Title / Body / Label). The guide gives head-unit pixel sizes;
 * [scale] maps them to sp for the running display (1.0 on the Pleos Connect emulator).
 */
internal object PleosType {
    var scale = 1f

    private fun style(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontSize = (size * scale).sp, lineHeight = (line * scale).sp, fontWeight = weight)

    val headlineLarge get() = style(60, 80, FontWeight.ExtraBold)
    val headlineSmall get() = style(48, 64, FontWeight.Bold)
    val titleLarge get() = style(40, 52, FontWeight.Bold)
    val titleMedium get() = style(36, 44, FontWeight.Bold)
    val titleSmall get() = style(32, 40, FontWeight.Bold)
    val bodyLarge get() = style(30, 38, FontWeight.Normal)
    val bodyMedium get() = style(28, 36, FontWeight.Normal)
    val bodySmall get() = style(26, 34, FontWeight.Normal)
    val labelMedium get() = style(24, 28, FontWeight.Normal)
    val labelSmall get() = style(20, 24, FontWeight.Normal)
}

/** Body paint choices for the 3D model (generic names, no manufacturer color names). */
internal val PaintOptions = listOf(
    "화이트" to Color(0xFFE6E9EC),
    "그레이" to Color(0xFF6E767E),
    "블루" to Color(0xFF2F5D8A),
)
