package io.github.aruru10313.arrivaltale

import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aruru10313.arrivaltale.data.AndroidTtsProvider
import io.github.aruru10313.arrivaltale.data.EtaProvider
import io.github.aruru10313.arrivaltale.data.LocalStoryGenerator
import io.github.aruru10313.arrivaltale.data.MockEtaProvider
import io.github.aruru10313.arrivaltale.data.TtsProvider
import io.github.aruru10313.arrivaltale.data.PleosVehicleStateProvider
import io.github.aruru10313.arrivaltale.data.ProviderConfig
import io.github.aruru10313.arrivaltale.data.MockVehicleStateProvider
import io.github.aruru10313.arrivaltale.data.AndroidCarUxRestrictionProvider
import io.github.aruru10313.arrivaltale.data.MockUxRestrictionProvider
import io.github.aruru10313.arrivaltale.data.UxRestrictionProvider
import io.github.aruru10313.arrivaltale.data.DrivingSafetyMode
import io.github.aruru10313.arrivaltale.data.MockNavigationStateProvider
import io.github.aruru10313.arrivaltale.data.NavigationStateProvider
import io.github.aruru10313.arrivaltale.data.PleosNavigationStateProvider
import io.github.aruru10313.arrivaltale.data.VehicleStateProvider
import io.github.aruru10313.arrivaltale.data.VehicleSnapshot
import io.github.aruru10313.arrivaltale.domain.AgeGroup
import io.github.aruru10313.arrivaltale.domain.PlannedChapter
import io.github.aruru10313.arrivaltale.domain.Story
import io.github.aruru10313.arrivaltale.domain.StoryPlanner
import io.github.aruru10313.arrivaltale.domain.StoryTheme
import io.github.aruru10313.arrivaltale.domain.TripAutoStartGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Night = Color(0xFF10152E)
private val Panel = Color(0xFF1B2342)
private val Muted = Color(0xFFB8C1DA)
private val Gold = Color(0xFFFFD78A)

class MainActivity : ComponentActivity() {
    private lateinit var ttsProvider: AndroidTtsProvider
    private lateinit var vehicleStateProvider: VehicleStateProvider
    private lateinit var uxRestrictionProvider: UxRestrictionProvider
    private lateinit var navigationStateProvider: NavigationStateProvider
    private var isAutomotive = false
    private val carDrivingStatePermission = "android.car.permission.CAR_DRIVING_STATE_3P"

    private val drivingStatePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted || isAutomotive) uxRestrictionProvider.connect()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsProvider = AndroidTtsProvider(applicationContext)
        isAutomotive = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        vehicleStateProvider = if (isAutomotive && ProviderConfig.usePleosVehicleSdk) {
            PleosVehicleStateProvider(applicationContext)
        } else {
            MockVehicleStateProvider()
        }
        vehicleStateProvider.connect()
        uxRestrictionProvider = if (isAutomotive && ProviderConfig.usePleosUxRestrictions) {
            AndroidCarUxRestrictionProvider(applicationContext)
        } else {
            MockUxRestrictionProvider()
        }
        if (
            isAutomotive &&
            checkSelfPermission(carDrivingStatePermission) != PackageManager.PERMISSION_GRANTED
        ) {
            drivingStatePermissionLauncher.launch(carDrivingStatePermission)
        } else {
            uxRestrictionProvider.connect()
        }
        navigationStateProvider = if (isAutomotive && ProviderConfig.usePleosNavigationSdk) {
            PleosNavigationStateProvider(applicationContext)
        } else {
            MockNavigationStateProvider()
        }
        navigationStateProvider.connect()
        val stories = LocalStoryGenerator(applicationContext).stories(AgeGroup.ELEMENTARY)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Night) {
                    ArrivalTaleScreen(
                        stories = stories,
                        etaProvider = MockEtaProvider(),
                        ttsProvider = ttsProvider,
                        vehicleStateProvider = vehicleStateProvider,
                        uxRestrictionProvider = uxRestrictionProvider,
                        navigationStateProvider = navigationStateProvider,
                        planner = StoryPlanner(),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        navigationStateProvider.release()
        uxRestrictionProvider.release()
        vehicleStateProvider.release()
        ttsProvider.release()
        super.onDestroy()
    }
}

@Composable
private fun ArrivalTaleScreen(
    stories: List<Story>,
    etaProvider: EtaProvider,
    ttsProvider: TtsProvider,
    vehicleStateProvider: VehicleStateProvider,
    uxRestrictionProvider: UxRestrictionProvider,
    navigationStateProvider: NavigationStateProvider,
    planner: StoryPlanner,
) {
    var remainingSeconds by remember { mutableIntStateOf(10 * 60) }
    var currentChapter by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("오늘의 모험을 골라보세요") }
    var selectedStory by remember { mutableStateOf(stories.first()) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    var etaJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val vehicleState by vehicleStateProvider.state.collectAsState()
    val uxState by uxRestrictionProvider.state.collectAsState()
    val navigationState by navigationStateProvider.state.collectAsState()
    val autoStartGate = remember { TripAutoStartGate() }

    fun stopStory() {
        playbackJob?.cancel()
        playbackJob = null
        etaJob?.cancel()
        etaJob = null
        ttsProvider.stop()
        isPlaying = false
        status = "이야기를 잠시 쉬고 있어요"
    }

    fun startStory() {
        if (isPlaying) {
            stopStory()
            return
        }
        val plan: List<PlannedChapter> = planner.plan(remainingSeconds, selectedStory.chapters)
        isPlaying = true
        if (etaJob?.isActive != true) {
            etaJob = scope.launch {
                etaProvider.remainingSeconds().collect { remainingSeconds = it }
            }
        }
        playbackJob = scope.launch {
            try {
                plan.forEach { planned ->
                    currentChapter = planned.chapter.title
                    status = "${selectedStory.title} 이야기를 들려줄게요"
                    ttsProvider.speak(planned.text)
                }
                status = "도착 전에 이야기가 끝났어요"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalStateException) {
                status = "음성을 재생할 수 없어요: ${error.message.orEmpty()}"
            } finally {
                isPlaying = false
            }
        }
    }

    BackHandler(enabled = isPlaying, onBack = ::stopStory)

    LaunchedEffect(navigationState.isRouteDriving) {
        if (autoStartGate.onRouteDrivingChanged(navigationState.isRouteDriving)) {
            startStory()
        } else if (!navigationState.isRouteDriving && isPlaying) {
            stopStory()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF151B38), Night, Color(0xFF0C1023)),
                ),
            )
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uxState.mode == DrivingSafetyMode.PARKED) {
                    StoryScene(
                        story = selectedStory,
                        modifier = Modifier
                            .weight(1.08f)
                            .fillMaxHeight()
                            .padding(vertical = 10.dp),
                    )
                }
                StoryControls(
                    stories = stories,
                    selectedStory = selectedStory,
                    onStorySelected = { selectedStory = it },
                    remainingSeconds = remainingSeconds,
                    currentChapter = currentChapter,
                    vehicleState = vehicleState,
                    uxState = uxState,
                    navigationState = navigationState,
                    onMockDrivingToggle = {
                        (uxRestrictionProvider as? MockUxRestrictionProvider)?.let { mockUx ->
                            val driving = uxState.mode == DrivingSafetyMode.PARKED
                            mockUx.setDriving(driving)
                            (navigationStateProvider as? MockNavigationStateProvider)
                                ?.setRouteDriving(driving)
                        }
                    },
                    showMockToggle = uxRestrictionProvider.isMock && navigationStateProvider.isMock,
                    status = status,
                    isPlaying = isPlaying,
                    onPlayToggle = ::startStory,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (uxState.mode == DrivingSafetyMode.PARKED) {
                    StoryScene(
                        story = selectedStory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                    )
                }
                StoryControls(
                    stories = stories,
                    selectedStory = selectedStory,
                    onStorySelected = { selectedStory = it },
                    remainingSeconds = remainingSeconds,
                    currentChapter = currentChapter,
                    vehicleState = vehicleState,
                    uxState = uxState,
                    navigationState = navigationState,
                    onMockDrivingToggle = {
                        (uxRestrictionProvider as? MockUxRestrictionProvider)?.let { mockUx ->
                            val driving = uxState.mode == DrivingSafetyMode.PARKED
                            mockUx.setDriving(driving)
                            (navigationStateProvider as? MockNavigationStateProvider)
                                ?.setRouteDriving(driving)
                        }
                    },
                    showMockToggle = uxRestrictionProvider.isMock && navigationStateProvider.isMock,
                    status = status,
                    isPlaying = isPlaying,
                    onPlayToggle = ::startStory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StoryScene(
    story: Story,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF202747)),
    ) {
        if (visible) Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF252B52), Color(0xFF535478), Color(0xFF202747)),
                ),
            )
            val w = size.width
            val h = size.height
            val stars = listOf(
                .10f to .18f, .21f to .31f, .34f to .14f, .50f to .26f, .68f to .12f,
                .83f to .23f, .92f to .13f, .77f to .36f, .15f to .42f,
            )
            stars.forEachIndexed { index, (x, y) ->
                drawCircle(
                    color = Color.White.copy(alpha = if (index % 3 == 0) .9f else .58f),
                    radius = if (index % 3 == 0) 3.2f else 2f,
                    center = Offset(w * x, h * y),
                )
            }
            when (story.theme) {
                StoryTheme.ADVENTURE -> drawForest(w, h)
                StoryTheme.ANIMALS -> drawAnimalMeadow(w, h)
                StoryTheme.SPACE -> drawSpace(w, h)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xD910142C)),
                    ),
                )
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Text("오늘의 그림 동화", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                story.title,
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun DrawScope.drawForest(w: Float, h: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFF2B6), Color(0xFFFFC96F).copy(alpha = .8f), Color.Transparent),
            center = Offset(w * .52f, h * .52f),
            radius = h * .24f,
        ),
        radius = h * .24f,
        center = Offset(w * .52f, h * .52f),
    )
    drawCircle(Color(0xFFFFE5A0), radius = h * .075f, center = Offset(w * .52f, h * .52f))
    drawRoundRect(
        color = Color(0xFF76503D),
        topLeft = Offset(w * .505f, h * .55f),
        size = Size(w * .03f, h * .19f),
    )
    drawHills(w, h, Color(0xFF253D50), Color(0xFF172E3D))
    listOf(.09f, .86f, .25f, .72f).forEachIndexed { index, x ->
        val y = if (index < 2) .46f else .59f
        val radius = if (index < 2) .13f else .085f
        drawCircle(Color(0xFF122A3A), radius = h * radius, center = Offset(w * x, h * y))
        drawRect(
            color = Color(0xFF493A3A),
            topLeft = Offset(w * x - h * .018f, h * y),
            size = Size(h * .036f, h * .27f),
        )
    }
}

private fun DrawScope.drawAnimalMeadow(w: Float, h: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFF3C1), Color(0xFFEABF8B), Color(0xFFAA806C)),
            center = Offset(w * .53f, h * .56f),
            radius = h * .25f,
        ),
        radius = h * .19f,
        center = Offset(w * .53f, h * .56f),
    )
    drawCircle(Color(0xFFDBA97C), radius = h * .07f, center = Offset(w * .39f, h * .41f))
    drawCircle(Color(0xFFDBA97C), radius = h * .07f, center = Offset(w * .67f, h * .41f))
    drawCircle(Color(0xFF382D39), radius = h * .012f, center = Offset(w * .49f, h * .54f))
    drawCircle(Color(0xFF382D39), radius = h * .012f, center = Offset(w * .57f, h * .54f))
    drawCircle(Color(0xFF98615C), radius = h * .018f, center = Offset(w * .53f, h * .59f))
    drawHills(w, h, Color(0xFF54735F), Color(0xFF243F49))
    listOf(.13f, .25f, .80f, .91f).forEach { x ->
        drawCircle(Color(0xFFFFD78A), radius = 3f, center = Offset(w * x, h * .70f))
    }
}

private fun DrawScope.drawSpace(w: Float, h: Float) {
    val center = Offset(w * .52f, h * .48f)
    drawOval(
        color = Color(0xFFB7B8E5),
        topLeft = Offset(w * .28f, h * .40f),
        size = Size(w * .48f, h * .16f),
        style = Stroke(width = h * .025f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFD99D), Color(0xFFE98A98), Color(0xFF7E67B8)),
            center = Offset(center.x - h * .07f, center.y - h * .08f),
            radius = h * .27f,
        ),
        radius = h * .19f,
        center = center,
    )
    drawOval(
        color = Color(0xFFE3C5FF),
        topLeft = Offset(w * .28f, h * .40f),
        size = Size(w * .48f, h * .16f),
        style = Stroke(width = h * .025f),
    )
    drawHills(w, h, Color(0xFF403665), Color(0xFF211F43))
}

private fun DrawScope.drawHills(w: Float, h: Float, back: Color, front: Color) {
    val backHill = Path().apply {
        moveTo(0f, h * .72f)
        cubicTo(w * .12f, h * .49f, w * .31f, h * .54f, w * .44f, h * .70f)
        cubicTo(w * .65f, h * .45f, w * .83f, h * .49f, w, h * .68f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    val frontHill = Path().apply {
        moveTo(0f, h * .82f)
        cubicTo(w * .18f, h * .65f, w * .34f, h * .70f, w * .53f, h * .84f)
        cubicTo(w * .72f, h * .67f, w * .84f, h * .68f, w, h * .78f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(backHill, back)
    drawPath(frontHill, Brush.verticalGradient(listOf(front, front.copy(red = front.red * .65f))))
}

@Composable
private fun StoryControls(
    stories: List<Story>,
    selectedStory: Story,
    onStorySelected: (Story) -> Unit,
    remainingSeconds: Int,
    currentChapter: String,
    vehicleState: io.github.aruru10313.arrivaltale.data.VehicleSnapshot,
    uxState: io.github.aruru10313.arrivaltale.data.UxRestrictionSnapshot,
    navigationState: io.github.aruru10313.arrivaltale.data.NavigationSnapshot,
    onMockDrivingToggle: () -> Unit,
    showMockToggle: Boolean,
    status: String,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("도착 동화", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(
            "도착까지  ${remainingSeconds / 60}분 ${remainingSeconds % 60}초",
            color = Gold,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (uxState.mode == DrivingSafetyMode.DRIVING) {
                "주행 안전 모드"
            } else {
                "주차 모드 · 이야기 선택"
            },
            color = if (uxState.mode == DrivingSafetyMode.DRIVING) Gold else Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (navigationState.error != null) {
            Text(
                "내비 상태를 확인할 수 없습니다",
                color = Color(0xFFFFB4A9),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "내비 상태  ${navigationState.status}",
                color = Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            status,
            color = Muted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (uxState.mode == DrivingSafetyMode.PARKED) {
            VehicleStateCard(vehicleState)
        }
        if (uxState.mode == DrivingSafetyMode.PARKED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stories.forEach { story ->
                    val selected = story == selectedStory
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(enabled = !isPlaying) { onStorySelected(story) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) Color(0xFF554878) else Panel,
                        tonalElevation = if (selected) 4.dp else 0.dp,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(story.theme.label, color = if (selected) Gold else Muted, fontSize = 17.sp)
                            Text(
                                if (selected) "선택됨" else "선택",
                                color = Color.White.copy(alpha = .72f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                "주행 중에는 동화 장면과 주제 선택을 숨깁니다",
                color = Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = onPlayToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPlaying) Color(0xFF76565D) else Color(0xFFFFD78A),
                contentColor = if (isPlaying) Color.White else Color(0xFF29213A),
            ),
        ) {
            Text(
                if (isPlaying) "■   이야기 잠시 멈추기" else "▶   동화 들려주기",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (currentChapter.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Panel,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Gold),
                    )
                    Text(
                        "지금 듣는 이야기  ·  $currentChapter",
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        if (showMockToggle) {
            Text(
                if (uxState.mode == DrivingSafetyMode.PARKED) {
                    "시연: 주행 시작"
                } else {
                    "시연: 주차로 전환"
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onMockDrivingToggle)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                color = Muted,
                fontSize = 13.sp,
            )
        }
        Text("화면을 보지 않아도 음성으로 함께 즐겨요", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun VehicleStateCard(
    state: VehicleSnapshot,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Panel,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(state.source, color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val mode = state.drivingMode?.let { "주행 모드  $it" } ?: "주행 모드  확인 중"
            val brake = state.brakePedalPressed?.let {
                if (it) "브레이크  감지" else "브레이크  해제"
            } ?: "브레이크  확인 중"
            Text(mode, color = Color.White, fontSize = 15.sp)
            Text(brake, color = Muted, fontSize = 14.sp)
            state.error?.let { error ->
                Text("차량 SDK 응답: $error", color = Color(0xFFFFB4A9), fontSize = 12.sp)
            }
        }
    }
}
