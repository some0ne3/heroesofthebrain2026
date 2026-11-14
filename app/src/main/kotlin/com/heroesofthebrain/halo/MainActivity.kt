package com.heroesofthebrain.halo

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.core.content.ContextCompat
import com.heroesofthebrain.halo.service.StartPermissions
import com.heroesofthebrain.halo.ui.MainScreen
import com.heroesofthebrain.halo.ui.MainViewModel
import com.heroesofthebrain.halo.ui.SourceKind
import com.neurotec.brainaccess.transport.BlePermissions

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // Re-checks the real grants instead of trusting the result map, which leaves out anything granted earlier.
    private val startPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (StartPermissions.canStart(headset(), BlePermissions.required, ::granted)) vm.start() else vm.onPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                MainScreen(vm, onConnect = ::connect, onAllowBackground = ::requestBatteryExemption)
            }
        }
    }

    // Also runs when the user comes back from the battery settings screen.
    override fun onResume() {
        super.onResume()
        vm.setBatteryExempt(getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: true)
    }

    // BlePermissions.required is the SDK's list for the running OS: SCAN+CONNECT on 31+, fine location on 30-.
    // They must be granted before HaloService starts, because Android 14+ refuses a connectedDevice service without them.
    private fun connect() {
        val missing = StartPermissions.toRequest(
            headset = headset(),
            sdk = Build.VERSION.SDK_INT,
            ble = BlePermissions.required,
            granted = ::granted,
            askNotifications = !vm.notificationsAsked,
        )
        if (missing.isEmpty()) {
            vm.start()
            return
        }
        if (StartPermissions.POST_NOTIFICATIONS in missing) vm.markNotificationsAsked()
        startPermissions.launch(missing.toTypedArray())
    }

    // The direct exemption prompt needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; a hands-free BLE remote is one of the
    // cases it exists for. Some OEM builds have no handler for it, so fall back to the full list.
    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        try {
            startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun headset() = vm.ui.value.sourceKind == SourceKind.DEVICE

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
