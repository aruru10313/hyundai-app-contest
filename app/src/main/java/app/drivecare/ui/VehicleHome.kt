package app.drivecare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drivecare.data.ParkedSpot
import app.drivecare.data.VehicleSnapshot
import app.drivecare.domain.CarSnapshot
import app.drivecare.domain.DoorPosition
import app.drivecare.domain.EnergyType
import app.drivecare.domain.Routine
import app.drivecare.domain.VehicleAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parked home screen laid out for a landscape head unit: the car fills the left, controls sit on
 * the right within thumb reach of the driver. Falls back to a single column on portrait screens.
 */
@Composable
fun VehicleHome(
    vehicleState: VehicleSnapshot,
    style: BodyStyle,
    paintIndex: Int,
    onStyleToggle: () -> Unit,
    onPaintNext: () -> Unit,
    suggestedRoutine: Routine,
    routineMessage: String?,
    onRoutine: () -> Unit,
    onAction: (VehicleAction) -> Unit,
    onVoice: () -> Unit,
    parkedSpot: ParkedSpot?,
    isDemo: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val car = vehicleState.car
    val stage: @Composable (Modifier) -> Unit = { m ->
        CarStage(car, style, paintIndex, isDemo, onStyleToggle, onPaintNext, m)
    }
    val controls: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("차량 제어", color = Muted, style = PleosType.labelMedium, modifier = Modifier.weight(1f))
                RoundIconButton(Icons.Outlined.Mic, "음성 명령", onClick = onVoice)
            }
            SwitchGrid(car, onAction)
            ClimateRow(car, onAction)
            RoutineButton(suggestedRoutine, routineMessage ?: vehicleState.controlMessage, onRoutine)
            parkedSpot?.let { ParkingRow(it) }
        }
    }

    if (isLandscape) {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            stage(Modifier.weight(1.3f).fillMaxHeight())
            controls(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
        }
    } else {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            stage(Modifier.fillMaxWidth().aspectRatio(0.95f))
            controls(Modifier.fillMaxWidth())
        }
    }
}

/** The car on a softly lit floor, with name, status and remaining range laid over it. */
@Composable
private fun CarStage(
    car: CarSnapshot,
    style: BodyStyle,
    paintIndex: Int,
    isDemo: Boolean,
    onStyleToggle: () -> Unit,
    onPaintNext: () -> Unit,
    modifier: Modifier,
) {
    val visual = CarVisualState(
        unlockedDoors = car.doorLocked.filterValues { !it }.keys,
        headlightsOn = car.headlightsOn == true,
        windowsOpen = car.windowsOpen == true,
    )
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Panel, Background))),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(PanelRaised.copy(alpha = .9f), Color.Transparent))),
        )
        CarView(
            style = style,
            paintIndex = paintIndex,
            visual = visual,
            modifier = Modifier.fillMaxSize().padding(top = 56.dp, bottom = 72.dp),
        )
        Column(Modifier.align(Alignment.TopStart).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("내 ${style.label}", color = TextMain, style = PleosType.titleLarge)
                if (isDemo) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "DEMO",
                        color = Muted,
                        style = PleosType.labelSmall,
                        modifier = Modifier
                            .border(1.dp, Faint, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                listOfNotNull(
                    "주차됨",
                    car.odometerMeters?.let { "%,d km".format(Locale.ROOT, it / 1000) },
                    car.cabinTempC?.let { "실내 %.1f°".format(Locale.ROOT, it) },
                ).joinToString("  ·  "),
                color = Muted,
                style = PleosType.bodySmall,
            )
            StatusBadges(car)
        }
        RangeReadout(car, Modifier.align(Alignment.BottomStart).padding(24.dp))
        Row(
            Modifier.align(Alignment.TopEnd).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconChip(Icons.Outlined.DirectionsCar, style.label, onStyleToggle)
            IconChip(Icons.Outlined.Palette, PaintOptions[paintIndex].first, onPaintNext)
        }
    }
}

/** Only problems get a badge; a calm car shows nothing extra. */
@Composable
private fun StatusBadges(car: CarSnapshot) {
    val unlocked = DoorPosition.entries.filter { car.doorLocked[it] == false }
    val badges = buildList {
        // This is the lock state, not door position, so say "잠금 해제" rather than "열림".
        if (unlocked.size == DoorPosition.entries.size) add("모든 문 잠금 해제" to Danger)
        else if (unlocked.isNotEmpty()) add(unlocked.joinToString(", ") { it.label } + " 문 잠금 해제" to Danger)
        if (car.windowsOpen == true) add("창문 열림" to Caution)
        if (car.headlightsOn == true) add("전조등 켜짐" to Muted)
    }
    if (badges.isEmpty()) return
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.forEach { (text, color) ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = .16f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(text, color = TextMain, style = PleosType.labelSmall)
            }
        }
    }
}

@Composable
private fun RangeReadout(car: CarSnapshot, modifier: Modifier) {
    val percent = car.energyPercent
    val low = (percent ?: 100) <= 15
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                car.rangeRemainingMeters?.let { (it / 1000).toString() } ?: "--",
                color = TextMain,
                style = PleosType.headlineLarge,
            )
            Text(" km", color = Muted, style = PleosType.bodyLarge, modifier = Modifier.padding(bottom = 10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.LocalGasStation,
                contentDescription = null,
                tint = if (low) Caution else Muted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(140.dp).height(6.dp).clip(RoundedCornerShape(50)).background(PanelAccent)) {
                Box(
                    Modifier
                        .fillMaxWidth(((percent ?: 0) / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (low) Caution else Good),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "${if (car.energyType == EnergyType.EV) "배터리" else "연료"} ${percent?.let { "$it%" } ?: "--"}",
                color = if (low) Caution else Muted,
                style = PleosType.labelMedium,
            )
        }
    }
}

/** Pleos switch buttons: off is a dark surface, on turns light with dark content. */
@Composable
private fun SwitchGrid(car: CarSnapshot, onAction: (VehicleAction) -> Unit) {
    val allLocked = car.doorLocked.isNotEmpty() && car.doorLocked.values.all { it }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchTile(
                icon = if (allLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                title = "도어",
                state = when {
                    car.doorLocked.isEmpty() -> "확인 불가"
                    allLocked -> "잠김"
                    else -> "잠금 해제"
                },
                on = allLocked,
                warn = !allLocked && car.doorLocked.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { onAction(VehicleAction.LockDoors(!allLocked)) }
            SwitchTile(
                icon = Icons.Outlined.AcUnit,
                title = "공조",
                state = if (car.climateOn == true) "켜짐" else "꺼짐",
                on = car.climateOn == true,
                modifier = Modifier.weight(1f),
            ) { onAction(VehicleAction.Climate(on = car.climateOn != true, targetC = car.targetTempC ?: 22f)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchTile(
                icon = Icons.Outlined.Lightbulb,
                title = "전조등",
                state = if (car.headlightsOn == true) "켜짐" else "꺼짐",
                on = car.headlightsOn == true,
                modifier = Modifier.weight(1f),
            ) { onAction(VehicleAction.Headlights(car.headlightsOn != true)) }
            SwitchTile(
                icon = Icons.Outlined.Window,
                title = "창문",
                state = when (car.windowsOpen) {
                    true -> "열림 · 닫기"
                    false -> "닫힘"
                    null -> "--"
                },
                on = car.windowsOpen == false,
                warn = car.windowsOpen == true,
                modifier = Modifier.weight(1f),
            ) { onAction(VehicleAction.CloseWindows) }
        }
    }
}

@Composable
private fun SwitchTile(
    icon: ImageVector,
    title: String,
    state: String,
    on: Boolean,
    modifier: Modifier,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    val content = if (on) SwitchOnContent else TextMain
    Row(
        modifier
            .height(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) SwitchOn else PanelRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (warn) Caution else content, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = content, style = PleosType.bodySmall, maxLines = 1)
            Text(
                state,
                color = when {
                    warn -> Caution
                    on -> SwitchOnContent.copy(alpha = .7f)
                    else -> Muted
                },
                style = PleosType.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ClimateRow(car: CarSnapshot, onAction: (VehicleAction) -> Unit) {
    val target = car.targetTempC ?: 22f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PanelRaised)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("설정 온도", color = Muted, style = PleosType.labelSmall)
            Text(
                "%.1f°".format(Locale.ROOT, target),
                color = if (car.climateOn == true) Accent else TextMain,
                style = PleosType.titleMedium,
            )
        }
        RoundIconButton(Icons.Outlined.Remove, "온도 내리기", size = 56.dp) {
            onAction(VehicleAction.Climate(on = true, targetC = (target - 0.5f).coerceAtLeast(16f)))
        }
        Spacer(Modifier.width(12.dp))
        RoundIconButton(Icons.Outlined.Add, "온도 올리기", size = 56.dp) {
            onAction(VehicleAction.Climate(on = true, targetC = (target + 0.5f).coerceAtMost(30f)))
        }
    }
}

@Composable
private fun RoutineButton(routine: Routine, message: String?, onClick: () -> Unit) {
    val color = if (routine == Routine.DEPARTURE) Good else Caution
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(44.dp))
                .background(Panel)
                .border(2.dp, color.copy(alpha = .7f), RoundedCornerShape(44.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, tint = Background, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(routine.label, color = TextMain, style = PleosType.bodyLarge)
                Text(
                    if (routine == Routine.DEPARTURE) "공조 · 창문 · 잠금 · 전조등" else "창문 · 전조등 · 공조 정리",
                    color = Muted,
                    style = PleosType.labelSmall,
                )
            }
        }
        message?.let {
            Text(
                it,
                color = TextMain,
                style = PleosType.labelMedium,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp),
            )
        }
    }
}

@Composable
private fun ParkingRow(spot: ParkedSpot) {
    val time = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(Date(spot.timeMillis))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocalParking, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(spot.placeName ?: "마지막 주차 위치", color = TextMain, style = PleosType.bodySmall)
            Text(
                "$time  ·  %.4f, %.4f".format(Locale.ROOT, spot.latitude, spot.longitude),
                color = Muted,
                style = PleosType.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun RoundIconButton(icon: ImageVector, description: String, size: Dp = 52.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(PanelAccent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = TextMain, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
private fun IconChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .pleosShadowLevel1(RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(PanelRaised.copy(alpha = .9f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextMain, style = PleosType.labelSmall)
    }
}
