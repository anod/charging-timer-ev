package info.anodsplace.evtimer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.permissions.AppPermission
import info.anodsplace.permissions.AppPermissions
import info.anodsplace.permissions.toRequestInput
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install SplashScreen (must be first before super.setContent usage pattern)
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App()
            ChargingNotificationUpdates(this@MainActivity)
        }
    }
}

@Composable
fun ChargingNotificationUpdates(activity: MainActivity) {
    val context = LocalContext.current
    val chargingService = koinInject<ChargingService>()
    val lastRunning = remember { mutableStateOf<Boolean?>(null) }
    val permissionRequested = remember { mutableStateOf(false) }
    val requestNotifications = rememberLauncherForActivityResult(contract = AppPermissions.Request()) { result ->
        val granted = result[AppPermission.PostNotification.value] == true
        if (granted) {
            context.startForegroundService(Intent(context, ChargingForegroundService::class.java))
        }
    }
    LaunchedEffect(Unit) {
        chargingService.status.collectLatest { status ->
            if (lastRunning.value != status.isRunning) {
                if (status.isRunning) {
                    val needsPermission = AppPermissions.shouldShowMessage(activity, AppPermission.PostNotification)
                    if (needsPermission && !permissionRequested.value) {
                        permissionRequested.value = true
                        requestNotifications.launch(AppPermission.PostNotification.toRequestInput())
                    } else {
                        context.startForegroundService(Intent(context, ChargingForegroundService::class.java))
                    }
                } else {
                    context.stopService(Intent(context, ChargingForegroundService::class.java))
                    permissionRequested.value = false
                }
                lastRunning.value = status.isRunning
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
