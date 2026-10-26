package io.github.aruru10313.drivecare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Sensors
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aruru10313.drivecare.data.ParkedSpot
import io.github.aruru10313.drivecare.data.VehicleSnapshot
import io.github.aruru10313.drivecare.domain.CarSnapshot
import io.github.aruru10313.drivecare.domain.DoorPosition
import io.github.aruru10313.drivecare.domain.EnergyType
import io.github.aruru10313.drivecare.domain.Routine
import io.github.aruru10313.drivecare.domain.VehicleAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Parked home screen: the car in 3D, its status at a glance, one-touch routine and controls. */
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
    val visual = CarVisualState(
        unlockedDoors = car.doorLocked.filterValues { !it }.keys,
        headlightsOn = car.headlightsOn == true,
        windowsOpen = car.windowsOpen == true,
    )
    val hero: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(Color(0xFF1F2933), Background))),
            )
            Car3D(
                style = style,
                paint = PaintOptions[paintIndex].second,
                visual = visual,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconChip(Icons.Outlined.DirectionsCar, style.label, onStyleToggle)
                IconChip(Icons.Outlined.Palette, PaintOptions[paintIndex].first, onPaintNext)
            }
        }
    }
    val details: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            RangeSummary(car)
            QuickControls(car, onAction)
            RoutineButton(suggestedRoutine, routineMessage ?: vehicleState.controlMessage, onRoutine)
            ClimateRow(car, onAction)
            StatusRow(car)
            parkedSpot?.let { ParkingRow(it) }
        }
    }

    Column(modifier.fillMaxSize()) {
        Header(car, style, isDemo, onVoice)
        if (isLandscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                hero(Modifier.weight(1.2f).fillMaxHeight())
                details(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                hero(Modifier.fillMaxWidth().height(290.dp))
                details(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp))
            }
        }
    }
}

@Composable
private fun Header(car: CarSnapshot, style: BodyStyle, isDemo: Boolean, onVoice: () -> Unit) {
    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("내 ${style.label}", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                if (isDemo) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "DEMO",
                        color = Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .border(1.dp, Muted.copy(alpha = .5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                listOfNotNull(
                    "주차됨",
                    car.odometerMeters?.let { "%,d km".format(Locale.ROOT, it / 1000) },
                ).joinToString("  ·  "),
                color = Muted,
                fontSize = 14.sp,
            )
        }
        RoundIconButton(Icons.Outlined.Mic, "음성 명령", onVoice)
    }
}

@Composable
private fun RangeSummary(car: CarSnapshot) {
    val percent = car.energyPercent
    val low = (percent ?: 100) <= 15
    val barColor = if (low) Caution else Good
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                car.rangeRemainingMeters?.let { (it / 1000).toString() } ?: "--",
                color = TextMain,
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
            )
            Text(" km", color = Muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(160.dp).height(6.dp).clip(RoundedCornerShape(50)).background(PanelRaised)) {
                Box(
                    Modifier
                        .fillMaxWidth(((percent ?: 0) / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(barColor),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "${if (car.energyType == EnergyType.EV) "배터리" else "연료"} ${percent?.let { "$it%" } ?: "--"}",
                color = if (low) Caution else Muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun QuickControls(car: CarSnapshot, onAction: (VehicleAction) -> Unit) {
    val allLocked = car.doorLocked.isNotEmpty() && car.doorLocked.values.all { it }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        QuickControl(
            icon = if (allLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
            label = if (allLocked) "잠김" else "열림",
            active = allLocked,
            warn = !allLocked && car.doorLocked.isNotEmpty(),
        ) { onAction(VehicleAction.LockDoors(!allLocked)) }
        QuickControl(Icons.Outlined.AcUnit, "공조", active = car.climateOn == true) {
            onAction(VehicleAction.Climate(on = car.climateOn != true, targetC = car.targetTempC ?: 22f))
        }
        QuickControl(Icons.Outlined.Lightbulb, "전조등", active = car.headlightsOn == true) {
            onAction(VehicleAction.Headlights(car.headlightsOn != true))
        }
        QuickControl(
            Icons.Outlined.Window,
            if (car.windowsOpen == true) "창문 열림" else "창문",
            active = car.windowsOpen == false,
            warn = car.windowsOpen == true,
        ) { onAction(VehicleAction.CloseWindows) }
    }
}

@Composable
private fun QuickControl(
    icon: ImageVector,
    label: String,
    active: Boolean,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        warn -> Caution
        active -> Background
        else -> TextMain
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (active) Accent else PanelRaised)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (warn) Caution else Muted, fontSize = 12.sp)
    }
}

@Composable
private fun RoutineButton(routine: Routine, message: String?, onClick: () -> Unit) {
    val color = if (routine == Routine.DEPARTURE) Good else Caution
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = .22f), Panel)))
                .border(1.dp, color.copy(alpha = .6f), RoundedCornerShape(32.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, tint = Background, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(routine.label, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (routine == Routine.DEPARTURE) "공조 · 창문 · 잠금 · 전조등" else "창문 · 전조등 · 공조 정리",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
        message?.let {
            Text(
                it,
                color = TextMain,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun ClimateRow(car: CarSnapshot, onAction: (VehicleAction) -> Unit) {
    val target = car.targetTempC ?: 22f
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("실내 온도", color = Muted, fontSize = 13.sp)
            Text(
                car.cabinTempC?.let { "%.1f°".format(Locale.ROOT, it) } ?: "--",
                color = TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        RoundIconButton(Icons.Outlined.Remove, "온도 내리기") {
            onAction(VehicleAction.Climate(on = true, targetC = (target - 0.5f).coerceAtLeast(16f)))
        }
        Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "%.1f°".format(Locale.ROOT, target),
                color = if (car.climateOn == true) Accent else TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(if (car.climateOn == true) "설정" else "꺼짐", color = Muted, fontSize = 11.sp)
        }
        RoundIconButton(Icons.Outlined.Add, "온도 올리기") {
            onAction(VehicleAction.Climate(on = true, targetC = (target + 0.5f).coerceAtMost(30f)))
        }
    }
}

@Composable
private fun StatusRow(car: CarSnapshot) {
    val unlocked = DoorPosition.entries.filter { car.doorLocked[it] == false }
    val items = listOf(
        "문" to when {
            car.doorLocked.isEmpty() -> "--"
            unlocked.isEmpty() -> "모두 잠김"
            else -> unlocked.joinToString(", ") { it.label }
        },
        "전조등" to when (car.headlightsOn) {
            true -> "켜짐"
            false -> "꺼짐"
            null -> "--"
        },
        "창문" to when (car.windowsOpen) {
            true -> "열림"
            false -> "닫힘"
            null -> "--"
        },
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Sensors, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("차량 상태", color = Muted, fontSize = 13.sp)
        }
        items.forEach { (label, value) ->
            val bad = value == "열림" || (label == "문" && unlocked.isNotEmpty())
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(label, color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(
                    value,
                    color = if (bad) Caution else Muted,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun ParkingRow(spot: ParkedSpot) {
    val time = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(Date(spot.timeMillis))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.LocalParking, contentDescription = null, tint = Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(spot.placeName ?: "마지막 주차 위치", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "$time  ·  %.4f, %.4f".format(Locale.ROOT, spot.latitude, spot.longitude),
                color = Muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(PanelRaised).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = TextMain, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun IconChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(PanelRaised.copy(alpha = .8f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextMain, fontSize = 12.sp)
    }
}

