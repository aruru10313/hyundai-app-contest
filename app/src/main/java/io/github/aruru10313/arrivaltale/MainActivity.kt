package io.github.aruru10313.arrivaltale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen()
                }
            }
        }
    }
}

/**
 * 빌드 골격 확인용 화면.
 * 주행 안전 가이드에 맞춰 큰 글씨와 단일 버튼만 사용한다.
 */
@Composable
private fun SetupScreen() {
    var taps by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "도착 동화",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Pleos Connect 빌드 골격",
            fontSize = 20.sp,
        )
        Text(
            text = "확인 횟수: $taps",
            fontSize = 18.sp,
        )
        Button(onClick = { taps++ }) {
            Text(text = "확인", fontSize = 24.sp)
        }
    }
}
