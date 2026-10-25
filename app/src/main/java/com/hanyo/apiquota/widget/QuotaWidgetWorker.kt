package com.hanyo.apiquota.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * 周期性 / 一次性任务：拉取最新额度写入缓存并刷新所有小组件实例。
 * 真正的网络请求在 WidgetUpdater 中执行，这里只负责调度。
 */
class QuotaWidgetWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetUpdater.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        const val NAME = "quota_widget_refresh"
        private const val ONE_TIME_NAME = "quota_widget_refresh_once"

        /** 立即触发一次拉取（组件首次添加时调用）。 */
        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<QuotaWidgetWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            // KEEP：1 分钟闹钟高频触发时，不打断/取消正在进行的拉取
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
