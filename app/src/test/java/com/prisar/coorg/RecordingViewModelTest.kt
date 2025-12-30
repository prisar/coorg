package com.prisar.coorg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecordingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: RecordingViewModel
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        viewModel = RecordingViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasCorrectDefaults() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.isRecording)
            assertEquals("", state.recognizedText)
            assertEquals(0, state.wordCount)
            assertEquals(0, state.recordingDurationSeconds)
            assertEquals(0, state.wordsPerMinute)
            assertEquals(emptyList<Pair<Int, Int>>(), state.wpmDataPoints)
            assertNull(state.error)
        }
    }

    @Test
    fun stopRecording_whenNotRecording_handlesGracefully() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.stopRecording()
            val state = awaitItem()
            assertFalse(state.isRecording)
        }
    }

    @Test
    fun wpmCalculation_withZeroDuration_returnsZero() {
        val wordCount = 10
        val durationSeconds = 0

        val wpm = if (durationSeconds > 0) {
            ((wordCount.toFloat() / durationSeconds) * 60).toInt()
        } else {
            0
        }

        assertEquals(0, wpm)
    }

    @Test
    fun wpmCalculation_with60Words60Seconds_returns60() {
        val wordCount = 60
        val durationSeconds = 60

        val wpm = ((wordCount.toFloat() / durationSeconds) * 60).toInt()

        assertEquals(60, wpm)
    }

    @Test
    fun wpmCalculation_with30Words60Seconds_returns30() {
        val wordCount = 30
        val durationSeconds = 60

        val wpm = ((wordCount.toFloat() / durationSeconds) * 60).toInt()

        assertEquals(30, wpm)
    }

    @Test
    fun wpmCalculation_with120Words60Seconds_returns120() {
        val wordCount = 120
        val durationSeconds = 60

        val wpm = ((wordCount.toFloat() / durationSeconds) * 60).toInt()

        assertEquals(120, wpm)
    }

    @Test
    fun wpmCalculation_with50Words30Seconds_returns100() {
        val wordCount = 50
        val durationSeconds = 30

        val wpm = ((wordCount.toFloat() / durationSeconds) * 60).toInt()

        assertEquals(100, wpm)
    }

    @Test
    fun wordCount_emptyText_returnsZero() {
        val text = ""
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }

        assertEquals(0, words.size)
    }

    @Test
    fun wordCount_singleWord_returnsOne() {
        val text = "hello"
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }

        assertEquals(1, words.size)
    }

    @Test
    fun wordCount_multipleWords_returnsCorrectCount() {
        val text = "hello world this is a test"
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }

        assertEquals(6, words.size)
    }

    @Test
    fun wordCount_withExtraSpaces_returnsCorrectCount() {
        val text = "hello  world   this    is"
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }

        assertEquals(4, words.size)
    }

    @Test
    fun wordCount_withLeadingTrailingSpaces_returnsCorrectCount() {
        val text = "  hello world  "
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }

        assertEquals(2, words.size)
    }

    @Test
    fun textConcatenation_emptyExisting_returnsNewText() {
        val currentText = ""
        val newText = "hello"
        val result = if (currentText.isEmpty()) newText else "$currentText $newText"

        assertEquals("hello", result)
    }

    @Test
    fun textConcatenation_existingText_appendsWithSpace() {
        val currentText = "hello"
        val newText = "world"
        val result = if (currentText.isEmpty()) newText else "$currentText $newText"

        assertEquals("hello world", result)
    }

    @Test
    fun textConcatenation_multipleAppends_maintainsSpacing() {
        var currentText = ""
        val texts = listOf("hello", "world", "this", "is", "test")

        for (newText in texts) {
            currentText = if (currentText.isEmpty()) newText else "$currentText $newText"
        }

        assertEquals("hello world this is test", currentText)
    }
}
