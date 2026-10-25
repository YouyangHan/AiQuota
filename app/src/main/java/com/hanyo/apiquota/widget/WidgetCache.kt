package com.hanyo.apiquota.widget

import android.content.Context
import android.content.SharedPreferences
import com.hanyo.apiquota.data.model.WidgetQuotaItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 小组件本地缓存：保存最近一次拉取到的额度快照与时间戳。
 * 用普通 SharedPreferences（非加密），确保 widget 在任何上下文都能瞬时读取，
 * 渲染不依赖网络、不依赖 Android Keystore，避免首屏空白。
 */
class WidgetCache(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<WidgetQuotaItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(WidgetQuotaItem.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    /** 写入最新数据并记录更新时间。 */
    fun save(items: List<WidgetQuotaItem>) {
        val raw = json.encodeToString(ListSerializer(WidgetQuotaItem.serializer()), items)
        prefs.edit()
            .putString(KEY_ITEMS, raw)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    fun lastUpdated(): Long = prefs.getLong(KEY_TIME, 0L)

    companion object {
        private const val NAME = "widget_cache"
        private const val KEY_ITEMS = "items"
        private const val KEY_TIME = "updated_at"
    }
}
