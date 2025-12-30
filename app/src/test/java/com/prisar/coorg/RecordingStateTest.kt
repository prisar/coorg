package com.prisar.coorg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStateTest {

    @Test
    fun defaultState_hasCorrectValues() {
        val state = RecordingState()

        assertFalse(state.isRecording)
        assertEquals("", state.recognizedText)
        assertEquals(0, state.wordCount)
        assertEquals(0, state.recordingDurationSeconds)
        assertEquals(0, state.wordsPerMinute)
        assertEquals(emptyList<Pair<Int, Int>>(), state.wpmDataPoints)
        assertNull(state.error)
    }

    @Test
    fun copy_modifiesOnlySpecifiedFields() {
        val original = RecordingState()
        val modified = original.copy(isRecording = true, wordCount = 5)

        assertTrue(modified.isRecording)
        assertEquals(5, modified.wordCount)
        assertEquals("", modified.recognizedText)
        assertEquals(0, modified.recordingDurationSeconds)
        assertEquals(0, modified.wordsPerMinute)
        assertEquals(emptyList<Pair<Int, Int>>(), modified.wpmDataPoints)
        assertNull(modified.error)
    }

    @Test
    fun copy_withRecognizedText_updatesText() {
        val state = RecordingState()
        val updated = state.copy(recognizedText = "hello world")

        assertEquals("hello world", updated.recognizedText)
    }

    @Test
    fun copy_withWpmDataPoints_updatesDataPoints() {
        val state = RecordingState()
        val dataPoints = listOf(10 to 60, 20 to 75, 30 to 80)
        val updated = state.copy(wpmDataPoints = dataPoints)

        assertEquals(3, updated.wpmDataPoints.size)
        assertEquals(10 to 60, updated.wpmDataPoints[0])
        assertEquals(20 to 75, updated.wpmDataPoints[1])
        assertEquals(30 to 80, updated.wpmDataPoints[2])
    }

    @Test
    fun copy_withError_setsErrorMessage() {
        val state = RecordingState()
        val updated = state.copy(error = "Recording failed")

        assertEquals("Recording failed", updated.error)
    }

    @Test
    fun copy_clearingError_setsToNull() {
        val state = RecordingState(error = "Some error")
        val updated = state.copy(error = null)

        assertNull(updated.error)
    }

    @Test
    fun fullRecordingState_hasAllFields() {
        val dataPoints = listOf(10 to 50, 20 to 60)
        val state = RecordingState(
            isRecording = true,
            recognizedText = "test speech",
            wordCount = 2,
            recordingDurationSeconds = 30,
            wordsPerMinute = 4,
            wpmDataPoints = dataPoints,
            error = null
        )

        assertTrue(state.isRecording)
        assertEquals("test speech", state.recognizedText)
        assertEquals(2, state.wordCount)
        assertEquals(30, state.recordingDurationSeconds)
        assertEquals(4, state.wordsPerMinute)
        assertEquals(dataPoints, state.wpmDataPoints)
        assertNull(state.error)
    }

    @Test
    fun recordingState_withError_canStillHaveOtherData() {
        val state = RecordingState(
            isRecording = false,
            recognizedText = "partial text",
            wordCount = 2,
            recordingDurationSeconds = 10,
            wordsPerMinute = 12,
            wpmDataPoints = listOf(5 to 10),
            error = "Connection lost"
        )

        assertFalse(state.isRecording)
        assertEquals("partial text", state.recognizedText)
        assertEquals(2, state.wordCount)
        assertEquals(10, state.recordingDurationSeconds)
        assertEquals(12, state.wordsPerMinute)
        assertEquals(1, state.wpmDataPoints.size)
        assertEquals("Connection lost", state.error)
    }

    @Test
    fun equality_sameValues_areEqual() {
        val state1 = RecordingState(isRecording = true, wordCount = 5)
        val state2 = RecordingState(isRecording = true, wordCount = 5)

        assertEquals(state1, state2)
    }

    @Test
    fun copy_createsNewInstance() {
        val original = RecordingState(wordCount = 10)
        val copied = original.copy()

        assertEquals(original, copied)
    }
}
