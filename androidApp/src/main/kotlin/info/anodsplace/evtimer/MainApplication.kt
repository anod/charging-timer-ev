package info.anodsplace.evtimer

import android.app.Application
import android.app.Activity
import android.os.Bundle
import info.anodsplace.evtimer.data.ChargingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class MainApplication : Application(), KoinComponent {
    lateinit var chargingSessionController: ChargingSessionController
        private set
    lateinit var chargingCompletionController: ChargingCompletionController
        private set

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@MainApplication)
            androidLogger()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val chargingService = get<ChargingService>()
        chargingCompletionController = ChargingCompletionController(this, chargingService, scope)
        chargingSessionController = ChargingSessionController(this, chargingService, scope)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                chargingSessionController.onActivityResumed()
                chargingCompletionController.onActivityResumed()
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) {
                chargingCompletionController.onActivityPaused()
            }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
