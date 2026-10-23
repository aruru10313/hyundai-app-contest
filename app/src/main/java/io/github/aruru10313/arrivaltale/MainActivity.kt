package io.github.aruru10313.arrivaltale

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import io.github.aruru10313.arrivaltale.data.AndroidTtsProvider
import io.github.aruru10313.arrivaltale.data.EtaProvider
import io.github.aruru10313.arrivaltale.data.LocalStoryGenerator
import io.github.aruru10313.arrivaltale.data.MockEtaProvider
import io.github.aruru10313.arrivaltale.data.TtsProvider
import io.github.aruru10313.arrivaltale.domain.AgeGroup
import io.github.aruru10313.arrivaltale.domain.PlannedChapter
import io.github.aruru10313.arrivaltale.domain.Story
import io.github.aruru10313.arrivaltale.domain.StoryPlanner
import io.github.aruru10313.arrivaltale.domain.StoryTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var ttsProvider: AndroidTtsProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsProvider = AndroidTtsProvider(applicationContext)
        val stories = LocalStoryGenerator(applicationContext).stories(AgeGroup.ELEMENTARY)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ArrivalTaleScreen(
                        stories = stories,
                        etaProvider = MockEtaProvider(),
                        ttsProvider = ttsProvider,
                        planner = StoryPlanner(),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        ttsProvider.release()
        super.onDestroy()
    }
}

@Composable
private fun ArrivalTaleScreen(
    stories: List<Story>,
    etaProvider: EtaProvider,
    ttsProvider: TtsProvider,
    planner: StoryPlanner,
) {
    var remainingSeconds by remember { mutableIntStateOf(10 * 60) }
    var currentChapter by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("주제를 고르면 동화가 시작돼요") }
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    var etaJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun stopStory() {
        playbackJob?.cancel()
        playbackJob = null
        etaJob?.cancel()
        etaJob = null
        ttsProvider.stop()
        status = "재생이 멈췄어요"
    }

    BackHandler(enabled = playbackJob?.isActive == true) {
        stopStory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "도착 동화",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "도착까지 ${remainingSeconds / 60}분 ${remainingSeconds % 60}초",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = status,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
        StoryTheme.entries.forEach { theme ->
            val story = stories.first { it.theme == theme }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = playbackJob?.isActive != true,
                onClick = {
                    val plan: List<PlannedChapter> = planner.plan(remainingSeconds, story.chapters)
                    if (etaJob?.isActive != true) {
                        etaJob = scope.launch {
                            etaProvider.remainingSeconds().collect { remainingSeconds = it }
                        }
                    }
                    playbackJob = scope.launch {
                        try {
                            plan.forEach { planned ->
                                currentChapter = planned.chapter.title
                                status = "${story.title} · $currentChapter"
                                ttsProvider.speak(planned.text)
                            }
                            status = "${story.title} 이야기가 끝났어요"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: IllegalStateException) {
                            status = "음성을 재생할 수 없어요: ${error.message.orEmpty()}"
                        }
                    }
                },
            ) {
                Text(text = "${theme.label} 이야기: ${story.title}", fontSize = 20.sp)
            }
        }
        if (playbackJob?.isActive == true) {
            Button(onClick = ::stopStory) {
                Text(text = "멈추기", fontSize = 20.sp)
            }
        }
        if (currentChapter.isNotBlank()) Text(text = "현재 챕터: $currentChapter", fontSize = 18.sp)
    }
}
