package ai.ebbflow.baseline.app.ui

import android.os.Bundle
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

/**
 * Single-activity Compose host. Requests the Bluetooth (and, on Android 13+,
 * notification) runtime permissions before asking the service to start streaming.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
