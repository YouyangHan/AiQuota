package com.hanyo.apiquota.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hanyo.apiquota.data.local.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * 小组件自动刷新调度。
 *
 * WorkManager 的周期任务最小间隔是 15 分钟，无法满足 1 / 5 分钟的需求，
 * 因此按间隔分流：
 *  - >= 15 分钟：WorkManager（省电、带联网约束、重启后自动恢复）
 *  - <  15 分钟：AlarmManager 重复闹钟，触发时投递一次性 Worker 去拉数据
 *
 * 注意：小于 15 分钟的间隔在息屏 / Doze / 省电模式下会被系统延后，属于系统限制。
 */
object RefreshScheduler {
    /** WorkManager 周期任务的最小间隔（系统强制下限）。 */
    const val MIN_WORK_MINUTES = 15L

    private const val REQUEST_CODE = 21001

    /** 设置变更时调用：强制按当前设置重建调度。 */
    fun schedule(context: Context) = apply(context, force = true)

    /**
     * 应用启动 / 开机时调用：不打断已有调度。
     * 否则每次打开 App 都会把 1 分钟闹钟重新计时，导致它永远等不到触发。
     */
    fun ensureScheduled(context: Context) = apply(context, force = false)

    fun cancelAll(context: Context) {
        val ctx = context.applicationContext
        runCatching { WorkManager.getInstance(ctx).cancelUniqueWork(QuotaWidgetWorker.NAME) }
        cancelAlarm(ctx)
    }

    /** 闹钟触发后由接收器调用，续排下一次。 */
    fun scheduleNextAlarm(context: Context) {
        val ctx = context.applicationContext
        val settings = SettingsStore(ctx)
        if (!settings.widgetAutoRefresh) return
        val minutes = settings.refreshIntervalMinutes.coerceAtLeast(1)
        if (minutes >= MIN_WORK_MINUTES) return
        val intervalMs = minutes * 60_000L
        val now = System.currentTimeMillis()
        val stored = settings.nextAlarmDue
        // 沿固定时间轴续排：系统每次最多延后一个窗口，按「上次计划时间 + 间隔」推进，
        // 整体节奏仍接近设定间隔；若已落后超过一个周期（如息屏很久），则重置为 now + 间隔，避免补跑风暴。
        val base = if (stored <= 0L || stored < now - intervalMs) now else stored
        scheduleAlarm(ctx, minutes, base + intervalMs)
    }

    private fun apply(context: Context, force: Boolean) {
        val ctx = context.applicationContext
        val settings = SettingsStore(ctx)
        if (!settings.widgetAutoRefresh) {
            cancelAll(ctx)
            return
        }
        val minutes = settings.refreshIntervalMinutes.coerceAtLeast(1)
        if (minutes >= MIN_WORK_MINUTES) {
            cancelAlarm(ctx)
            enqueueWork(
                ctx,
                minutes,
                if (force) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
            )
        } else {
            cancelWork(ctx)
            // 单次闹钟：若已排上且尚未到点，按原定时间幂等重排（不重置计时）；
            // 这样即使闹钟被系统清理（如强行停止），下次启动 App 也能补回
            val stored = settings.nextAlarmDue
            val resumeAt = if (!force && stored > System.currentTimeMillis() + 5_000L) stored else null
            scheduleAlarm(ctx, minutes, resumeAt)
        }
    }

    private fun enqueueWork(ctx: Context, minutes: Long, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<QuotaWidgetWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            QuotaWidgetWorker.NAME,
            policy,
            request
        )
    }

    private fun cancelWork(ctx: Context) {
        runCatching { WorkManager.getInstance(ctx).cancelUniqueWork(QuotaWidgetWorker.NAME) }
    }

    private fun scheduleAlarm(ctx: Context, minutes: Long, at: Long? = null) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(ctx, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        am.cancel(pi)
        val triggerAt = at ?: (System.currentTimeMillis() + minutes * 60_000L)
        // 单次闹钟 + 接收器续排：比 setInexactRepeating 的 75% 窗口更准时。
        // 无需精确闹钟权限；Doze 下由系统择机触发（约 9 分钟一次上限）。
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        SettingsStore(ctx).nextAlarmDue = triggerAt
    }

    private fun cancelAlarm(ctx: Context) {
        alarmIntent(ctx, PendingIntent.FLAG_IMMUTABLE)?.let { pi ->
            runCatching { ctx.getSystemService(AlarmManager::class.java)?.cancel(pi) }
        }
        SettingsStore(ctx).nextAlarmDue = 0L
    }

    private fun alarmIntent(ctx: Context, extraFlags: Int): PendingIntent? {
        val intent = Intent(ctx, RefreshAlarmReceiver::class.java)
            .setAction(RefreshAlarmReceiver.ACTION_ALARM_REFRESH)
        return PendingIntent.getBroadcast(
            ctx,
            REQUEST_CODE,
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
