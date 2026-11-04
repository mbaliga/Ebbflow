package ai.ebbflow.baseline.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.ebbflow.baseline.app.bluetooth.BluetoothPermissions
import ai.ebbflow.baseline.app.ui.theme.EbbflowTheme
import ai.ebbflow.baseline.app.widget.WidgetDestination
import ai.ebbflow.baseline.app.widget.WidgetIntents
import ai.ebbflow.baseline.app.widget.WidgetUpdater

/**
 * Single-activity Compose host. Requests the Bluetooth (and, on Android 13+,
 * notification) runtime permissions before asking the service to start streaming.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleWidgetTap(intent)
            // Widgets may still show a session a killed or force-stopped process left behind.
            WidgetUpdater.refreshInBackground(this)
        }
        setContent {
            val vm: StreamViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val recent by vm.recent.collectAsStateWithLifecycle()

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                if (grants.values.all { it }) vm.start()
            }

            EbbflowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StreamScreen(
                        state = state,
                        persistedCount = recent.size,
                        onStart = {
                            if (BluetoothPermissions.allGranted(this)) {
                                vm.start()
                            } else {
                                permissionLauncher.launch(BluetoothPermissions.required())
                            }
                        },
                        onStop = vm::stop,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetTap(intent)
    }

    /**
     * A home-screen widget tap. The acquisition screen is the app's only screen, so taps land
     * here; "Bluetooth settings" opens the system pairing screen on top, so Back returns to
     * the Start button. Nothing here starts a capture: the user still taps Start.
     */
    private fun handleWidgetTap(intent: Intent?) {
        // A relaunch from Recents replays the original intent; it is not a new tap.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        when (WidgetIntents.destinationOf(intent)) {
            WidgetDestination.BLUETOOTH_SETTINGS -> runCatching {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            WidgetDestination.CAPTURE, null -> Unit
        }
        intent.removeExtra(WidgetIntents.EXTRA_DESTINATION)
    }
}
