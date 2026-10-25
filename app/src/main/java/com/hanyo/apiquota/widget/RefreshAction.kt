package com.hanyo.apiquota.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** 小组件上的「刷新」按钮回调：重新拉取额度并刷新（写入 state 触发重组）。 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WidgetUpdater.refresh(context, glanceId)
    }
}
