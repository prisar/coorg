package com.prisar.coorg

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )
    )

    LaunchedEffect(state.recordingMode) {
        if (state.recordingMode == RecordingMode.CALL) {
            viewModel.loadCallRecordings(context)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (!permissionsState.allPermissionsGranted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Permissions Required",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This app needs microphone and phone state permissions to record voice and calls",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }
        } else {
            TabRow(
                selectedTabIndex = when (state.recordingMode) {
                    RecordingMode.VOICE -> 0
                    RecordingMode.CALL -> 1
                }
            ) {
                Tab(
                    selected = state.recordingMode == RecordingMode.VOICE,
                    onClick = { viewModel.switchMode(RecordingMode.VOICE) },
                    text = { Text("Voice") }
                )
                Tab(
                    selected = state.recordingMode == RecordingMode.CALL,
                    onClick = {
                        viewModel.switchMode(RecordingMode.CALL)
                        viewModel.loadCallRecordings(context)
                    },
                    text = { Text("Call") }
                )
            }

            when (state.recordingMode) {
                RecordingMode.VOICE -> VoiceRecordingTab(
                    state = state,
                    onStartRecording = { viewModel.startRecording(context) },
                    onStopRecording = { viewModel.stopRecording() }
                )
                RecordingMode.CALL -> CallRecordingTab(
                    state = state,
                    onDeleteRecording = { id -> viewModel.deleteRecording(id) }
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun WpmChart(dataPoints: List<Pair<Int, Int>>) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(dataPoints) {
        if (dataPoints.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(dataPoints.map { it.second })
                }
            }
        }
    }

    if (dataPoints.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "WPM Over Time",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(),
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}
