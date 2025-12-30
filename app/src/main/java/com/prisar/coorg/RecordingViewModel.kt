package com.prisar.coorg

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

enum class RecordingMode {
    VOICE,
    CALL
}

data class CallRecording(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val duration: Int,
    val filePath: String,
    val phoneNumber: String?,
    val transcription: String,
    val wordCount: Int,
    val averageWpm: Int,
    val wpmDataPoints: List<Pair<Int, Int>>
)

data class RecordingState(
    val recordingMode: RecordingMode = RecordingMode.VOICE,
    val isRecording: Boolean = false,
    val recognizedText: String = "",
    val wordCount: Int = 0,
    val recordingDurationSeconds: Int = 0,
    val wordsPerMinute: Int = 0,
    val wpmDataPoints: List<Pair<Int, Int>> = emptyList(),
    val error: String? = null,
    val isInCall: Boolean = false,
    val callRecordings: List<CallRecording> = emptyList()
)

class RecordingViewModel : ViewModel() {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var recordingStartTime: Long = 0
    private var audioFile: File? = null
    private var wpmSamplingJob: Job? = null
    private var repository: CallRecordingRepository? = null
    private var currentPhoneNumber: String? = null
    private var isRestarting = false
    private var audioManager: AudioManager? = null

    fun startRecording(context: Context, isCallRecording: Boolean = false) {
        try {
            if (repository == null) {
                repository = CallRecordingRepository(context)
            }

            audioFile = if (isCallRecording) {
                repository?.createRecordingFile(System.currentTimeMillis(), currentPhoneNumber)
            } else {
                File.createTempFile("recording", ".3gp", context.cacheDir)
            }

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                val audioSource = if (isCallRecording) {
                    try {
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION
                    } catch (e: Exception) {
                        MediaRecorder.AudioSource.MIC
                    }
                } else {
                    MediaRecorder.AudioSource.MIC
                }

                setAudioSource(audioSource)

                if (isCallRecording) {
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                }

                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            recordingStartTime = System.currentTimeMillis()
            isRestarting = false
            _state.value = _state.value.copy(
                isRecording = true,
                error = null,
                recognizedText = "",
                wordCount = 0,
                wordsPerMinute = 0,
                wpmDataPoints = emptyList()
            )

            startSpeechRecognition(context)
            startWpmSampling()
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Recording failed: ${e.message}",
                isRecording = false
            )
        }
    }

    fun stopRecording(saveCallRecording: Boolean = false) {
        try {
            isRestarting = false
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            wpmSamplingJob?.cancel()
            wpmSamplingJob = null

            speechRecognizer?.destroy()
            speechRecognizer = null

            val durationSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
            val currentWordCount = _state.value.wordCount

            val wpm = if (durationSeconds > 0) {
                ((currentWordCount.toFloat() / durationSeconds) * 60).roundToInt()
            } else {
                0
            }

            if (saveCallRecording && audioFile != null) {
                val callRecording = CallRecording(
                    timestamp = recordingStartTime,
                    duration = durationSeconds,
                    filePath = audioFile!!.absolutePath,
                    phoneNumber = currentPhoneNumber,
                    transcription = _state.value.recognizedText,
                    wordCount = currentWordCount,
                    averageWpm = wpm,
                    wpmDataPoints = _state.value.wpmDataPoints
                )
                repository?.saveRecording(callRecording)

                val updatedRecordings = repository?.loadAllRecordings() ?: emptyList()
                _state.value = _state.value.copy(callRecordings = updatedRecordings)
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

    private fun calculateCurrentWpm(): Int {
        val currentDuration = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
        val currentWordCount = _state.value.wordCount
        return if (currentDuration > 0) {
            ((currentWordCount.toFloat() / currentDuration) * 60).roundToInt()
        } else {
            0
        }
    }

    private fun startWpmSampling() {
        wpmSamplingJob = viewModelScope.launch {
            while (_state.value.isRecording) {
                delay(3000)

                if (_state.value.isRecording) {
                    val currentDuration = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                    val currentWpm = calculateCurrentWpm()

                    val updatedDataPoints = _state.value.wpmDataPoints + (currentDuration to currentWpm)

                    _state.value = _state.value.copy(
                        wpmDataPoints = updatedDataPoints,
                        recordingDurationSeconds = currentDuration,
                        wordsPerMinute = currentWpm
                    )
                }
            }
        }
    }

    private fun startSpeechRecognition(context: Context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(error = "Speech recognition not available")
            return
        }

        if (audioManager == null) {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    viewModelScope.launch {
                        delay(1000)
                        audioManager?.apply {
                            adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                            adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                            adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                        }
                    }
                }
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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }

        audioManager?.apply {
            adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun restartSpeechRecognition(context: Context) {
        if (isRestarting) return

        try {
            isRestarting = true
            speechRecognizer?.stopListening()

            viewModelScope.launch {
                delay(100)
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                        putExtra("android.speech.extra.DICTATION_MODE", true)
                    }
                    audioManager?.apply {
                        adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                        adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                        adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                    }
                    speechRecognizer?.startListening(intent)
                } finally {
                    isRestarting = false
                }
            }
        } catch (e: Exception) {
            isRestarting = false
        }
    }

    fun switchMode(mode: RecordingMode) {
        _state.value = _state.value.copy(recordingMode = mode)
    }

    fun onCallStarted(context: Context, phoneNumber: String?) {
        if (repository == null) {
            repository = CallRecordingRepository(context)
        }
        currentPhoneNumber = phoneNumber
        _state.value = _state.value.copy(
            isInCall = true,
            recordingMode = RecordingMode.CALL
        )
        startRecording(context, isCallRecording = true)
    }

    fun onCallEnded() {
        _state.value = _state.value.copy(isInCall = false)
        if (_state.value.isRecording && _state.value.recordingMode == RecordingMode.CALL) {
            stopRecording(saveCallRecording = true)
        }
    }

    fun loadCallRecordings(context: Context) {
        if (repository == null) {
            repository = CallRecordingRepository(context)
        }
        val recordings = repository?.loadAllRecordings() ?: emptyList()
        _state.value = _state.value.copy(callRecordings = recordings)
    }

    fun deleteRecording(id: String) {
        val success = repository?.deleteRecording(id) ?: false
        if (success) {
            val updatedRecordings = _state.value.callRecordings.filter { it.id != id }
            _state.value = _state.value.copy(callRecordings = updatedRecordings)
        }
    }

    override fun onCleared() {
        super.onCleared()
        wpmSamplingJob?.cancel()
        mediaRecorder?.release()
        speechRecognizer?.destroy()
    }
}
