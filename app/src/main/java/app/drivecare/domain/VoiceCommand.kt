package app.drivecare.domain

sealed interface VoiceCommand {
    data class RunRoutine(val routine: Routine) : VoiceCommand
    data object Briefing : VoiceCommand
    /** "휴게소 들를래": add the suggested rest stop as a waypoint. */
    data object AddRestStop : VoiceCommand
    data class Control(val action: VehicleAction) : VoiceCommand
    data object Unknown : VoiceCommand
}

/**
 * Maps a spoken Korean sentence to a command with simple keyword rules, so it works offline with
 * any recognizer. Checked most-specific first (a temperature beats a plain "에어컨 켜").
 */
object VoiceCommandParser {
    private val number = Regex("""(\d{2}(?:\.5)?)\s*도""")

    fun parse(spoken: String): VoiceCommand {
        val text = spoken.replace(" ", "")
        val off = listOf("꺼", "끄", "off").any { it in text }

        number.find(spoken)?.let { match ->
            val celsius = match.groupValues[1].toFloat()
            if (celsius in 16f..30f) return VoiceCommand.Control(VehicleAction.Climate(on = true, targetC = celsius))
        }
        return when {
            listOf("휴게소", "쉬어", "쉼터").any { it in text } -> VoiceCommand.AddRestStop
            listOf("하차", "내릴", "주차모드").any { it in text } -> VoiceCommand.RunRoutine(Routine.EXIT)
            listOf("출발", "준비").any { it in text } -> VoiceCommand.RunRoutine(Routine.DEPARTURE)
            listOf("브리핑", "상태", "얼마나", "남았").any { it in text } -> VoiceCommand.Briefing
            listOf("에어컨", "공조", "히터", "냉방", "난방").any { it in text } ->
                VoiceCommand.Control(VehicleAction.Climate(on = !off))
            listOf("전조등", "라이트", "헤드라이트").any { it in text } ->
                VoiceCommand.Control(VehicleAction.Headlights(on = !off))
            "창문" in text && listOf("닫", "올려").any { it in text } ->
                VoiceCommand.Control(VehicleAction.CloseWindows)
            listOf("잠가", "잠궈", "잠금", "잠그").any { it in text } ->
                VoiceCommand.Control(VehicleAction.LockDoors(true))
            "문" in text && "창문" !in text && listOf("열어", "해제").any { it in text } ->
                VoiceCommand.Control(VehicleAction.LockDoors(false))
            else -> VoiceCommand.Unknown
        }
    }

    /** Reply to a yes/no question: true = yes, false = no, null = not an answer. */
    fun answer(spoken: String): Boolean? {
        val text = spoken.replace(" ", "")
        return when {
            listOf("아니", "괜찮아", "됐어", "안해", "싫어", "취소").any { it in text } -> false
            // Short replies only count at the start ("어" is also inside "에어컨").
            listOf("응", "네", "예", "어", "ㅇㅇ").any { text.startsWith(it) } -> true
            listOf("좋아", "그래", "추가").any { it in text } -> true
            else -> null
        }
    }

    /** Spoken confirmation for a direct control command. */
    fun confirmation(action: VehicleAction): String = when (action) {
        is VehicleAction.LockDoors -> if (action.locked) "문을 잠글게요." else "문 잠금을 해제할게요."
        is VehicleAction.Headlights -> if (action.on) "전조등을 켤게요." else "전조등을 끌게요."
        is VehicleAction.Climate -> when {
            !action.on -> "공조를 끌게요."
            action.targetC != null -> "실내 온도를 ${formatCelsius(action.targetC)}도로 맞출게요."
            else -> "공조를 켤게요."
        }
        VehicleAction.CloseWindows -> "창문을 닫을게요."
    }

    private fun formatCelsius(c: Float) = if (c % 1f == 0f) c.toInt().toString() else c.toString()
}

/** 을/를 for a Korean word, decided by the last Hangul syllable (brackets and spaces skipped). */
fun objectParticle(word: String): String {
    val last = word.lastOrNull { it in '가'..'힣' } ?: return "를"
    return if ((last - '가') % 28 != 0) "을" else "를"
}
