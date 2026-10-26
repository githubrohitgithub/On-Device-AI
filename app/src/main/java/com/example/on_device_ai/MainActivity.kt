package com.example.on_device_ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.on_device_ai.ui.OnDeviceApp
import com.example.on_device_ai.ui.theme.OnDeviceAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnDeviceAITheme {
                OnDeviceApp()
            }
        }
    }
}
