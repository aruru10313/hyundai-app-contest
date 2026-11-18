package app.drivecare

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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.outlined.AddLocationAlt
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
import app.drivecare.data.AndroidCarUxRestrictionProvider
import app.drivecare.data.AndroidTtsProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import app.drivecare.data.DemoDrive
import app.drivecare.data.DemoMode
import app.drivecare.data.DemoOnCarUxRestrictionProvider
import app.drivecare.data.RestAreaRepository
import app.drivecare.domain.Geo
import app.drivecare.domain.RestArea
import app.drivecare.domain.RestStopFinder
import androidx.compose.material.icons.outlined.Coffee
import app.drivecare.domain.RestTracker
import app.drivecare.domain.RestStopSuggestion
import app.drivecare.domain.objectParticle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import app.drivecare.data.DemoParkingLocationProvider
import app.drivecare.data.FallbackTtsProvider
import app.drivecare.data.GuidanceAudio
import app.drivecare.data.ParkingLocationProvider
import app.drivecare.data.PleosParkingLocationProvider
import app.drivecare.data.PleosTtsProvider
import app.drivecare.data.PleosVoiceInputProvider
import app.drivecare.data.VoiceInputProvider
import app.drivecare.data.VoiceInputState
import app.drivecare.domain.VehicleAction
import app.drivecare.domain.VoiceCommand
import app.drivecare.domain.VoiceCommandParser
import app.drivecare.ui.Accent
import app.drivecare.data.DrivingSafetyMode
import app.drivecare.data.MockNavigationStateProvider
import app.drivecare.data.MockUxRestrictionProvider
import app.drivecare.data.MockVehicleStateProvider
import app.drivecare.data.NavigationStateProvider
import app.drivecare.data.PleosNavigationStateProvider
import app.drivecare.data.PleosVehicleStateProvider
import app.drivecare.data.ProviderConfig
import app.drivecare.data.TtsProvider
import app.drivecare.data.UxRestrictionProvider
import app.drivecare.data.VehicleSnapshot
import app.drivecare.data.VehicleStateProvider
import app.drivecare.domain.AnnouncementGate
import app.drivecare.domain.CareAlert
import app.drivecare.domain.DoorPosition
import app.drivecare.domain.DriveCareAdvisor
import app.drivecare.domain.DriveCareReport
import app.drivecare.domain.EnergyType
import app.drivecare.domain.RangeAdvice
import app.drivecare.domain.RestAdvice
import app.drivecare.domain.Severity
import app.drivecare.domain.TripAutoStartGate
import app.drivecare.domain.TripSnapshot
import app.drivecare.domain.briefing
import app.drivecare.domain.formatDuration
import app.drivecare.domain.formatKm
import app.drivecare.domain.RoutinePlanner
import app.drivecare.ui.Background
import app.drivecare.ui.BodyStyle
import app.drivecare.ui.Caution
import app.drivecare.ui.Danger
import app.drivecare.ui.Good
import app.drivecare.ui.Muted
import app.drivecare.ui.PaintOptions
import app.drivecare.ui.Pleos
import app.drivecare.ui.PleosType
import app.drivecare.ui.pleosShadowLevel1
import app.drivecare.ui.Panel
import app.drivecare.ui.PanelRaised
import app.drivecare.ui.TextMain
import app.drivecare.ui.SwitchOnContent
import app.drivecare.ui.SwitchOn
import app.drivecare.ui.VehicleHome
import app.drivecare.ui.color
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
        DemoMode.applyLaunchExtra(this, intent)
        val useDemo = !isAutomotive || ProviderConfig.forceDemoDrive || DemoMode.isOn(this)
        // Pleos voice services only exist on a Pleos head unit; elsewhere use Android's own.
        val guidanceAudio = GuidanceAudio(applicationContext)
        ttsProvider = FallbackTtsProvider(
            primary = if (isAutomotive) PleosTtsProvider(applicationContext, guidanceAudio.attributes) else null,
            fallback = AndroidTtsProvider(applicationContext, guidanceAudio.attributes),
            audio = guidanceAudio,
        )
        voiceInputProvider = PleosVoiceInputProvider(applicationContext)
        parkingLocationProvider = if (useDemo) {
            DemoParkingLocationProvider()
        } else {
            PleosParkingLocationProvider(applicationContext)
        }

        val carUx = if (isAutomotive && ProviderConfig.usePleosUxRestrictions) {
            AndroidCarUxRestrictionProvider(applicationContext)
        } else {
            null
        }
        val demoUx = if (useDemo) MockUxRestrictionProvider() else null
        uxRestrictionProvider = when {
            // Demo on a head unit still respects the car's real restrictions.
            carUx != null && demoUx != null -> DemoOnCarUxRestrictionProvider(carUx, demoUx, lifecycleScope)
            carUx != null -> carUx
            else -> demoUx ?: MockUxRestrictionProvider()
        }
        val demo = if (useDemo) {
            DemoDrive(lifecycleScope) { driving -> demoUx?.setDriving(driving) }
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
                        // Only a head unit has real data to switch back to.
                        onToggleDemo = if (isAutomotive && !ProviderConfig.forceDemoDrive) {
                            { DemoMode.set(this, !useDemo); recreate() }
                        } else {
                            null
                        },
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
    onToggleDemo: (() -> Unit)?,
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

    // A 15-minute stop mid-trip restarts the continuous-driving count. Demo time runs fast, so the
    // demo drive supplies its own clock.
    val now: () -> Long = remember(demoDrive) { demoDrive?.let { d -> { d.nowMillis() } } ?: { System.currentTimeMillis() } }
    val restTracker = remember { RestTracker() }
    var restEpoch by remember { mutableIntStateOf(0) }
    var restRemaining by remember { mutableStateOf<Int?>(null) }
    val car = vehicleState.car.let { raw ->
        restEpoch.let { raw.copy(tripDrivingSeconds = restTracker.continuousSeconds(raw.tripDrivingSeconds)) }
    }

    val advisor = remember { DriveCareAdvisor() }
    val report = advisor.evaluate(trip, car, isParked, resting = isParked && restRemaining != null)
    val announcementGate = remember { AnnouncementGate() }
    val tripStartGate = remember { TripAutoStartGate() }
    val scope = rememberCoroutineScope()
    val speechLock = remember { Mutex() }
    val routinePlanner = remember { RoutinePlanner() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var styleIndex by rememberSaveable { mutableIntStateOf(BodyStyle.SUV.ordinal) }
    var paintIndex by rememberSaveable { mutableIntStateOf(0) }
    var routineMessage by remember { mutableStateOf<String?>(null) }

    fun say(text: String): Job =
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

    // Rest stop on the way: when a break is due, find the next 휴게소 ahead and offer it as a waypoint.
    val context = LocalContext.current
    val restFinder = remember { RestStopFinder(RestAreaRepository.load(context)) }
    var declinedStop by remember { mutableStateOf<String?>(null) }
    var awaitingAnswer by remember { mutableStateOf<RestStopSuggestion?>(null) }
    val needsRest = report.rest is RestAdvice.RestSoon || report.rest is RestAdvice.Overdue
    // Range tight or short: prefer a stop where the car can also refuel / charge.
    val needsEnergy = report.range is RangeAdvice.Tight || report.range is RangeAdvice.Short ||
        (car.energyPercent ?: 100) <= 15
    val refuel = if (needsEnergy) car.energyType ?: EnergyType.FUEL else null
    val position = trip.position
    val heading = trip.headingDegrees
    // Close to the destination a detour makes less sense than resting on arrival.
    val arrivingSoon = (trip.etaSeconds ?: Int.MAX_VALUE) < 10 * 60
    // Once a place is offered it stays the offer until the car passes it, so the suggestion (and the
    // spoken question) doesn't hop between nearby stops as the car moves.
    var offeredStop by remember { mutableStateOf<RestArea?>(null) }
    val restOffer = if (!isParked && trip.isRouteDriving && trip.waypointName == null &&
        (needsRest || report.range is RangeAdvice.Short) && !arrivingSoon &&
        position != null && heading != null
    ) {
        val kept = offeredStop?.takeIf { area ->
            Geo.distanceMeters(position, area.point) > 500 &&
                Geo.angleBetween(Geo.bearingDegrees(position, area.point), heading) < 90
        }
        val fresh = remember(position, heading, trip.destination, refuel) {
            restFinder.nextAhead(position, heading, trip.destination, trip.destinationName, refuel)
        }
        // Switch only when the new pick lets the car refuel and the kept one doesn't.
        val keepOld = kept != null && (fresh == null || !fresh.refuelHere || (refuel != null && kept.canRefuel(refuel)))
        val offer = if (keepOld) {
            RestStopSuggestion(kept!!, Geo.distanceMeters(position, kept.point).toInt(), refuelHere = refuel != null && kept.canRefuel(refuel))
        } else {
            fresh
        }
        offer?.takeIf { it.area.name != declinedStop }
    } else {
        null
    }
    LaunchedEffect(restOffer?.area) { if (restOffer != null) offeredStop = restOffer.area }

    fun addRestStop(stop: RestStopSuggestion) {
        navigationStateProvider.addWaypoint(stop.area)
        awaitingAnswer = null
        say("${stop.area.name}${objectParticle(stop.area.name)} 경유지로 추가했어요.")
    }

    // Breaks: parked mid-route starts the rest timer; driving on ends it.
    LaunchedEffect(isParked) {
        if (isParked && trip.isRouteDriving && !trip.arrived) {
            restTracker.onParked(now(), vehicleState.car.tripDrivingSeconds)
            var announced = false
            while (true) {
                restRemaining = restTracker.remainingSeconds(now())
                if (!announced && restTracker.isComplete(now())) {
                    announced = true
                    say("15분 쉬었어요. 출발하면 연속 운전 시간을 처음부터 다시 셀게요.")
                }
                delay(1_000L)
            }
        } else if (!isParked && restTracker.isResting) {
            restTracker.onDriving(now())
            restRemaining = null
            restEpoch++
        }
    }
    // A new trip after arriving starts the count from zero (the car's own trip timer may keep running).
    var tripEnded by remember { mutableStateOf(false) }
    LaunchedEffect(trip.arrived) { if (trip.arrived) tripEnded = true }
    LaunchedEffect(trip.isRouteDriving) {
        if (trip.isRouteDriving && tripEnded) {
            tripEnded = false
            restTracker.reset()
            restRemaining = null
            restEpoch++
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
        awaitingAnswer?.let { pending ->
            when (VoiceCommandParser.answer(spoken)) {
                true -> return addRestStop(pending)
                false -> {
                    declinedStop = pending.area.name
                    awaitingAnswer = null
                    say("알겠어요. 다음 휴게소에서 다시 알려 드릴게요.")
                    return
                }
                null -> Unit
            }
        }
        when (val command = VoiceCommandParser.parse(spoken)) {
            VoiceCommand.AddRestStop -> restOffer?.let(::addRestStop) ?: say("가까운 휴게소를 찾지 못했어요.")
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

    // Ask once per place, then listen for "응" / "아니" without the driver touching the screen.
    val askedStops = remember { mutableSetOf<String>() }
    LaunchedEffect(restOffer?.area?.name) {
        val stop = restOffer ?: return@LaunchedEffect
        if (!askedStops.add(stop.area.name)) return@LaunchedEffect
        awaitingAnswer = stop
        val energyNote = when {
            !stop.refuelHere -> ""
            car.energyType == EnergyType.EV -> " 쉬면서 충전도 할 수 있어요."
            else -> " 쉬면서 주유도 할 수 있어요."
        }
        say("${stop.spokenPlace}${objectParticle(stop.area.name)} 경유지로 추가할까요?$energyNote 추가하려면 응이라고 말씀하세요.").join()
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
                            car = car,
                            report = report,
                            resting = restRemaining != null,
                            navigationStatus = navigationState.status,
                            navigationError = navigationState.error,
                            isLandscape = isLandscape,
                            onBriefing = { say(briefing(trip, car, report)) },
                            demoDrive = demoDrive,
                            onToggleDemo = onToggleDemo,
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
                restOffer = restOffer,
                onAddRestStop = { restOffer?.let(::addRestStop) },
            )
        }
        if (isParked) {
            restRemaining?.let { RestBanner(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)) }
        }
        VoiceOverlay(voiceState, onCancel = voiceInputProvider::cancel, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun VoiceOverlay(state: VoiceInputState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    // Errors and final transcripts are informational; clear them after a moment.
    LaunchedEffect(state) {
        if (!state.listening && state.error != null) {
            delay(4_000)
            onCancel()
        }
    }
    val text = when {
        state.listening -> state.transcript ?: "듣고 있어요"
        state.error != null -> state.error
        else -> return
    }
    Row(
        modifier
            .padding(bottom = 96.dp)
            .pleosShadowLevel1(RoundedCornerShape(32.dp))
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
    val items = listOf(Icons.Outlined.DirectionsCar to "내 차", Icons.Outlined.HealthAndSafety to "케어")
    // Pleos-style tab: a large touch tile, filled when selected, with a red count badge for alerts.
    val item: @Composable (Int, Modifier) -> Unit = { index, m ->
        val (icon, label) = items[index]
        val active = index == selected
        val content = if (active) SwitchOnContent else Muted
        Box(
            m
                .clip(RoundedCornerShape(20.dp))
                .background(if (active) SwitchOn else Color.Transparent)
                .clickable { onSelect(index) }
                .padding(vertical = 16.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Pleos iconography base size is 48dp.
                Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(48.dp))
                Spacer(Modifier.size(6.dp))
                Text(label, color = content, style = PleosType.labelMedium, maxLines = 1)
            }
            if (index == 1 && alertCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Danger),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$alertCount", color = Color.White, style = PleosType.labelSmall)
                }
            }
        }
    }
    if (vertical) {
        Column(
            Modifier
                .width(132.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(Panel)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { items.indices.forEach { item(it, Modifier.fillMaxWidth()) } }
    } else {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { items.indices.forEach { item(it, Modifier.weight(1f)) } }
    }
}

/** Driving: one glanceable headline, two facts, one big button. No scrolling, no animation. */
@Composable
private fun DrivingView(
    trip: TripSnapshot,
    car: app.drivecare.domain.CarSnapshot,
    report: DriveCareReport,
    isLandscape: Boolean,
    onBriefing: () -> Unit,
    onVoice: () -> Unit,
    restOffer: RestStopSuggestion?,
    onAddRestStop: () -> Unit,
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
            trip.waypointName?.let { "경유 $it" },
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
            restOffer?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    "휴게소 제안  ·  ${it.spokenPlace}" +
                        if (it.refuelHere) (if (car.energyType == EnergyType.EV) "  ·  충전 가능" else "  ·  주유 가능") else "",
                    color = Good,
                    fontSize = ts(20),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
            if (restOffer != null) {
                Button(
                    onClick = onAddRestStop,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Good, contentColor = Background),
                ) {
                    Icon(Icons.Outlined.AddLocationAlt, contentDescription = null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("경유지 추가", fontSize = ts(22), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            } else {
                BriefingButton(onBriefing, Modifier.weight(1f).fillMaxHeight())
            }
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
    car: app.drivecare.domain.CarSnapshot,
    report: DriveCareReport,
    resting: Boolean,
    navigationStatus: String,
    navigationError: String?,
    isLandscape: Boolean,
    onBriefing: () -> Unit,
    demoDrive: DemoDrive?,
    onToggleDemo: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Long-press switches between the demo trip and the car's real data (parked only).
                Text(
                    "드라이브 케어",
                    modifier = if (onToggleDemo != null) {
                        Modifier.pointerInput(onToggleDemo) { detectTapGestures(onLongPress = { onToggleDemo() }) }
                    } else {
                        Modifier
                    },
                    color = TextMain,
                    fontSize = ts(30),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (trip.arrived) {
                        "${trip.destinationName ?: "목적지"} 도착 · 내리기 전에 확인하세요"
                    } else {
                        "주차 중 · 내비 ${navigationError?.let { "연결 안 됨" } ?: navigationLabel(navigationStatus)}"
                    },
                    color = Muted,
                    fontSize = ts(15),
                )
            }
            SourceChip(vehicleState)
        }

        report.alerts.forEach { AlertRow(it) }

        val cards = listOf<@Composable (Modifier) -> Unit>(
            { RestCard(car.tripDrivingSeconds, report.rest, resting, it) },
            { RangeCard(trip, car.rangeRemainingMeters, car.energyPercent, car.energyType, report.range, it) },
            { ExitCheckCard(car.doorLocked, car.headlightsOn, it) },
        )
        if (isLandscape) {
            // As tall as the tallest card, so the exit checklist never gets cut off.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                cards.forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
            }
        } else {
            cards.forEach { card -> card(Modifier.fillMaxWidth()) }
        }

        BriefingButton(onBriefing, Modifier.fillMaxWidth().height(76.dp))

        if (demoDrive != null) {
            DemoControls(demoDrive, trip.arrived, report.exitChecklistDone, onToggleDemo)
        }
        if (vehicleState.unavailable.isNotEmpty()) {
            Text(
                "이 차량에서 확인할 수 없는 항목: ${vehicleState.unavailable.sorted().joinToString(", ")}",
                color = Muted,
                fontSize = ts(13),
            )
        }
        // CC-BY-4.0 attribution for the 3D car models shown on the home tab.
        Text(
            "3D 차량 모델: \"Generic passenger car pack\" by Comrade1280 · CC BY 4.0 (sketchfab.com/comrade1280)",
            color = Muted,
            fontSize = ts(11),
        )
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
private fun RestCard(drivenSeconds: Int?, rest: RestAdvice, resting: Boolean, modifier: Modifier) {
    DashCard("휴식 코치", modifier) {
        Text(
            if (resting) "휴식 중" else restShort(rest),
            color = if (resting) Good else restColor(rest),
            fontSize = ts(30),
            fontWeight = FontWeight.Bold,
        )
        Meter((drivenSeconds ?: 0) / (2f * 60 * 60), if (resting) Good else restColor(rest))
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
            CheckLine(door.label + " 문", doorLocked[door]?.let { if (it) "잠김" else "잠금 해제" }, doorLocked[door])
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

/** Shown while parked mid-trip: how long until the break counts as a full rest. */
@Composable
private fun RestBanner(remainingSeconds: Int, modifier: Modifier = Modifier) {
    val done = remainingSeconds <= 0
    Row(
        modifier = modifier
            .pleosShadowLevel1(RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(PanelRaised)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Coffee, contentDescription = null, tint = if (done) Good else Caution, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            if (done) {
                "휴식 완료 · 연속 운전 시간이 초기화됐어요"
            } else {
                "휴식 중 · %d:%02d 남음 · 15분 쉬면 연속 운전 시간이 초기화돼요".format(remainingSeconds / 60, remainingSeconds % 60)
            },
            color = TextMain,
            fontSize = ts(20),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DemoControls(demo: DemoDrive, arrived: Boolean, exitChecksDone: Boolean, onExit: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DemoChip(if (arrived) "시연: 다시 출발" else "시연: 강릉 가는 길 주행") {
            // Restart only after arriving, so a fresh start keeps what the driver set up while parked.
            if (arrived) demo.stop()
            demo.start()
        }
        if (arrived && !exitChecksDone) {
            DemoChip("시연: 문 잠그고 전조등 끄기", demo::completeExitChecks)
        }
        if (onExit != null) {
            DemoChip("시연 끝내기 · 실제 차량 데이터", onExit)
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

/** NaviHelper status names in words; unknown names are shown as they are. */
private fun navigationLabel(status: String) = when (status) {
    "ROUTE_DRIVE" -> "경로 안내 중"
    "WAYPOINT_ARRIVED" -> "경유지 도착"
    "INACTIVE" -> "안내 없음"
    else -> status
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
