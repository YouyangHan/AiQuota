package com.hanyo.apiquota.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.hanyo.apiquota.appContainer
import com.hanyo.apiquota.data.local.AccountStore
import com.hanyo.apiquota.data.model.WidgetQuotaItem
import com.hanyo.apiquota.data.remote.QuotaFetcher
import com.hanyo.apiquota.repository.QuotaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 小组件刷新入口：拉取最新额度 -> 写本地缓存 + 写各实例的 Glance state -> 触发重绘。
 * 网络请求集中在这里（Worker / ActionCallback / 后台协程），provideGlance 只负责渲染。
 *
 * 为什么写 state：Glance 的 update() 在目标会话存活时只会触发 state 刷新 + 重组，
 * 不会重跑 provideGlance；内容区读取 LocalState，因此把新数据写进 PreferencesGlanceStateDefinition
 * 后调 update()，界面即会刷新（冷启动无存活会话时 update() 会完整重跑 provideGlance，同样成立）。
 */
object WidgetUpdater {
    private const val TAG = "QuotaWidget"

    /** triggerId：发起刷新的实例（刷新按钮），优先更新；null 表示后台任务，更新全部实例。 */
    suspend fun refresh(context: Context, triggerId: GlanceId? = null) {
        val appCtx = context.applicationContext
        val repo = safeRepository(appCtx)
        if (repo == null) {
            Log.e(TAG, "refresh: repository unavailable")
            return
        }
        val cache = safeCache(appCtx)

        val items = withContext(Dispatchers.IO) {
            runCatching { repo.loadAndFetch() }.getOrNull()
        }
        if (items == null) {
            Log.e(TAG, "refresh: fetch failed, keep old data")
            return
        }
        Log.d(TAG, "refresh: fetched items=${items.size}")

        // 本地缓存仍是冷启动数据源（新实例尚无 state）
        runCatching { cache?.save(items) }

        val raw = QuotaGlanceWidget.encodeItems(items)
        if (raw == null) {
            Log.e(TAG, "refresh: encode failed")
            return
        }
        val now = System.currentTimeMillis()

        val ids = runCatching {
            GlanceAppWidgetManager(appCtx).getGlanceIds(QuotaGlanceWidget::class.java)
        }.getOrDefault(emptyList())
        Log.d(TAG, "refresh: glanceIds=${ids.size}")

        val ordered = triggerId?.let { listOf(it) + ids.filter { id -> id != it } } ?: ids
        ordered.forEach { glanceId ->
            runCatching {
                updateAppWidgetState(appCtx, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[QuotaGlanceWidget.STATE_ITEMS] = raw
                        this[QuotaGlanceWidget.STATE_TIME] = now
                    }
                }
                QuotaGlanceWidget().update(appCtx, glanceId)
                Log.d(TAG, "refresh: updated $glanceId")
            }.onFailure { Log.e(TAG, "refresh: update failed for $glanceId", it) }
        }
    }

    private fun safeCache(ctx: Context): WidgetCache? = runCatching {
        ctx.appContainer().widgetCache
    }.getOrNull() ?: runCatching { WidgetCache(ctx) }.getOrNull()

    private fun safeRepository(ctx: Context): QuotaRepository? = runCatching {
        ctx.appContainer().repository
    }.getOrNull() ?: runCatching {
        QuotaRepository(AccountStore(ctx), QuotaFetcher())
    }.getOrNull()
}
