package io.github.aruru10313.drivecare.ui

import androidx.compose.ui.graphics.Color
import io.github.aruru10313.drivecare.domain.Severity

internal val Background = Color(0xFF0B0F14)
internal val Panel = Color(0xFF161C24)
internal val PanelRaised = Color(0xFF1E2630)
internal val TextMain = Color(0xFFF2F5F8)
internal val Muted = Color(0xFF9AA7B4)
internal val Good = Color(0xFF3DDC97)
internal val Caution = Color(0xFFFFB547)
internal val Danger = Color(0xFFFF6B6B)
internal val Accent = Color(0xFF5AB4FF)

internal fun Severity.color() = when (this) {
    Severity.INFO -> Good
    Severity.CAUTION -> Caution
    Severity.WARNING -> Danger
}

/** Body paint choices for the 3D model (generic names, no manufacturer color names). */
internal val PaintOptions = listOf(
    "화이트" to Color(0xFFE6E9EC),
    "그레이" to Color(0xFF6E767E),
    "블루" to Color(0xFF2F5D8A),
)
