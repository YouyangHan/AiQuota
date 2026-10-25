package com.hanyo.apiquota.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.hanyo.apiquota.data.model.QuotaResult
import com.hanyo.apiquota.data.remote.Providers
import com.hanyo.apiquota.ui.QuotaViewModel
import com.hanyo.apiquota.widget.WidgetIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: QuotaViewModel,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val results by viewModel.results.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val lastUpdated by viewModel.lastUpdated.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 额度管家") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "添加账号") }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (loading) "刷新中…" else lastUpdatedText(lastUpdated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }

            if (accounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有账号，点右下角 + 添加", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(accounts, key = { it.id }) { acc ->
                        AccountCard(
                            account = acc,
                            result = results[acc.id],
                            onDelete = { viewModel.deleteAccount(acc.id) }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: com.hanyo.apiquota.data.model.Account,
    result: QuotaResult?,
    onDelete: () -> Unit
) {
    val providerName = Providers.byId(account.providerId).displayName
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetIcons.drawableFor(account.providerId)?.let { resId ->
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(providerName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (account.label.isNotEmpty()) {
                        Text(
                            account.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            when (result) {
                is QuotaResult.Success -> {
                    Text(
                        result.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (result.low) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        result.secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (result.lines.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        result.lines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    line.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    line.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                if (line.note.isNotEmpty()) {
                                    Text(
                                        " · ${line.note}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
                is QuotaResult.Failure -> {
                    Text("查询失败", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                    Text(
                        result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                null -> Text("—", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun lastUpdatedText(ts: Long): String {
    if (ts == 0L) return "尚未更新"
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return "更新于 ${sdf.format(Date(ts))}"
}
