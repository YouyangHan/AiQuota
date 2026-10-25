package com.hanyo.apiquota.data.model

import kotlinx.serialization.Serializable

/** API Key 的鉴权方式。 */
enum class AuthMode {
    /** Authorization: Bearer <key> */
    BEARER,
    /** Authorization: <key>（智谱 GLM 裸 key） */
    BARE
}

/** 额度展示形态：余额型（货币）或配额窗口型（用量/上限）。 */
enum class QuotaKind {
    BALANCE,
    WINDOW
}

/** 平台元信息：决定请求地址、鉴权与解析方式。 */
@Serializable
data class ProviderMeta(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val path: String,
    val auth: AuthMode,
    val kind: QuotaKind,
    /** 是否为官方稳定接口；false 表示社区逆向接口，可能随官方调整失效。 */
    val official: Boolean
)

/** 用户在 App 中保存的一个账号。 */
@Serializable
data class Account(
    val id: String,
    val providerId: String,
    val label: String,
    val apiKey: String,
    val enabled: Boolean = true
)

/** 额度明细行：窗口型配额（周用量 / 频限）用「标题 + 数值 + 重置时间」表达。 */
@Serializable
data class QuotaLine(
    val title: String,
    val value: String,
    /** 例如「10-27 14:00 重置」，没有重置信息时为空串。 */
    val note: String = ""
)

/** 一次额度查询的结果。 */
sealed interface QuotaResult {
    val low: Boolean

    data class Success(
        val primary: String,
        val secondary: String,
        override val low: Boolean,
        /** 结构化明细（小组件按行渲染）；余额型平台可为空。 */
        val lines: List<QuotaLine> = emptyList()
    ) : QuotaResult

    data class Failure(val message: String) : QuotaResult {
        override val low: Boolean get() = false
    }
}

/** 供小组件展示的扁平化数据。 */
@Serializable
data class WidgetQuotaItem(
    val providerName: String,
    val label: String,
    val primary: String,
    val secondary: String,
    val low: Boolean,
    val error: String? = null,
    /** 用于匹配品牌图标；旧缓存里没有该字段时为空串。 */
    val providerId: String = "",
    val lines: List<QuotaLine> = emptyList()
)
