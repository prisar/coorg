package com.prisar.coorg

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.roundToInt

data class RecordingState(
    val isRecording: Boolean = false,
    val recognizedText: String = "",
    val wordCount: Int = 0,
    val recordingDurationSeconds: Int = 0,
    val wordsPerMinute: Int = 0,
    val error: String? = null
)

class RecordingViewModel : ViewModel() {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var recordingStartTime: Long = 0
    private var audioFile: File? = null

    fun startRecording(context: Context) {
        try {
            val cacheDir = context.cacheDir
            audioFile = File.createTempFile("recording", ".3gp", cacheDir)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            recordingStartTime = System.currentTimeMillis()
            _state.value = _state.value.copy(
                isRecording = true,
                error = null,
                recognizedText = "",
                wordCount = 0,
                wordsPerMinute = 0
            )

            startSpeechRecognition(context)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Recording failed: ${e.message}",
                isRecording = false
            )
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            speechRecognizer?.destroy()
            speechRecognizer = null

            val durationSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
            val currentWordCount = _state.value.wordCount

            val wpm = if (durationSeconds > 0) {
                ((currentWordCount.toFloat() / durationSeconds) * 60).roundToInt()
            } else {
                0
            }

            _state.value = _state.value.copy(
                isRecording = false,
                recordingDurationSeconds = durationSeconds,
                wordsPerMinute = wpm
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Stop recording failed: ${e.message}",
                isRecording = false
            )
        }
    }

    private fun startSpeechRecognition(context: Context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(error = "Speech recognition not available")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (_state.value.isRecording) {
                        restartSpeechRecognition(context)
                    }
                }

                override fun onError(error: Int) {
                    if (_state.value.isRecording) {
                        restartSpeechRecognition(context)
                    }
                }

                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val newText = matches[0]
                        val currentText = _state.value.recognizedText
                        val updatedText = if (currentText.isEmpty()) newText else "$currentText $newText"
                        val words = updatedText.split("\\s+".toRegex()).filter { it.isNotBlank() }

                        _state.value = _state.value.copy(
                            recognizedText = updatedText,
                            wordCount = words.size
                        )
                    }

                    if (_state.value.isRecording) {
                        restartSpeechRecognition(context)
                    }
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun restartSpeechRecognition(context: Context) {
        try {
            speechRecognizer?.stopListening()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
        speechRecognizer?.destroy()
    }
}
