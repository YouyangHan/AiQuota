package com.hanyo.apiquota.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hanyo.apiquota.AppContainer
import com.hanyo.apiquota.widget.RefreshScheduler
import com.hanyo.apiquota.widget.WidgetUpdater
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val settings = container.settings
    val scope = rememberCoroutineScope()
    var autoRefresh by remember { mutableStateOf(settings.widgetAutoRefresh) }
    var interval by remember { mutableStateOf(settings.refreshIntervalMinutes) }
    val intervals = listOf(1L, 5L, 15L, 30L, 60L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("小组件自动刷新", modifier = Modifier.weight(1f))
                Switch(
                    checked = autoRefresh,
                    onCheckedChange = {
                        autoRefresh = it
                        settings.widgetAutoRefresh = it
                        if (it) RefreshScheduler.schedule(container.appContext)
                        else RefreshScheduler.cancelAll(container.appContext)
                    }
                )
            }

            Text("刷新间隔（分钟）", style = MaterialTheme.typography.titleSmall)
            intervals.forEach { mins ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = interval == mins,
                        onClick = {
                            interval = mins
                            settings.refreshIntervalMinutes = mins
                            if (autoRefresh) RefreshScheduler.schedule(container.appContext)
                        }
                    )
                    Text("$mins 分钟")
                }
            }

            Button(onClick = {
                scope.launch {
                    WidgetUpdater.refresh(container.appContext)
                    Toast.makeText(container.appContext, "小组件已刷新", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("立即刷新小组件")
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "说明：1 / 5 分钟使用系统闹钟调度，息屏或省电模式下可能被延后；" +
                    "15 分钟及以上使用 WorkManager 后台任务。" +
                    "桌面小组件点一下即可手动刷新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                "DeepSeek、Kimi 使用官方余额接口；KimiCode、智谱 GLM 使用社区逆向接口，可能随官方调整而失效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
