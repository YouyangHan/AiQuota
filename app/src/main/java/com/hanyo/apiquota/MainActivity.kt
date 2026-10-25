package com.hanyo.apiquota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanyo.apiquota.ui.QuotaViewModel
import com.hanyo.apiquota.ui.screens.AddAccountScreen
import com.hanyo.apiquota.ui.screens.HomeScreen
import com.hanyo.apiquota.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val container = (applicationContext as App).container
            var screen by rememberSaveable { mutableStateOf("home") }
            androidx.compose.material3.MaterialTheme {
                when (screen) {
                    "add" -> AddAccountScreen(
                        container = container,
                        onDone = { screen = "home" },
                        onCancel = { screen = "home" }
                    )
                    "settings" -> SettingsScreen(
                        container = container,
                        onBack = { screen = "home" }
                    )
                    else -> {
                        val viewModel: QuotaViewModel = viewModel(
                            factory = QuotaViewModel.Factory(container)
                        )
                        HomeScreen(
                            viewModel = viewModel,
                            onAdd = { screen = "add" },
                            onSettings = { screen = "settings" }
                        )
                    }
                }
            }
        }
    }
}
