package com.hanyo.apiquota.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class QuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = QuotaGlanceWidget()

    /** 第一个组件实例被添加到桌面时，立即触发一次数据拉取。 */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        QuotaWidgetWorker.enqueueImmediate(context)
    }
}
