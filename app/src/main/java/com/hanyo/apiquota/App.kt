package com.hanyo.apiquota

import android.app.Application
import android.content.Context
import com.hanyo.apiquota.data.local.AccountStore
import com.hanyo.apiquota.data.local.SettingsStore
import com.hanyo.apiquota.data.remote.QuotaFetcher
import com.hanyo.apiquota.repository.QuotaRepository
import com.hanyo.apiquota.widget.RefreshScheduler
import com.hanyo.apiquota.widget.WidgetCache

/**
 * 应用级容器：集中持有各单例依赖，避免引入 DI 框架。
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val accountStore = AccountStore(appContext)
    val fetcher = QuotaFetcher()
    val repository = QuotaRepository(accountStore, fetcher)
    val settings = SettingsStore(appContext)
    /** 小组件额度快照缓存，渲染与刷新都走这里。 */
    val widgetCache = WidgetCache(appContext)
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 不打断已有调度，只在缺失时补建（避免每次打开 App 重置短间隔闹钟）
        RefreshScheduler.ensureScheduled(this)
    }
}

/** 从任意 Context 拿到应用容器。 */
fun Context.appContainer(): AppContainer = (applicationContext as App).container
