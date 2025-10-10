package com.anod.chargingtimer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.anod.chargingtimer.data.ChargingRepositoryImpl
import com.anod.chargingtimer.data.ChargingViewModel
import com.anod.chargingtimer.ui.ChargingTimerApp

fun main() = application {
    val viewModel = ChargingViewModel(ChargingRepositoryImpl())
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "EV Charging Timer",
        state = rememberWindowState(width = 400.dp, height = 700.dp)
    ) {
        ChargingTimerApp(viewModel)
    }
}
