package com.prisar.coorg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.prisar.coorg.ui.theme.CoorgTheme

class MainActivity : ComponentActivity() {
    private val viewModel: RecordingViewModel by viewModels()
    private var callStateMonitor: CallStateMonitor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        callStateMonitor = CallStateMonitor(this, object : CallStateCallback {
            override fun onCallStarted(phoneNumber: String?) {
                viewModel.onCallStarted(this@MainActivity, phoneNumber)
            }

            override fun onCallEnded() {
                viewModel.onCallEnded()
            }
        })

        setContent {
            CoorgTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecordingScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        callStateMonitor?.register()
    }

    override fun onPause() {
        super.onPause()
        callStateMonitor?.unregister()
    }
}