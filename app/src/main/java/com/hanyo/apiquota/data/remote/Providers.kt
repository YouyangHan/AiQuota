package com.hanyo.apiquota.data.remote

import com.hanyo.apiquota.data.model.AuthMode
import com.hanyo.apiquota.data.model.ProviderMeta
import com.hanyo.apiquota.data.model.QuotaKind

/**
 * 支持的平台配置。新增平台只需在此追加一项，并在 QuotaFetcher 中实现对应解析。
 */
object Providers {
    val ALL = listOf(
        ProviderMeta(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            path = "/user/balance",
            auth = AuthMode.BEARER,
            kind = QuotaKind.BALANCE,
            official = true
        ),
        ProviderMeta(
            id = "kimi",
            displayName = "Kimi (Moonshot)",
            baseUrl = "https://api.moonshot.cn",
            path = "/v1/users/me/balance",
            auth = AuthMode.BEARER,
            kind = QuotaKind.BALANCE,
            official = true
        ),
        ProviderMeta(
            id = "kimicode",
            displayName = "KimiCode",
            baseUrl = "https://api.kimi.com/coding",
            path = "/v1/usages",
            auth = AuthMode.BEARER,
            kind = QuotaKind.WINDOW,
            official = false
        ),
        ProviderMeta(
            id = "glm",
            displayName = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn",
            path = "/api/monitor/usage/quota/limit",
            auth = AuthMode.BARE,
            kind = QuotaKind.WINDOW,
            official = false
        )
    )

    fun byId(id: String): ProviderMeta = ALL.firstOrNull { it.id == id } ?: ALL[0]
}
