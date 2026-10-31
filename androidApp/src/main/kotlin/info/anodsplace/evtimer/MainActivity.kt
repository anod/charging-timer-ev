package info.anodsplace.evtimer

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import info.anodsplace.evtimer.data.ChargingService
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val chargingService: ChargingService by inject()
    private var promptedSessionId: Long? = null
    private var promotionDialog: AlertDialog? = null
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            (application as MainApplication).chargingSessionController.onActivityResumed()
            offerLiveUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                chargingService.status.collect { status ->
                    if (status.isLoading || !status.isRunning || promptedSessionId == status.startTime) return@collect
                    promptedSessionId = status.startTime
                    if ((application as MainApplication).chargingSessionController.isDismissed(status.startTime)) return@collect
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        offerLiveUpdates()
                    }
                }
            }
        }
    }

    private fun offerLiveUpdates() {
        if (!chargingService.status.value.isRunning || Build.VERSION.SDK_INT < 36 || ChargingNotifier(this).canPromote()) return
        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        promotionDialog = AlertDialog.Builder(this)
            .setTitle(R.string.charging_live_updates)
            .setMessage(R.string.charging_live_updates_message)
            .setPositiveButton(R.string.charging_live_updates_settings) { _, _ -> startActivity(settingsIntent) }
            .setNegativeButton(R.string.charging_not_now, null)
            .show()
    }

    override fun onDestroy() {
        promotionDialog?.dismiss()
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
