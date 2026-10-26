package com.example.on_device_ai.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.on_device_ai.ui.chat.ChatRoute
import com.example.on_device_ai.ui.setup.ModelSetupRoute

private object Routes {
    const val SETUP = "setup"
    const val CHAT = "chat"
}

@Composable
fun OnDeviceApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.SETUP) {
        composable(Routes.SETUP) {
            ModelSetupRoute(onStartChat = { navController.navigate(Routes.CHAT) })
        }
        composable(Routes.CHAT) {
            ChatRoute(onBack = { navController.popBackStack() })
        }
    }
}
