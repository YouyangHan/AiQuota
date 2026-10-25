# AI 额度管家 (AiQuota)

一个 Android 应用，把 **DeepSeek / Kimi / KimiCode / 智谱 GLM** 的剩余额度集中查出来，
并以圆角卡片小组件常驻桌面 —— 不用逐个打开各家控制台，抬头就能看到还剩多少钱、额度用了多少、什么时候重置。

- 小众但好用：桌面小组件点一下即刷新，支持 1 / 5 / 15 / 30 / 60 分钟自动刷新
- API Key 只保存在本机（Android Keystore 加密），不上传任何服务器
- 纯 Kotlin + Jetpack Compose + Glance（无 WebView、无第三方统计）

## 功能

- **多平台聚合**：余额型（DeepSeek / Kimi）与配额窗口型（KimiCode / GLM）统一展示
- **桌面小组件（4x2）**
  - 圆角卡片、品牌图标、平台间分隔线
  - 明细行展示「本周用量 / 5 小时频限 + 重置时间」
  - 去掉标题和按钮，**点按整块卡片即刷新**
  - 底部显示「更新于 HH:mm · 点按刷新」
- **后台自动刷新**：间隔可选 1 / 5 / 15 / 30 / 60 分钟，开机后自动恢复
- **账号管理**：同一平台可添加多个账号（用备注区分），可单独删除
- **低额度提示**：余额过低标红，周额度使用率 ≥ 90% 标红

## 支持的平台

| 平台 | 接口 | 鉴权 | 展示内容 | 稳定性 |
| --- | --- | --- | --- | --- |
| DeepSeek | `GET api.deepseek.com/user/balance` | `Bearer` | 总余额、赠金、充值余额 | 官方 |
| Kimi (Moonshot) | `GET api.moonshot.cn/v1/users/me/balance` | `Bearer` | 可用余额、代金券、现金 | 官方 |
| KimiCode | `GET api.kimi.com/coding/v1/usages` | `Bearer` | 本周用量、5 小时频限、各自重置时间 | 社区逆向 |
| 智谱 GLM | `GET open.bigmodel.cn/api/monitor/usage/quota/limit` | 裸 Key（无 `Bearer`） | 5 小时 Token、月度配额、重置时间 | 社区逆向 |

> KimiCode 与 GLM 的接口来自社区逆向，**可能随官方调整而失效**。
> 解析层对字段名、时间格式都做了容错：缺字段时展示可读降级信息（如 `—`），不会崩溃。

## 桌面小组件

```
┌─────────────────────────────────────────────┐
│ [D]  DeepSeek · 主号                ¥20.20  │
│      赠金余额                         ¥0   │
│      充值余额                      ¥20.20  │
│ ─────────────────────────────────────────── │
│ [KC] KimiCode                       本周 35% │
│      本周用量  35 / 100 · 10-27 14:00 重置  │
│      5小时频限  8 / 20   · 13:00 重置       │
│                                             │
│ 更新于 11:38 · 点按刷新                      │
└─────────────────────────────────────────────┘
```

- 布局为普通 `Column` + `forEach`（不使用 Lazy 列表），避免合成期异常导致整块空白
- 所有文字显式指定明/暗两套颜色，不依赖主题
- 尺寸一律使用 `Dp`：Glance 的 `Modifier.padding` 有 `Int`（dimen 资源 ID）重载，传整数会在渲染期崩溃
- 圆角使用 `cornerRadius()`，仅在 Android 12+ 生效；更低版本显示为直角

### 刷新机制

| 间隔 | 调度方式 | 说明 |
| --- | --- | --- |
| 1 / 5 分钟 | `AlarmManager.setAndAllowWhileIdle` 单次闹钟 + 接收器续排 | 系统对非精确闹钟有批量窗口，实测每次可能延后 0~45 秒；Doze 下更久 |
| 15 / 30 / 60 分钟 | WorkManager 周期任务 | 系统强制的最小周期是 15 分钟，带联网约束 |

- 短间隔按固定时间轴续排（`上次计划时间 + 间隔`），不会因系统延迟而逐步漂移；
  落后超过一个周期时重置，避免补跑风暴
- 开机 / 应用更新后由 `BootReceiver` 恢复调度
- 闹钟触发时直接在接收器中拉取（`goAsync` + 8 秒超时），超时才降级给 WorkManager

## 快速开始

### 环境要求

- Android Studio Hedgehog / Iguana 或更新版本
- JDK 17+（本项目用 JDK 21 验证构建）
- Android SDK：`compileSdk 34`，最低运行 `minSdk 26`（Android 8.0）

### 构建

```bash
# 用本机已安装的 Gradle（8.9）
gradle assembleDebug

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

> 仓库未提交 `gradle-wrapper.jar`（只保留 `gradle-wrapper.properties`）。
> 用 Android Studio 打开本项目会自动生成 wrapper；命令行构建请先安装 Gradle 8.9，
> 或执行一次 `gradle wrapper` 生成 `gradlew`。

### 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用

1. 打开 App，点右下角 **+** 添加账号：选择平台、粘贴 API Key（可选填备注）
2. 主页卡片会显示余额 / 配额明细，点右上角刷新可手动更新
3. 长按桌面空白处 → **小组件** → 找到 **AI 额度管家** → 拖到桌面
4. 点一下桌面小组件即可刷新
5. 设置页可开关自动刷新、选择间隔（1 / 5 / 15 / 30 / 60 分钟），也可立即刷新小组件

## 项目结构

```
AiQuota/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── drawable/ic_brand_*.xml      # 各平台品牌图标（矢量）
        │   ├── drawable/ic_launcher_foreground.xml
        │   ├── mipmap-anydpi-v26/ic_launcher.xml
        │   └── xml/quota_widget_info.xml    # 小组件声明
        └── java/com/hanyo/apiquota/
            ├── App.kt                       # Application + 依赖容器 + 调度恢复
            ├── MainActivity.kt              # Compose 入口与页面切换
            ├── data/
            │   ├── model/Models.kt          # Account / QuotaResult / QuotaLine / WidgetQuotaItem
            │   ├── remote/Providers.kt      # 四个平台配置
            │   ├── remote/QuotaFetcher.kt   # OkHttp 请求 + 各平台解析 / 容错
            │   └── local/                   # AccountStore(加密) / SettingsStore
            ├── repository/QuotaRepository.kt
            ├── ui/
            │   ├── QuotaViewModel.kt
            │   └── screens/                 # Home / AddAccount / Settings
            └── widget/
                ├── QuotaGlanceWidget.kt     # Glance 小组件 UI
                ├── WidgetUpdater.kt         # 唯一网络刷新入口
                ├── WidgetCache.kt           # 冷启动快照缓存
                ├── WidgetIcons.kt           # 平台 -> 品牌图标
                ├── RefreshScheduler.kt      # 1/5 分钟闹钟 + 15 分钟以上 WorkManager
                ├── RefreshReceivers.kt      # 闹钟接收器 / 开机恢复
                ├── RefreshAction.kt         # 点按小组件回调
                └── QuotaWidgetWorker.kt     # WorkManager Worker
```

## 架构与数据流

```
AccountStore(加密) ──► QuotaRepository ──► QuotaFetcher ──► 各平台 HTTP 接口
        │                                      │
        └──► QuotaViewModel ──► App UI          └──► QuotaResult
                                  │
        RefreshScheduler ──► WidgetUpdater.refresh()
        (闹钟 / WorkManager)         │
                                     ├──► WidgetCache（SharedPreferences 快照）
                                     └──► 各实例 Glance state + update()
                                                    │
                                             QuotaGlanceWidget
```

几个关键设计：

- **渲染与网络解耦**：`provideGlance` 只从 `WidgetCache` 读快照、立即渲染首帧，绝不在合成路径里等网络；
  缓存为空时后台触发一次拉取
- **为什么写 Glance state**：`update()` 在 widget 会话存活时只会触发 state 刷新 + 重组，
  **不会重跑 `provideGlance`**。因此 `WidgetUpdater` 通过 `updateAppWidgetState(...)` 把数据写入
  `PreferencesGlanceStateDefinition`，内容区读取 `LocalState`，从而实现运行期实时刷新
- **结构化数据**：`QuotaResult.Success` 除 `primary/secondary` 外还带 `lines: List<QuotaLine>`
  （标题 / 数值 / 重置时间），小组件按行渲染，App 卡片同样展示
- **单实例多平台**：小组件对 `GlanceAppWidgetManager.getGlanceIds()` 返回的所有实例统一更新

## 新增一个平台

1. 在 `data/remote/Providers.kt` 的 `Providers.ALL` 增加一项 `ProviderMeta`
2. 在 `data/remote/QuotaFetcher.kt` 的 `parse()` 中增加 `provider.id` 分支，返回
   `QuotaResult.Success(primary, secondary, low, lines)` 或 `QuotaResult.Failure(msg)`
3. 在 `res/drawable/` 放一个品牌图标，并在 `widget/WidgetIcons.kt` 中登记映射

## 安全与隐私

- API Key 使用 `EncryptedSharedPreferences` 保存，密钥由 Android Keystore 托管
- Key 仅在本机使用，直接请求各平台官方域名，不经过任何中转服务器
- 应用不申请无关权限；`RECEIVE_BOOT_COMPLETED` 仅用于恢复刷新调度
- 小组件缓存（`WidgetCache`）只存额度数值，不存 API Key

## 已知限制

- **逆向接口**：KimiCode / 智谱 GLM 的接口可能随官方调整失效；解析层已做容错，字段变化时只需更新 `QuotaFetcher`
- **短间隔刷新**：1 / 5 分钟受系统闹钟批量窗口与 Doze 限制，实际可能延后；息屏时尤其明显
- **小组件空间**：4x2 尺寸，账号较多时内容会自动压缩/裁剪
- **圆角**：`cornerRadius` 仅在 Android 12+ 生效
- **Android 8~11**：无动态取色等新特性，但基础功能完整

## 技术栈

- Kotlin 1.9 / JDK 17
- Jetpack Compose + Material 3（App 界面）
- **Glance 1.1.0**（桌面小组件）
- WorkManager 2.9.1 / AlarmManager（后台刷新）
- OkHttp 4.12（网络）
- kotlinx.serialization 1.6（状态与缓存序列化）
- androidx.security:security-crypto（加密存储）
