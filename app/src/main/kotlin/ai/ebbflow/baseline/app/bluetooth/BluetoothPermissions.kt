package ai.ebbflow.baseline.app.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permissions the streaming flow needs, which differ by API level:
 *  - API 31+ (S): BLUETOOTH_SCAN + BLUETOOTH_CONNECT (no location needed because
 *    SCAN is declared neverForLocation in the manifest).
 *  - API <= 30: ACCESS_FINE_LOCATION (required by the platform to BLE-scan at all);
 *    the legacy BLUETOOTH / BLUETOOTH_ADMIN perms are install-time, not runtime.
 *  - API 33+ (Tiramisu): POST_NOTIFICATIONS so the foreground-service notification
 *    is visible.
 */
object BluetoothPermissions {

    fun required(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun allGranted(context: Context): Boolean = required().all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun missing(context: Context): List<String> = required().filter { perm ->
        ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
    }
}
