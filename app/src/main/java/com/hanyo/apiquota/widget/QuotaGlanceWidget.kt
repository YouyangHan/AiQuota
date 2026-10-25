package com.hanyo.apiquota.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalState
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.hanyo.apiquota.appContainer
import com.hanyo.apiquota.data.model.QuotaLine
import com.hanyo.apiquota.data.model.WidgetQuotaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 桌面小组件（4x2，圆角卡片，无标题行）。
 * 设计原则：
 *  - 数据双通道：冷启动从 [WidgetCache] 读快照；运行期刷新经 PreferencesGlanceStateDefinition
 *    写入实例状态，内容区读取 LocalState —— Glance 会话感知状态变化后自动重组；
 *    （Glance 的 update() 在会话存活时只刷新 state，不会重跑 provideGlance，这是官方推荐的刷新模式）
 *  - provideGlance 立即渲染首帧，绝不阻塞网络；缓存为空时后台触发一次拉取；
 *  - 整块卡片可点击刷新（无独立按钮）；
 *  - 每个平台显示品牌图标、主额度与结构化明细行（如 KimiCode 的周用量 / 5h 频限 + 重置时间）；
 *  - 渲染层刻意保持简单：普通 Column + forEach（不用 LazyColumn/items，避免合成期崩溃导致整块空白），
 *    所有文字均显式指定明/暗双色，不依赖主题默认色；尺寸一律用 Dp，
 *    Glance 的 Modifier.padding 有 Int(dimen 资源) 重载，传整数会在渲染期抛 NotFoundException；
 *  - 任何异常都被兜底为可见文案，保证组件「永不空白」。
 */
class QuotaGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance: start")
        val cache = safeCache(context)
        val cacheItems = runCatching { cache?.load().orEmpty() }.getOrDefault(emptyList())
        val cacheTime = runCatching { cache?.lastUpdated() ?: 0L }.getOrDefault(0L)
        Log.d(TAG, "provideGlance: cached items=${cacheItems.size}, updatedAt=$cacheTime")

        // 首帧立即渲染（哪怕缓存为空，也显示引导文案），不在此处同步等网络；
        // 后台拉取成功后 WidgetUpdater.refresh 会写入 state 并 update()，触发重组。
        if (cacheItems.isEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastAutoRefresh.get() > AUTO_REFRESH_INTERVAL_MS) {
                lastAutoRefresh.set(now)
                Log.d(TAG, "provideGlance: trigger background refresh")
                CoroutineScope(Dispatchers.IO).launch { WidgetUpdater.refresh(context) }
            }
        }

        provideContent {
            // 运行期刷新写入的实例状态优先；无状态（新实例/从未刷新）回退到本地缓存快照。
            val state = readState(LocalState.current as? Preferences)
            val items = state?.first ?: cacheItems
            val updatedAt = state?.second ?: cacheTime
            WidgetContent(items, updatedAt)
        }
    }

    @Composable
    private fun WidgetContent(items: List<WidgetQuotaItem>, updatedAt: Long) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                // cornerRadius 只能作用在根元素上（Android 12+ 生效）
                .cornerRadius(22.dp)
                .background(Color(0xFFF7F8FA), Color(0xFF1C1C1E))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                // 整块卡片可点击：点一下即刷新
                .clickable(actionRunCallback<RefreshAction>())
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (items.isEmpty()) {
                    EmptyContent()
                } else {
                    items.forEachIndexed { index, item ->
                        if (index > 0) Divider()
                        WidgetItem(item)
                    }
                }
            }
            Text(
                text = footerText(updatedAt),
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(Color(0xFF8E8E93), Color(0xFF8E8E93))
                ),
                modifier = GlanceModifier.padding(top = 4.dp),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun EmptyContent() {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "暂无数据",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(Color(0xFF16181D), Color(0xFFF2F2F7))
                )
            )
            Text(
                text = "点按此处刷新，或先在 App 中添加账号",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = ColorProvider(Color(0xFF70737A), Color(0xFF9A9AA1))
                ),
                modifier = GlanceModifier.padding(top = 2.dp)
            )
        }
    }

    @Composable
    private fun WidgetItem(item: WidgetQuotaItem) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetIcons.drawableFor(item.providerId)?.let { resId ->
                Image(
                    provider = ImageProvider(resId),
                    contentDescription = item.providerName,
                    modifier = GlanceModifier.size(22.dp)
                )
            }
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(start = if (item.providerId.isNotEmpty()) 8.dp else 0.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = itemTitle(item),
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorProvider(Color(0xFF3C3C43), Color(0xFFC7C7CC))
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = item.primary,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.low) ColorProvider(Color(0xFFD32F2F), Color(0xFFEF9A9A))
                            else ColorProvider(Color(0xFF16181D), Color(0xFFF2F2F7))
                        ),
                        maxLines = 1
                    )
                }

                when {
                    item.error != null -> Text(
                        text = item.error,
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = ColorProvider(Color(0xFFD32F2F), Color(0xFFEF9A9A))
                        ),
                        modifier = GlanceModifier.padding(top = 1.dp),
                        maxLines = 1
                    )

                    item.lines.isNotEmpty() -> item.lines.forEach { line ->
                        QuotaLineRow(line)
                    }

                    item.secondary.isNotEmpty() -> Text(
                        text = item.secondary,
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = ColorProvider(Color(0xFF70737A), Color(0xFF9A9AA1))
                        ),
                        modifier = GlanceModifier.padding(top = 1.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }

    @Composable
    private fun QuotaLineRow(line: QuotaLine) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = line.title,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(Color(0xFF70737A), Color(0xFF9A9AA1))
                ),
                maxLines = 1
            )
            Text(
                text = line.value,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(Color(0xFF3C3C43), Color(0xFFD1D1D6))
                ),
                maxLines = 1
            )
            if (line.note.isNotEmpty()) {
                Text(
                    text = " · ${line.note}",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = ColorProvider(Color(0xFF8E8E93), Color(0xFF8E8E93))
                    ),
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun Divider() {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x14000000), Color(0x1FFFFFFF))
        ) {}
    }

    private fun itemTitle(item: WidgetQuotaItem): String =
        if (item.label.isNotEmpty()) "${item.providerName} · ${item.label}" else item.providerName

    private fun footerText(updatedAt: Long): String =
        if (updatedAt > 0) "更新于 ${formatTime(updatedAt)} · 点按刷新" else "点按刷新"

    private fun formatTime(ts: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    /** 优先复用 App 容器里的缓存；widget 上下文拿不到时直接构造，保证不崩溃。 */
    private fun safeCache(context: Context): WidgetCache? = runCatching {
        context.applicationContext.appContainer().widgetCache
    }.getOrNull() ?: runCatching { WidgetCache(context.applicationContext) }.getOrNull()

    companion object {
        private const val TAG = "QuotaWidget"
        private const val AUTO_REFRESH_INTERVAL_MS = 15_000L
        private val lastAutoRefresh = AtomicLong(0)

        val STATE_ITEMS = stringPreferencesKey("items")
        val STATE_TIME = longPreferencesKey("updated_at")

        private val json = Json { ignoreUnknownKeys = true }

        /** 把额度列表序列化为可写入 Preferences 的 JSON 字符串。 */
        fun encodeItems(items: List<WidgetQuotaItem>): String? = runCatching {
            json.encodeToString(ListSerializer(WidgetQuotaItem.serializer()), items)
        }.getOrNull()

        /** 从实例状态里解析额度快照；无数据或解析失败返回 null（调用方回退缓存）。 */
        private fun readState(prefs: Preferences?): Pair<List<WidgetQuotaItem>, Long>? {
            if (prefs == null) return null
            val raw = prefs[STATE_ITEMS] ?: return null
            val items = runCatching {
                json.decodeFromString(ListSerializer(WidgetQuotaItem.serializer()), raw)
            }.getOrNull() ?: return null
            return items to (prefs[STATE_TIME] ?: 0L)
        }
    }
}
