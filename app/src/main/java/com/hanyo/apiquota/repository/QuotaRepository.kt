package com.hanyo.apiquota.repository

import com.hanyo.apiquota.data.local.AccountStore
import com.hanyo.apiquota.data.model.Account
import com.hanyo.apiquota.data.model.QuotaResult
import com.hanyo.apiquota.data.model.WidgetQuotaItem
import com.hanyo.apiquota.data.remote.Providers
import com.hanyo.apiquota.data.remote.QuotaFetcher

/**
 * 业务层：聚合账号与抓取器，对外提供「按账号查询」和「小组件扁平数据」两种结果。
 */
class QuotaRepository(
    private val accountStore: AccountStore,
    private val fetcher: QuotaFetcher
) {
    /** 并发查询所有启用账号，返回 id -> 结果 映射。 */
    suspend fun fetchAll(accounts: List<Account>): Map<String, QuotaResult> {
        return accounts.associate { acc ->
            val provider = Providers.byId(acc.providerId)
            acc.id to fetcher.fetch(acc, provider)
        }
    }

    /** 直接产出小组件所需的扁平列表。 */
    suspend fun loadAndFetch(): List<WidgetQuotaItem> {
        val accs = accountStore.getAccounts().filter { it.enabled }
        return accs.map { acc ->
            val provider = Providers.byId(acc.providerId)
            when (val r = fetcher.fetch(acc, provider)) {
                is QuotaResult.Success -> WidgetQuotaItem(
                    providerName = provider.displayName,
                    label = acc.label,
                    primary = r.primary,
                    secondary = r.secondary,
                    low = r.low,
                    providerId = provider.id,
                    lines = r.lines
                )
                is QuotaResult.Failure -> WidgetQuotaItem(
                    providerName = provider.displayName,
                    label = acc.label,
                    primary = "—",
                    secondary = r.message,
                    low = false,
                    error = r.message,
                    providerId = provider.id
                )
            }
        }
    }
}
