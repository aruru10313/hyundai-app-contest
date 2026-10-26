package io.github.aruru10313.drivecare.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {
    private fun parse(text: String) = VoiceCommandParser.parse(text)

    @Test
    fun `routines and briefing`() {
        assertEquals(VoiceCommand.RunRoutine(Routine.DEPARTURE), parse("출발 준비해 줘"))
        assertEquals(VoiceCommand.RunRoutine(Routine.EXIT), parse("이제 내릴게"))
        assertEquals(VoiceCommand.Briefing, parse("얼마나 남았어"))
    }

    @Test
    fun `climate on, off and temperature`() {
        assertEquals(VoiceCommand.Control(VehicleAction.Climate(on = true)), parse("에어컨 켜 줘"))
        assertEquals(VoiceCommand.Control(VehicleAction.Climate(on = false)), parse("히터 꺼"))
        assertEquals(VoiceCommand.Control(VehicleAction.Climate(on = true, targetC = 22.5f)), parse("온도 22.5도로 맞춰"))
    }

    @Test
    fun `out of range temperature is not a temperature command`() {
        assertEquals(VoiceCommand.Unknown, parse("밖이 35도야"))
    }

    @Test
    fun `doors lights and windows`() {
        assertEquals(VoiceCommand.Control(VehicleAction.LockDoors(true)), parse("문 잠가"))
        assertEquals(VoiceCommand.Control(VehicleAction.LockDoors(false)), parse("문 열어 줘"))
        assertEquals(VoiceCommand.Control(VehicleAction.Headlights(false)), parse("전조등 꺼"))
        assertEquals(VoiceCommand.Control(VehicleAction.CloseWindows), parse("창문 닫아"))
    }

    @Test
    fun `confirmation wording`() {
        assertEquals("실내 온도를 22도로 맞출게요.", VoiceCommandParser.confirmation(VehicleAction.Climate(true, 22f)))
        assertEquals("문을 잠글게요.", VoiceCommandParser.confirmation(VehicleAction.LockDoors(true)))
    }
}
