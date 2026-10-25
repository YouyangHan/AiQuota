package com.hanyo.apiquota.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * 普通（非敏感）设置项存储：小组件自动刷新开关与刷新间隔。
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_quota_settings", Context.MODE_PRIVATE)

    var refreshIntervalMinutes: Long
        get() = prefs.getLong(KEY_INTERVAL, 30)
        set(value) = prefs.edit().putLong(KEY_INTERVAL, value).apply()

    var widgetAutoRefresh: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    /** 下一次短间隔闹钟的触发时间（毫秒），用于判断闹钟是否已排上。 */
    var nextAlarmDue: Long
        get() = prefs.getLong(KEY_NEXT_DUE, 0L)
        set(value) = prefs.edit().putLong(KEY_NEXT_DUE, value).apply()

    companion object {
        private const val KEY_INTERVAL = "refresh_interval_min"
        private const val KEY_AUTO = "widget_auto_refresh"
        private const val KEY_NEXT_DUE = "next_alarm_due"
    }
}
