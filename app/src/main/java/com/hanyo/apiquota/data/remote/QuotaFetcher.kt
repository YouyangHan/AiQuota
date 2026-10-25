package com.hanyo.apiquota.data.remote

import com.hanyo.apiquota.data.model.Account
import com.hanyo.apiquota.data.model.AuthMode
import com.hanyo.apiquota.data.model.ProviderMeta
import com.hanyo.apiquota.data.model.QuotaLine
import com.hanyo.apiquota.data.model.QuotaResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * 负责向各平台发起余额/配额请求并解析为统一的 QuotaResult。
 * 对社区逆向接口（KimiCode / GLM）做了容错：字段名、时间格式都不稳定，
 * 解析时逐一尝试常见写法，拿不到就留空展示，绝不因字段缺失崩溃。
 */
class QuotaFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(account: Account, provider: ProviderMeta): QuotaResult = withContext(Dispatchers.IO) {
        try {
            val url = provider.baseUrl.trimEnd('/') + provider.path
            val request = Request.Builder().url(url).apply {
                when (provider.auth) {
                    AuthMode.BEARER -> header("Authorization", "Bearer ${account.apiKey}")
                    AuthMode.BARE -> header("Authorization", account.apiKey)
                }
                header("Accept", "application/json")
            }.build()

            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext QuotaResult.Failure("HTTP ${resp.code}")
                }
                parse(provider, body)
            }
        } catch (e: Exception) {
            QuotaResult.Failure(e.message ?: "网络错误")
        }
    }

    private fun parse(provider: ProviderMeta, body: String): QuotaResult {
        val json = runCatching { JSONObject(body) }
            .getOrElse { return QuotaResult.Failure("无法解析返回数据") }
        return when (provider.id) {
            "deepseek" -> parseDeepSeek(json)
            "kimi" -> parseKimi(json)
            "kimicode" -> parseKimiCode(json)
            "glm" -> parseGlm(json)
            else -> QuotaResult.Failure("不支持的平台: ${provider.id}")
        }
    }

    // ---- 各平台解析 ----

    private fun parseDeepSeek(json: JSONObject): QuotaResult {
        val infos = json.optJSONArray("balance_infos")
        val first = infos?.optJSONObject(0)
        val currency = (first?.optString("currency") ?: "CNY").uppercase()
        val total = (first?.optString("total_balance") ?: "0").toDoubleOrNull() ?: 0.0
        val granted = (first?.optString("granted_balance") ?: "0").toDoubleOrNull() ?: 0.0
        val topped = (first?.optString("topped_up_balance") ?: "0").toDoubleOrNull() ?: 0.0
        val available = json.optBoolean("is_available", true)
        val symbol = if (currency == "USD") "$" else "¥"
        val low = !available || total < LOW_THRESHOLD
        return QuotaResult.Success(
            primary = "$symbol${fmt(total)}",
            secondary = "赠金 $symbol${fmt(granted)} / 充值 $symbol${fmt(topped)}",
            low = low,
            lines = listOf(
                QuotaLine("赠金余额", "$symbol${fmt(granted)}"),
                QuotaLine("充值余额", "$symbol${fmt(topped)}")
            )
        )
    }

    private fun parseKimi(json: JSONObject): QuotaResult {
        val data = json.optJSONObject("data") ?: return QuotaResult.Failure("返回格式异常")
        val available = data.optDouble("available_balance", 0.0)
        val voucher = data.optDouble("voucher_balance", 0.0)
        val cash = data.optDouble("cash_balance", 0.0)
        val low = available < LOW_THRESHOLD
        return QuotaResult.Success(
            primary = "¥${fmt(available)}",
            secondary = "代金券 ¥${fmt(voucher)} / 现金 ¥${fmt(cash)}",
            low = low,
            lines = listOf(
                QuotaLine("代金券", "¥${fmt(voucher)}"),
                QuotaLine("现金", "¥${fmt(cash)}")
            )
        )
    }

    /**
     * KimiCode：usage = 7 天周额度；limits[] 中 duration=300 分钟（或 timeUnit=MINUTE）= 5 小时频限窗口。
     * 重置时间字段在不同版本里叫法不一，用 findReset() 兼容毫秒/秒时间戳与 ISO 字符串。
     */
    private fun parseKimiCode(json: JSONObject): QuotaResult {
        // 有的版本直接返回顶层 usage/limits，有的包一层 data
        val root = json.optJSONObject("data") ?: json
        val usage = root.optJSONObject("usage") ?: json.optJSONObject("usage")
        val weeklyUsed = num(usage, "used")
        val weeklyLimit = num(usage, "limit")
        val weeklyReset = findReset(usage) ?: findReset(root) ?: findReset(json)

        var sessionUsed = Double.NaN
        var sessionLimit = Double.NaN
        var sessionReset: Long? = null
        val limits = root.optJSONArray("limits") ?: json.optJSONArray("limits")
        for (i in 0 until (limits?.length() ?: 0)) {
            val w = limits!!.optJSONObject(i) ?: continue
            val win = w.optJSONObject("window")
            val dur = win?.optInt("duration", 0) ?: 0
            val unit = win?.optString("timeUnit").orEmpty()
            if (dur == 300 || unit.contains("MINUTE", ignoreCase = true)) {
                val d = w.optJSONObject("detail") ?: w
                sessionUsed = num(d, "used")
                sessionLimit = num(d, "limit")
                sessionReset = findReset(d) ?: findReset(win) ?: findReset(w)
                break
            }
        }

        val lines = buildList {
            if (!weeklyUsed.isNaN() || !weeklyLimit.isNaN()) {
                add(QuotaLine("本周用量", usageText(weeklyUsed, weeklyLimit), resetNote(weeklyReset)))
            }
            if (!sessionUsed.isNaN() || !sessionLimit.isNaN()) {
                add(QuotaLine("5小时频限", usageText(sessionUsed, sessionLimit), resetNote(sessionReset)))
            }
        }

        val membership = (root.optJSONObject("user") ?: json.optJSONObject("user"))
            ?.optJSONObject("membership")
            ?.optString("level") ?: "-"
        val pct = percent(weeklyUsed, weeklyLimit)
        val primary = if (pct >= 0) "本周 $pct%" else "周 ${fmt(weeklyUsed)}/${fmt(weeklyLimit)}"
        val secondary = buildString {
            append(usageText(weeklyUsed, weeklyLimit))
            if (!sessionLimit.isNaN()) append(" · 5h ${usageText(sessionUsed, sessionLimit)}")
            if (membership.isNotEmpty() && membership != "-") append(" · $membership")
        }
        val low = pct >= 90
        return QuotaResult.Success(primary, secondary, low, lines)
    }

    private fun parseGlm(json: JSONObject): QuotaResult {
        // 裸 key 鉴权；返回 limits[]：TOKENS_LIMIT=5h Token，TIME_LIMIT=月度配额
        val data = json.optJSONObject("data") ?: return QuotaResult.Failure("返回格式异常")
        val limits = data.optJSONArray("limits")
        var token5h = ""
        var token5hLine: QuotaLine? = null
        var month = ""
        var monthLine: QuotaLine? = null
        for (i in 0 until (limits?.length() ?: 0)) {
            val l = limits!!.optJSONObject(i)
            val type = l.optString("type")
            val usage = l.optLong("usage", 0).toDouble()
            val current = l.optLong("currentValue", 0).toDouble()
            val reset = findReset(l) ?: findReset(data)
            when (type) {
                "TOKENS_LIMIT" -> {
                    token5h = usageText(current, usage)
                    token5hLine = QuotaLine("5小时 Token", token5h, resetNote(reset))
                }
                "TIME_LIMIT" -> {
                    month = usageText(current, usage)
                    monthLine = QuotaLine("月度配额", month, resetNote(reset))
                }
            }
        }
        val level = data.optString("level").ifEmpty { "-" }
        val primary = token5h.ifEmpty { month.ifEmpty { "—" } }
        val secondary = listOf(month, "套餐:$level")
            .filter { it.isNotEmpty() }
            .joinToString("  ")
        val lines = listOfNotNull(token5hLine, monthLine)
        return QuotaResult.Success(primary, secondary, low = false, lines = lines)
    }

    // ---- 工具 ----

    private fun num(obj: JSONObject?, key: String): Double {
        if (obj == null) return Double.NaN
        obj.optString(key, "").toDoubleOrNull()?.let { return it }
        return obj.optDouble(key, Double.NaN)
    }

    private fun fmt(v: Double): String {
        if (v.isNaN()) return "?"
        return if (v % 1.0 == 0.0) "%.0f".format(v) else "%.2f".format(v)
    }

    /** 大数字缩写：12345 -> 12.3K，1234567 -> 1.2M。 */
    private fun compact(v: Double): String {
        if (v.isNaN()) return "?"
        val abs = kotlin.math.abs(v)
        val text = when {
            abs >= 1_000_000_000 -> "%.1fB".format(v / 1_000_000_000)
            abs >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
            abs >= 10_000 -> "%.1fK".format(v / 1_000)
            else -> fmt(v)
        }
        return text.removeSuffix(".0")
    }

    private fun percent(used: Double, limit: Double): Int {
        if (used.isNaN() || limit.isNaN() || limit <= 0) return -1
        return ((used / limit) * 100).roundToInt()
    }

    /** 「已用 / 总量 (百分比)」，缺字段时降级展示。 */
    private fun usageText(used: Double, limit: Double): String {
        val pct = percent(used, limit)
        val base = "${compact(used)} / ${compact(limit)}"
        return if (pct >= 0) "$base ($pct%)" else base
    }

    /**
     * 从对象里找重置时间。兼容：
     *  - 毫秒 / 秒级时间戳（数值或字符串）
     *  - ISO-8601（2026-10-27T14:00:00Z / +08:00）
     *  - 「yyyy-MM-dd HH:mm:ss」
     *  - resetIn / ttl 之类的相对秒数
     */
    private fun findReset(obj: JSONObject?): Long? {
        if (obj == null) return null
        for (key in RESET_KEYS) {
            if (!obj.has(key) || obj.isNull(key)) continue
            when (val v = obj.opt(key)) {
                is Number -> normalizeEpoch(v.toLong())?.let { return it }
                is String -> {
                    v.trim().toLongOrNull()?.let { n -> normalizeEpoch(n)?.let { return it } }
                    parseTimeString(v)?.let { return it }
                }
            }
        }
        for (key in TTL_KEYS) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val seconds = obj.optLong(key, -1L)
            if (seconds > 0) return System.currentTimeMillis() + seconds * 1000
        }
        return null
    }

    private fun normalizeEpoch(raw: Long): Long? {
        if (raw <= 0) return null
        // 10 位为秒、13 位为毫秒
        val millis = if (raw < 100_000_000_000L) raw * 1000 else raw
        // 合理区间：2001 年 ~ 10 年后
        val now = System.currentTimeMillis()
        if (millis < 1_000_000_000_000L || millis > now + 10L * 365 * 24 * 3600 * 1000) return null
        return millis
    }

    private fun parseTimeString(raw: String): Long? {
        if (raw.isBlank()) return null
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "MM-dd HH:mm"
        )
        for (p in patterns) {
            runCatching {
                val sdf = SimpleDateFormat(p, Locale.getDefault()).apply { isLenient = true }
                sdf.parse(raw)?.time
            }.getOrNull()?.let { return it }
        }
        return null
    }

    /** 今天内只显示 HH:mm，跨天显示 MM-dd HH:mm。 */
    private fun resetNote(ts: Long?): String {
        if (ts == null || ts <= 0) return ""
        val target = Calendar.getInstance().apply { timeInMillis = ts }
        val now = Calendar.getInstance()
        val sameDay = target.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "MM-dd HH:mm"
        val text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ts))
        return "$text 重置"
    }

    companion object {
        private const val LOW_THRESHOLD = 5.0

        private val RESET_KEYS = listOf(
            "resetTime", "reset_time", "resetAt", "reset_at",
            "nextResetTime", "next_reset_time", "resetTimestamp", "reset_timestamp",
            "expiresAt", "expires_at", "expireTime", "expire_time",
            "endTime", "end_time", "resetDate", "reset_date"
        )
        private val TTL_KEYS = listOf(
            "resetIn", "reset_in", "resetInSeconds", "reset_in_seconds",
            "ttl", "ttlSeconds", "ttl_seconds", "remainingSeconds", "remaining_seconds"
        )
    }
}
