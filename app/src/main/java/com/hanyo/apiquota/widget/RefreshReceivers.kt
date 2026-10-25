package com.hanyo.apiquota.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.hanyo.apiquota.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 短间隔（1 / 5 分钟）闹钟触发。
 *
 * 这里直接拉取，而不是转交 WorkManager：短间隔场景下 WorkManager 的调度与联网校验会带来
 * 额外延迟（部分设备的网络长期处于未验证状态时甚至不触发）。goAsync() 给约 10 秒窗口，
 * 用 withTimeout 兜住，超时才降级给 WorkManager 重试。
 */
class RefreshAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        if (!SettingsStore(ctx).widgetAutoRefresh) return
        // 先排下一次，避免进程在拉取途中被杀导致调度中断
        RefreshScheduler.scheduleNextAlarm(ctx)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching {
                withTimeout(REFRESH_TIMEOUT_MS) {
                    val ids = GlanceAppWidgetManager(ctx)
                        .getGlanceIds(QuotaGlanceWidget::class.java)
                    if (ids.isEmpty()) return@withTimeout true
                    Log.d(TAG, "alarm fired, refresh ${ids.size} widget(s)")
                    WidgetUpdater.refresh(ctx)
                    true
                }
            }.getOrDefault(false)
            if (!ok) {
                Log.w(TAG, "alarm refresh timed out, fallback to WorkManager")
                QuotaWidgetWorker.enqueueImmediate(ctx)
            }
            pending.finish()
        }
    }

    companion object {
        private const val TAG = "QuotaWidget"
        private const val REFRESH_TIMEOUT_MS = 8_000L
        const val ACTION_ALARM_REFRESH = "com.hanyo.apiquota.action.ALARM_REFRESH"
    }
}

/** 开机 / 应用更新后恢复自动刷新调度（AlarmManager 的闹钟在重启后会丢失）。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> RefreshScheduler.ensureScheduled(context.applicationContext)
        }
    }
}
