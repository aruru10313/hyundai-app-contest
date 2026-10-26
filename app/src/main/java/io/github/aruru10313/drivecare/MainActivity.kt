package io.github.aruru10313.drivecare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.aruru10313.drivecare.data.AndroidCarUxRestrictionProvider
import io.github.aruru10313.drivecare.data.AndroidTtsProvider
import io.github.aruru10313.drivecare.data.DemoDrive
import io.github.aruru10313.drivecare.data.DemoParkingLocationProvider
import io.github.aruru10313.drivecare.data.FallbackTtsProvider
import io.github.aruru10313.drivecare.data.ParkingLocationProvider
import io.github.aruru10313.drivecare.data.PleosParkingLocationProvider
import io.github.aruru10313.drivecare.data.PleosTtsProvider
import io.github.aruru10313.drivecare.data.PleosVoiceInputProvider
import io.github.aruru10313.drivecare.data.VoiceInputProvider
import io.github.aruru10313.drivecare.data.VoiceInputState
import io.github.aruru10313.drivecare.domain.VehicleAction
import io.github.aruru10313.drivecare.domain.VoiceCommand
import io.github.aruru10313.drivecare.domain.VoiceCommandParser
import io.github.aruru10313.drivecare.ui.Accent
import io.github.aruru10313.drivecare.data.DrivingSafetyMode
import io.github.aruru10313.drivecare.data.MockNavigationStateProvider
import io.github.aruru10313.drivecare.data.MockUxRestrictionProvider
import io.github.aruru10313.drivecare.data.MockVehicleStateProvider
import io.github.aruru10313.drivecare.data.NavigationStateProvider
import io.github.aruru10313.drivecare.data.PleosNavigationStateProvider
import io.github.aruru10313.drivecare.data.PleosVehicleStateProvider
import io.github.aruru10313.drivecare.data.ProviderConfig
import io.github.aruru10313.drivecare.data.TtsProvider
import io.github.aruru10313.drivecare.data.UxRestrictionProvider
import io.github.aruru10313.drivecare.data.VehicleSnapshot
import io.github.aruru10313.drivecare.data.VehicleStateProvider
import io.github.aruru10313.drivecare.domain.AnnouncementGate
import io.github.aruru10313.drivecare.domain.CareAlert
import io.github.aruru10313.drivecare.domain.DoorPosition
import io.github.aruru10313.drivecare.domain.DriveCareAdvisor
import io.github.aruru10313.drivecare.domain.DriveCareReport
import io.github.aruru10313.drivecare.domain.EnergyType
import io.github.aruru10313.drivecare.domain.RangeAdvice
import io.github.aruru10313.drivecare.domain.RestAdvice
import io.github.aruru10313.drivecare.domain.Severity
import io.github.aruru10313.drivecare.domain.TripAutoStartGate
import io.github.aruru10313.drivecare.domain.TripSnapshot
import io.github.aruru10313.drivecare.domain.briefing
import io.github.aruru10313.drivecare.domain.formatDuration
import io.github.aruru10313.drivecare.domain.formatKm
import io.github.aruru10313.drivecare.domain.RoutinePlanner
import io.github.aruru10313.drivecare.ui.Background
import io.github.aruru10313.drivecare.ui.BodyStyle
import io.github.aruru10313.drivecare.ui.Caution
import io.github.aruru10313.drivecare.ui.Danger
import io.github.aruru10313.drivecare.ui.Good
import io.github.aruru10313.drivecare.ui.Muted
import io.github.aruru10313.drivecare.ui.PaintOptions
import io.github.aruru10313.drivecare.ui.Pleos
import io.github.aruru10313.drivecare.ui.PleosType
import io.github.aruru10313.drivecare.ui.Panel
import io.github.aruru10313.drivecare.ui.PanelRaised
import io.github.aruru10313.drivecare.ui.TextMain
import io.github.aruru10313.drivecare.ui.VehicleHome
import io.github.aruru10313.drivecare.ui.color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {
    private lateinit var ttsProvider: FallbackTtsProvider
    private lateinit var voiceInputProvider: VoiceInputProvider
    private lateinit var parkingLocationProvider: ParkingLocationProvider
    private lateinit var vehicleStateProvider: VehicleStateProvider
    private lateinit var uxRestrictionProvider: UxRestrictionProvider
    private lateinit var navigationStateProvider: NavigationStateProvider
    private var demoDrive: DemoDrive? = null
    private var isAutomotive = false
    private val carDrivingStatePermission = "android.car.permission.CAR_DRIVING_STATE_3P"

    private var pendingVoiceStart: (() -> Unit)? = null
    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingVoiceStart?.invoke()
            pendingVoiceStart = null
        }

    /** Pleos STT needs no runtime grant, but the Android fallback recognizer needs the microphone. */
    private fun withMicPermission(start: () -> Unit) {
        if (isAutomotive || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            start()
        } else {
            pendingVoiceStart = start
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private val drivingStatePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted || isAutomotive) uxRestrictionProvider.connect()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isAutomotive = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val useDemo = !isAutomotive || ProviderConfig.forceDemoDrive
        // Pleos voice services only exist on a Pleos head unit; elsewhere use Android's own.
        ttsProvider = FallbackTtsProvider(
            primary = if (isAutomotive) PleosTtsProvider(applicationContext) else null,
            fallback = AndroidTtsProvider(applicationContext),
        )
        voiceInputProvider = PleosVoiceInputProvider(applicationContext)
        parkingLocationProvider = if (useDemo) {
            DemoParkingLocationProvider()
        } else {
            PleosParkingLocationProvider(applicationContext)
        }

        uxRestrictionProvider = if (!useDemo && ProviderConfig.usePleosUxRestrictions) {
            AndroidCarUxRestrictionProvider(applicationContext)
        } else {
            MockUxRestrictionProvider()
        }
        val demo = if (useDemo) {
            DemoDrive(lifecycleScope) { driving ->
                (uxRestrictionProvider as? MockUxRestrictionProvider)?.setDriving(driving)
            }
        } else {
            null
        }
        demoDrive = demo

        vehicleStateProvider = if (demo == null && ProviderConfig.usePleosVehicleSdk) {
            PleosVehicleStateProvider(applicationContext)
        } else {
            MockVehicleStateProvider(demo ?: DemoDrive(lifecycleScope))
        }
        navigationStateProvider = if (demo == null && ProviderConfig.usePleosNavigationSdk) {
            PleosNavigationStateProvider(applicationContext)
        } else {
            MockNavigationStateProvider(demo ?: DemoDrive(lifecycleScope))
        }
        vehicleStateProvider.connect()
        navigationStateProvider.connect()
        // Some head units (e.g. the Pleos Connect image) do not define this permission at all;
        // asking for it then just returns "denied", so only ask where it exists.
        val permissionDefined = runCatching { packageManager.getPermissionInfo(carDrivingStatePermission, 0) }.isSuccess
        if (
            isAutomotive && permissionDefined &&
            checkSelfPermission(carDrivingStatePermission) != PackageManager.PERMISSION_GRANTED
        ) {
            drivingStatePermissionLauncher.launch(carDrivingStatePermission)
        } else {
            uxRestrictionProvider.connect()
        }

        setContent {
            Pleos.dark = isSystemInDarkTheme()
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                    DriveCareScreen(
                        ttsProvider = ttsProvider,
                        vehicleStateProvider = vehicleStateProvider,
                        uxRestrictionProvider = uxRestrictionProvider,
                        navigationStateProvider = navigationStateProvider,
                        demoDrive = demo,
                        voiceInputProvider = voiceInputProvider,
                        parkingLocationProvider = parkingLocationProvider,
                        requestMic = ::withMicPermission,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        demoDrive?.stop()
        navigationStateProvider.release()
        uxRestrictionProvider.release()
        vehicleStateProvider.release()
        ttsProvider.release()
        voiceInputProvider.release()
        parkingLocationProvider.release()
        super.onDestroy()
    }
}

@Composable
private fun DriveCareScreen(
    ttsProvider: TtsProvider,
    vehicleStateProvider: VehicleStateProvider,
    uxRestrictionProvider: UxRestrictionProvider,
    navigationStateProvider: NavigationStateProvider,
    demoDrive: DemoDrive?,
    voiceInputProvider: VoiceInputProvider,
    parkingLocationProvider: ParkingLocationProvider,
    requestMic: (() -> Unit) -> Unit,
) {
    val vehicleState by vehicleStateProvider.state.collectAsState()
    val voiceState by voiceInputProvider.state.collectAsState()
    val parkedSpot by parkingLocationProvider.lastSpot.collectAsState()
    val uxState by uxRestrictionProvider.state.collectAsState()
    val navigationState by navigationStateProvider.state.collectAsState()
    val isParked = uxState.mode == DrivingSafetyMode.PARKED
    val trip = navigationState.trip
    val car = vehicleState.car

    val advisor = remember { DriveCareAdvisor() }
    val report = advisor.evaluate(trip, car, isParked)
    val announcementGate = remember { AnnouncementGate() }
    val tripStartGate = remember { TripAutoStartGate() }
    val scope = rememberCoroutineScope()
    val speechLock = remember { Mutex() }
    val routinePlanner = remember { RoutinePlanner() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var styleIndex by rememberSaveable { mutableIntStateOf(BodyStyle.SUV.ordinal) }
    var paintIndex by rememberSaveable { mutableIntStateOf(0) }
    var routineMessage by remember { mutableStateOf<String?>(null) }

    fun say(text: String) {
        scope.launch {
            speechLock.withLock {
                try {
                    ttsProvider.speak(text)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (ignored: IllegalStateException) {
                    // No voice available: the screen still shows every alert.
                }
            }
        }
    }

    // A briefing when route guidance starts, then each new or escalated alert exactly once.
    LaunchedEffect(trip.isRouteDriving) {
        if (tripStartGate.onRouteDrivingChanged(trip.isRouteDriving)) {
            say(briefing(trip, car, report))
        }
    }
    LaunchedEffect(trip.arrived, trip.isRouteDriving) { routineMessage = null }
    // Remember where the car stopped: on arrival, or when a drive ends without route guidance.
    var wasDriving by remember { mutableStateOf(false) }
    LaunchedEffect(isParked, trip.arrived) {
        if (isParked && (wasDriving || trip.arrived)) parkingLocationProvider.recordParking(trip.destinationName)
        wasDriving = !isParked
    }

    fun runRoutine() {
        val plan = routinePlanner.plan(routinePlanner.suggested(trip), car)
        plan.actions.forEach(vehicleStateProvider::perform)
        routineMessage = plan.summary
        say(plan.summary)
    }

    fun handleVoice(spoken: String) {
        when (val command = VoiceCommandParser.parse(spoken)) {
            is VoiceCommand.RunRoutine -> runRoutine()
            VoiceCommand.Briefing -> say(briefing(trip, car, report))
            is VoiceCommand.Control -> {
                // Never unlock doors while the car is moving.
                if (!isParked && command.action == VehicleAction.LockDoors(false)) {
                    say("주행 중에는 문을 열 수 없어요.")
                } else {
                    vehicleStateProvider.perform(command.action)
                    say(VoiceCommandParser.confirmation(command.action))
                }
            }
            VoiceCommand.Unknown -> say("다시 말씀해 주세요. 예를 들어 에어컨 켜 줘, 출발 준비.")
        }
    }

    fun startVoice() {
        ttsProvider.stop()
        requestMic { voiceInputProvider.listen(::handleVoice) }
    }
    LaunchedEffect(report.alerts) {
        announcementGate.toAnnounce(report.alerts).forEach { say(it.spoken) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        val isLandscape = maxWidth > maxHeight
        // Pleos type sizes are specified for the 2560x1440 mdpi head unit; shrink them on small screens.
        PleosType.scale = (minOf(maxWidth.value, maxHeight.value) / 800f).coerceIn(0.55f, 1f)
        if (isParked) {
            val content: @Composable (Modifier) -> Unit = { m ->
                Box(m) {
                    if (tab == 0) {
                        VehicleHome(
                            vehicleState = vehicleState,
                            style = BodyStyle.entries[styleIndex],
                            paintIndex = paintIndex,
                            onStyleToggle = { styleIndex = (styleIndex + 1) % BodyStyle.entries.size },
                            onPaintNext = { paintIndex = (paintIndex + 1) % PaintOptions.size },
                            suggestedRoutine = routinePlanner.suggested(trip),
                            routineMessage = routineMessage,
                            onRoutine = ::runRoutine,
                            onAction = vehicleStateProvider::perform,
                            onVoice = ::startVoice,
                            parkedSpot = parkedSpot,
                            isDemo = demoDrive != null,
                            isLandscape = isLandscape,
                        )
                    } else {
                        ParkedDashboard(
                            trip = trip,
                            vehicleState = vehicleState,
                            report = report,
                            navigationStatus = navigationState.status,
                            navigationError = navigationState.error,
                            isLandscape = isLandscape,
                            onBriefing = { say(briefing(trip, car, report)) },
                            demoDrive = demoDrive,
                        )
                    }
                }
            }
            val alertCount = report.alerts.size
            if (isLandscape) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    NavRail(tab, alertCount, vertical = true) { tab = it }
                    content(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content(Modifier.weight(1f).fillMaxWidth())
                    NavRail(tab, alertCount, vertical = false) { tab = it }
                }
            }
        } else {
            DrivingView(
                trip = trip,
                car = vehicleState.car,
                report = report,
                isLandscape = isLandscape,
                onBriefing = { say(briefing(trip, car, report)) },
                onVoice = ::startVoice,
            )
        }
        VoiceOverlay(voiceState, onCancel = voiceInputProvider::cancel, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun VoiceOverlay(state: VoiceInputState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val text = when {
        state.listening -> state.transcript ?: "듣고 있어요"
        state.error != null -> state.error
        else -> return
    }
    Row(
        modifier
            .padding(bottom = 96.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(PanelRaised)
            .clickable(onClick = onCancel)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (state.listening) Accent else Muted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = Background, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(text, color = TextMain, fontSize = ts(17), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NavRail(selected: Int, alertCount: Int, vertical: Boolean, onSelect: (Int) -> Unit) {
    val items = listOf(Icons.Outlined.DirectionsCar to "내 차", Icons.Outlined.HealthAndSafety to "드라이브 케어")
    val item: @Composable (Int, Modifier) -> Unit = { index, m ->
        val (icon, label) = items[index]
        val active = index == selected
        Column(
            m
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Transparent)
                .clickable { onSelect(index) }
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) TextMain else Muted, modifier = Modifier.size(24.dp))
            Text(
                if (index == 1 && alertCount > 0) "$label · $alertCount" else label,
                color = if (active) TextMain else Muted,
                fontSize = ts(13),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
    if (vertical) {
        Column(
            Modifier.fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Panel).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) { items.indices.forEach { item(it, Modifier) } }
    } else {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { items.indices.forEach { item(it, Modifier.weight(1f)) } }
    }
}

/** Driving: one glanceable headline, two facts, one big button. No scrolling, no animation. */
@Composable
private fun DrivingView(
    trip: TripSnapshot,
    car: io.github.aruru10313.drivecare.domain.CarSnapshot,
    report: DriveCareReport,
    isLandscape: Boolean,
    onBriefing: () -> Unit,
    onVoice: () -> Unit,
) {
    val headline = report.headline
    val accent = headline?.severity?.color() ?: Good
    val headlineText = headline?.title ?: when (val rest = report.rest) {
        is RestAdvice.ArriveBeforeRest -> "휴식 없이 도착해요"
        is RestAdvice.OnTrack -> "휴식까지 ${formatDuration(rest.secondsUntilRest)}"
        else -> "안전 운전 중"
    }
    val tripLine = trip.etaSeconds?.let { eta ->
        listOfNotNull(
            trip.destinationName,
            "${formatDuration(eta)} 남음",
            trip.remainingRouteMeters?.let(::formatKm),
        ).joinToString("  ·  ")
    } ?: "경로 안내를 기다리는 중"

    val headlineCard: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Panel)
                .border(3.dp, accent, RoundedCornerShape(28.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("주행 안전 모드", color = Muted, fontSize = ts(16), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                headlineText,
                color = accent,
                fontSize = ts(36),
                fontWeight = FontWeight.Bold,
                lineHeight = ts(42),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Text(tripLine, color = TextMain, fontSize = ts(22), lineHeight = ts(30), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val facts: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FactTile("휴식", restShort(report.rest), restColor(report.rest), Modifier.weight(1f))
            FactTile(
                if (car.energyType == EnergyType.EV) "주행 가능 · 배터리" else "주행 가능 · 연료",
                car.rangeRemainingMeters?.let(::formatKm) ?: "--",
                rangeColor(report.range),
                Modifier.weight(1f),
            )
        }
    }
    val button: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BriefingButton(onBriefing, Modifier.weight(1f).fillMaxHeight())
            Button(
                onClick = onVoice,
                modifier = Modifier.fillMaxHeight().width(120.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = TextMain),
            ) {
                Icon(Icons.Outlined.Mic, contentDescription = "음성 명령", modifier = Modifier.size(36.dp))
            }
        }
    }

    if (isLandscape) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            headlineCard(Modifier.weight(1.3f).fillMaxHeight())
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                facts(Modifier.fillMaxWidth().weight(1f))
                button(Modifier.fillMaxWidth().height(96.dp))
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            headlineCard(Modifier.fillMaxWidth().weight(1f))
            facts(Modifier.fillMaxWidth().height(130.dp))
            button(Modifier.fillMaxWidth().height(96.dp))
        }
    }
}

@Composable
private fun FactTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(Panel)
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = Muted, fontSize = ts(16), maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            color = accent,
            fontSize = ts(26),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BriefingButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Good, contentColor = Background),
    ) {
        Icon(Icons.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Text("브리핑", fontSize = ts(22), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ParkedDashboard(
    trip: TripSnapshot,
    vehicleState: VehicleSnapshot,
    report: DriveCareReport,
    navigationStatus: String,
    navigationError: String?,
    isLandscape: Boolean,
    onBriefing: () -> Unit,
    demoDrive: DemoDrive?,
) {
    val car = vehicleState.car
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("드라이브 케어", color = TextMain, fontSize = ts(30), fontWeight = FontWeight.Bold)
                Text(
                    if (trip.arrived) {
                        "${trip.destinationName ?: "목적지"} 도착 · 내리기 전에 확인하세요"
                    } else {
                        "주차 중 · 내비 ${navigationError?.let { "연결 안 됨" } ?: navigationStatus}"
                    },
                    color = Muted,
                    fontSize = ts(15),
                )
            }
            SourceChip(vehicleState)
        }

        report.alerts.forEach { AlertRow(it) }

        val cards = listOf<@Composable (Modifier) -> Unit>(
            { RestCard(car.tripDrivingSeconds, report.rest, it) },
            { RangeCard(trip, car.rangeRemainingMeters, car.energyPercent, car.energyType, report.range, it) },
            { ExitCheckCard(car.doorLocked, car.headlightsOn, it) },
        )
        if (isLandscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                cards.forEach { card -> card(Modifier.weight(1f).height(250.dp)) }
            }
        } else {
            cards.forEach { card -> card(Modifier.fillMaxWidth()) }
        }

        BriefingButton(onBriefing, Modifier.fillMaxWidth().height(76.dp))

        if (demoDrive != null) {
            DemoControls(demoDrive, trip.arrived, report.exitChecklistDone)
        }
        if (vehicleState.unavailable.isNotEmpty()) {
            Text(
                "이 차량에서 확인할 수 없는 항목: ${vehicleState.unavailable.sorted().joinToString(", ")}",
                color = Muted,
                fontSize = ts(13),
            )
        }
    }
}

@Composable
private fun SourceChip(state: VehicleSnapshot) {
    val ok = state.error == null
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(PanelRaised)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (ok) Good else Danger))
        Spacer(Modifier.size(8.dp))
        Text(if (ok) state.source else "차량 연결 오류", color = TextMain, fontSize = ts(13))
    }
}

@Composable
private fun AlertRow(alert: CareAlert) {
    val color = alert.severity.color()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = .14f))
            .border(1.dp, color.copy(alpha = .6f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(12.dp))
        Text(alert.title, color = TextMain, fontSize = ts(18), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DashCard(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Panel)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = Muted, fontSize = ts(15), fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Meter(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            .background(PanelRaised),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

@Composable
private fun RestCard(drivenSeconds: Int?, rest: RestAdvice, modifier: Modifier) {
    DashCard("휴식 코치", modifier) {
        Text(restShort(rest), color = restColor(rest), fontSize = ts(30), fontWeight = FontWeight.Bold)
        Meter((drivenSeconds ?: 0) / (2f * 60 * 60), restColor(rest))
        Text(
            drivenSeconds?.let { "연속 운전 ${formatDuration(it)} / 2시간마다 15분 휴식" }
                ?: "출발하면 연속 운전 시간을 잽니다",
            color = Muted,
            fontSize = ts(14),
        )
    }
}

@Composable
private fun RangeCard(
    trip: TripSnapshot,
    rangeMeters: Int?,
    energyPercent: Int?,
    energyType: EnergyType?,
    range: RangeAdvice,
    modifier: Modifier,
) {
    val what = if (energyType == EnergyType.EV) "배터리" else "연료"
    DashCard("주행 가능 거리", modifier) {
        Text(rangeMeters?.let(::formatKm) ?: "--", color = rangeColor(range), fontSize = ts(30), fontWeight = FontWeight.Bold)
        Meter((energyPercent ?: 0) / 100f, rangeColor(range))
        Text(
            listOfNotNull(
                energyPercent?.let { "$what $it%" },
                trip.remainingRouteMeters?.takeIf { !trip.arrived }?.let { "남은 경로 ${formatKm(it)}" },
            ).joinToString("  ·  ").ifEmpty { "$what 정보를 기다리는 중" },
            color = Muted,
            fontSize = ts(14),
        )
        Text(
            when (range) {
                is RangeAdvice.Enough -> "도착 후 ${formatKm(range.spareMeters)} 여유"
                is RangeAdvice.Tight -> "도착 후 ${formatKm(range.spareMeters)}만 남아요"
                is RangeAdvice.Short -> "${formatKm(range.missingMeters)} 부족"
                RangeAdvice.Unknown -> if (trip.arrived) "도착 완료" else "경로를 안내받으면 도착 가능 여부를 알려드려요"
            },
            color = TextMain,
            fontSize = ts(15),
        )
    }
}

@Composable
private fun ExitCheckCard(doorLocked: Map<DoorPosition, Boolean>, headlightsOn: Boolean?, modifier: Modifier) {
    DashCard("하차 체크", modifier) {
        DoorPosition.entries.forEach { door ->
            CheckLine(door.label + " 문", doorLocked[door]?.let { if (it) "잠김" else "열림" }, doorLocked[door])
        }
        CheckLine("전조등", headlightsOn?.let { if (it) "켜짐" else "꺼짐" }, headlightsOn?.not())
    }
}

@Composable
private fun CheckLine(label: String, value: String?, ok: Boolean?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (ok) {
                true -> "✓"
                false -> "!"
                null -> "–"
            },
            color = when (ok) {
                true -> Good
                false -> Danger
                null -> Muted
            },
            fontSize = ts(18),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(label, color = TextMain, fontSize = ts(16), modifier = Modifier.weight(1f))
        Text(value ?: "확인 불가", color = if (ok == false) Danger else Muted, fontSize = ts(15))
    }
}

@Composable
private fun DemoControls(demo: DemoDrive, arrived: Boolean, exitChecksDone: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DemoChip(if (arrived) "시연: 다시 출발" else "시연: 강릉 가는 길 주행") {
            // Restart only after arriving, so a fresh start keeps what the driver set up while parked.
            if (arrived) demo.stop()
            demo.start()
        }
        if (arrived && !exitChecksDone) {
            DemoChip("시연: 문 잠그고 전조등 끄기", demo::completeExitChecks)
        }
    }
    Text(
        "시연 모드 · 실제 Pleos 차량에서는 순정 내비와 차량 상태를 자동으로 읽습니다",
        color = Muted,
        fontSize = ts(12),
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun DemoChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PanelRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        color = TextMain,
        fontSize = ts(14),
    )
}

private fun restShort(rest: RestAdvice) = when (rest) {
    RestAdvice.Unknown -> "--"
    is RestAdvice.OnTrack -> "${formatDuration(rest.secondsUntilRest)} 남음"
    is RestAdvice.ArriveBeforeRest -> "휴식 없이 도착"
    is RestAdvice.RestSoon -> "${formatDuration(rest.secondsUntilRest)} 내"
    is RestAdvice.Overdue -> "지금 쉬세요"
}

private fun restColor(rest: RestAdvice) = when (rest) {
    is RestAdvice.Overdue -> Danger
    is RestAdvice.RestSoon -> Caution
    RestAdvice.Unknown -> Muted
    else -> Good
}

private fun rangeColor(range: RangeAdvice) = when (range) {
    is RangeAdvice.Short -> Danger
    is RangeAdvice.Tight -> Caution
    RangeAdvice.Unknown -> Muted
    is RangeAdvice.Enough -> Good
}

/** Text sizes in this file were tuned for a phone; scale them with the Pleos type scale. */
private fun ts(phoneSp: Int) = (phoneSp * PleosType.scale * 1.6f).sp
