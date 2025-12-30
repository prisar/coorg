package com.prisar.coorg

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class CallRecordingsMetadata(
    val recordings: List<CallRecording> = emptyList()
)

class CallRecordingRepository(private val context: Context) {
    private val gson = Gson()
    private val recordingsDir = File(context.filesDir, "call_recordings")
    private val metadataFile = File(context.filesDir, "call_recordings_metadata.json")

    init {
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
    }

    fun saveRecording(callRecording: CallRecording): Boolean {
        return try {
            val recordings = loadAllRecordings().toMutableList()
            recordings.add(callRecording)

            val metadata = CallRecordingsMetadata(recordings)
            val json = gson.toJson(metadata)
            metadataFile.writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun loadAllRecordings(): List<CallRecording> {
        return try {
            if (!metadataFile.exists()) {
                return emptyList()
            }

            val json = metadataFile.readText()
            val type = object : TypeToken<CallRecordingsMetadata>() {}.type
            val metadata: CallRecordingsMetadata = gson.fromJson(json, type)
            metadata.recordings.filter { File(it.filePath).exists() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun deleteRecording(id: String): Boolean {
        return try {
            val recordings = loadAllRecordings()
            val recordingToDelete = recordings.find { it.id == id } ?: return false

            File(recordingToDelete.filePath).delete()

            val updatedRecordings = recordings.filter { it.id != id }
            val metadata = CallRecordingsMetadata(updatedRecordings)
            val json = gson.toJson(metadata)
            metadataFile.writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getRecordingById(id: String): CallRecording? {
        return loadAllRecordings().find { it.id == id }
    }

    fun createRecordingFile(timestamp: Long, phoneNumber: String?): File {
        val sanitizedNumber = phoneNumber?.replace(Regex("[^0-9+]"), "") ?: "unknown"
        val fileName = "call_recording_${timestamp}_${sanitizedNumber}.m4a"
        return File(recordingsDir, fileName)
    }
}
