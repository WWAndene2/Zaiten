package com.zaiten.mixer

import android.Manifest
import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.zaiten.mixer.core.capture.ActiveSessionWatcher
import com.zaiten.mixer.core.capture.AudioCaptureService

class MainActivity : ComponentActivity() {
    // Le système redemande l'autorisation de capture à chaque session (§5).
    private val projectionRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                AudioCaptureService.start(this, data)
            }
        }

    private val permissionsRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.RECORD_AUDIO] == true) {
                val manager = getSystemService(MediaProjectionManager::class.java)
                projectionRequest.launch(manager.createScreenCaptureIntent())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val capturing by AudioCaptureService.running.collectAsState()
                AppShell(capturing = capturing, onStartCapture = ::requestCapture)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // L'accès aux notifications s'accorde hors de l'appli : on relance la détection au retour.
        ActiveSessionWatcher.refresh(this)
    }

    private fun requestCapture() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsRequest.launch(permissions.toTypedArray())
    }
}
