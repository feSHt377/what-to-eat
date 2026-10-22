# 交接文档 · whattoeat（Android 热量/代谢记录 App）

> 生成时间：2026-10-22 23:5x（Asia/Shanghai）
> 状态一句话：**代码已全部写完（可编译的正确性未验证），还没跑通 build，还没建 git 仓库、没推送。**

---

## 0. 立刻要知道的 3 件事

1. **编译还没成功验证过**。最后一次 `assembleDebug` 被中断，日志停在 `Starting a Gradle Daemon`。大概率能过，但需要第一次真实构建来暴露问题（预期的坑见第 6 节）。
2. **Gradle Wrapper 已就绪**，`gradle-8.14.3` 分发版已解压到本机缓存，新机器需要重新下载（见第 5 节，国内网络有坑）。
3. **git 仓库还没初始化**，GitHub 仓库也还没创建。`gh` CLI 已登录账号 `feSHt377`（scopes 含 `repo`），新机器需要重新 `gh auth login`。

---

## 1. 项目是什么

安卓 App「whattoeat」：记录每日饮食（热量 + 三大营养素），长期记录后**反推真实每日代谢（TDEE）**，并给出热量缺口/盈余。

当前 v0.1.0 demo，纯本地、无账号、不联网。

---

## 2. 已完成的功能（代码都在）

### 开屏动画
`ui/splash/SplashScreen.kt`：🧐 从下方弹到中央（spring 回弹）→ 变 😋 → 放大 1.9 倍 → 渐隐 → 进主页。
纯 `Animatable` 编排，无 lottie 依赖。总时长约 2 秒。

### Tab 1 · 今日（`ui/diary/`）
- 日期前后翻页 + 「回到今天」
- 环形进度：剩余/超出 kcal（超出标红）
- 三大营养素进度条（蛋白/脂肪/碳水，各自 已吃/目标）
- 4 个餐次分块（早/午/晚/加餐），单条可删

### 添加食物（`ui/add/AddFoodScreen.kt`）
两条路径（对应你的需求）：
- **模板食物**：内置 50+ 中餐食物（`data/seed/FoodSeed.kt`），搜索 → 输入克数 → 自动换算
- **自定义记录**：只填「名称 + 热量」，三大营养素可选填

### Tab 2 · 汇总（`ui/summary/`）
- 周期切换 7 / 14 / 30 天
- 日均摄入、目标、累计热量差（缺口/盈余）、记录天数、达标天数、体重变化
- 每日热量柱状图（Canvas 手绘，`ui/common/Charts.kt`）：目标虚线、超标标红、无记录显示灰线
- TDEE 估算卡片（显示来源 + 各参考值）
- 日均三大营养素 vs 目标
- 逐日明细列表

### Tab 3 · 设置（`ui/settings/`）
- **账号卡**：占位文字 + 禁用按钮「登录 / 注册（暂未开放）」
- **个人档案**：性别、身高、体重、出生年份、活动量、目标（减脂/维持/增肌）
- **每日代谢 TDEE**：手填（留空=自动），界面上实时显示"留空时的自动值约多少、来源是什么"
- **食物模板管理**：新增 / 编辑 / 删除（内置模板也可改可删）
- 关于卡：版本、存储说明

### 单元测试（`app/src/test/`，尚未运行过）
- `MetabolismTest`：BMR 手算校验、男女差值 166、体重斜率回归、抗噪、自适应 TDEE 正负号、可信度分档
- `TdeeEstimatorTest`：三档优先级（手动 > 自适应 > 性别平均值）、数据不足回退、0/负数视为未设置、目标热量安全下限
- `NutrientsTest`：按克数换算、多条加总、空集合

---

## 3. 代谢算法（核心业务，勿随意改）

**优先级（高 → 低）**，实现见 `domain/TdeeEstimator.kt`：

| 优先级 | 来源 | 生效条件 |
|---|---|---|
| 1 | 手动设置值 `profile.manualTdeeKcal` | 设置页填了就无条件用 |
| 2 | 自适应反推 | 最近 28 天 ≥5 天饮食记录 且 ≥2 次体重 |
| 3 | 性别平均值 | 男 2250 / 女 1800 kcal（轻体力参考值） |

**自适应反推公式**：

```
体重趋势 = 对 (日期, 体重) 做最小二乘线性回归，得到 kg/天（不要用首尾相减，噪声太大）
TDEE     = 平均日摄入 − (kg/天 × 7700)
```
例：日均 2000 kcal，体重每天 −0.05 kg → 2000 + 385 = **2385 kcal**

**每日目标**（`DailyTargets.from`）：
```
热量 = TDEE + 目标差值（减脂 −400 / 维持 0 / 增肌 +250），下限 1200
蛋白 = 体重 × 1.6 g
脂肪 = 热量 × 25% / 9
碳水 = 剩余 / 4
```

---

## 4. 技术栈与版本（**已锁定，别乱动**）

| 项 | 版本 | 备注 |
|---|---|---|
| JDK | **17**（必须） | 本机 PATH 里的 java 是 1.8，会构建失败 |
| Gradle | 8.14.3 | 通过 wrapper 使用 |
| AGP | 8.13.2 | 与 Gradle 8.14.3 配套；**不要升 9.x**（AGP 9 改了内置 Kotlin 机制） |
| Kotlin | 2.2.21 | |
| KSP | **2.2.21-2.0.5** | 前半段必须与 Kotlin 完全一致，单独改会挂 |
| Compose BOM | 2025.10.01 | |
| Room | 2.7.2 | KSP 生成，`exportSchema = true` |
| compileSdk / targetSdk | 36 | |
| minSdk | 26 | 为了直接用 `java.time`，不开 desugaring |
| 架构 | 单 Activity + MVVM + UDF + 手写 DI（无 Hilt） | |

依赖版本集中管理：`gradle/libs.versions.toml`。

---

## 5. 新机器上继续（照做即可）

### 5.1 环境准备
1. JDK 17（推荐 Temurin 17）
2. Android SDK：`platforms;android-36` + `build-tools;36.0.0`（本机路径 `G:\AndroidSDK`）
3. 复制整个 `WhatToEat` 目录（**不要复制** `.gradle/`、`app/build/`、`build-log.txt`）

### 5.2 改本机路径
编辑 `local.properties`（此文件不进 git）：
```properties
sdk.dir=D\:\\Your\\AndroidSdk
```

### 5.3 构建
```bat
cd <项目目录>
set JAVA_HOME=C:\Path\To\jdk-17
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
```
产物：`app\build\outputs\apk\debug\app-debug.apk`

### 5.4 国内网络两个坑（本机已踩过）

**坑 1：Gradle 分发版下载超时。** `services.gradle.org` 在 Java 客户端里连不上（PowerShell 能连，Java 不走系统代理）。解决方式二选一：

方式 A（推荐，快）：手动把 zip 塞进 wrapper 缓存
```powershell
# 1) 先跑一次 gradlew，让它生成缓存目录（会失败，正常）
.\gradlew.bat --version

# 2) 找到 hash 目录
$dir = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.14.3-bin" -Directory | Select-Object -First 1

# 3) 从腾讯镜像下载（实测 131MB 秒下）
Invoke-WebRequest "https://mirrors.cloud.tencent.com/gradle/gradle-8.14.3-bin.zip" `
  -OutFile "$($dir.FullName)\gradle-8.14.3-bin.zip" -UseBasicParsing

# 4) 重跑 gradlew
.\gradlew.bat assembleDebug
```
方式 B：用代理，通过环境变量传给 JVM（本机是 Clash，端口 7897）：
```powershell
$env:GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"
```

**坑 2：Maven 依赖下载。** 若 `dl.google.com` / `repo1.maven.org` 慢，可在 `settings.gradle.kts` 里加阿里云镜像（`maven { url = uri("https://maven.aliyun.com/repository/google") }` 等）。目前配置只有 google() + mavenCentral()，本机实测可达。

### 5.5 建仓库并推送（还没做，账号 `feSHt377`）
```bat
cd <项目目录>
git init
git add .
git commit -m "feat: whattoeat v0.1 demo（开屏动画 + 每日记录 + 周期汇总 + 设置/模板管理）"

:: GitHub 仓库名不支持空格，"what to eat" 会被规范成 what-to-eat
gh repo create what-to-eat --private --source . --remote origin --push
```
注意：`.gitignore` 已排除 `local.properties`、`.gradle/`、`build/`、`*.apk`，别把它们提交上去。
`app/schemas/`（Room 导出的表结构 JSON）**建议提交**，以后写数据库迁移要用。

---

## 6. 还没做 / 未验证的部分

### 必须验的
- [ ] **首次真实构建**（`assembleDebug`）—— 从未成功跑完过一次
- [ ] **单测**（`testDebugUnitTest`）—— 写了没跑
- [ ] **装机实测**：开屏动画手感、录一条记录、翻页、删记录、改档案、增删模板

### 首次构建可能报错的地方（按概率排序，供快速定位）
1. **Kotlin/KSP 版本错配**：若报 `ksp-2.2.21-2.0.5 not found` 或 KSP 与 Kotlin 不匹配，去 Maven 确认可用版本号（`com.google.devtools.ksp:symbol-processing-gradle-plugin`），前半段必须等于 Kotlin 版本。
2. **Material3 API 差异**：`LinearProgressIndicator(progress = { })`（lambda 重载）、`HorizontalDivider`、`FilterChip` 在不同版本签名不同。若报错，改成当前 BOM 对应的签名即可。
3. **`fallbackToDestructiveMigration(dropAllTables = true)`**：Room 2.7 的新重载；若报没有此方法，改成无参调用（会有一条 deprecation 警告）。
4. **Compose BOM 与 Kotlin metadata 不兼容**：若报 "class file compiled with a newer Kotlin"，把 BOM 换成更早的 2025.x 版本。
5. 未使用的 import 只是 warning，不影响构建。

### 功能上还没做的
- [ ] 记录**编辑**（现在只能删了重加）
- [ ] 数据导出/导入（JSON/CSV）—— 本地 App 没备份等于随时丢数据，**优先级最高**
- [ ] 真正的 Room `Migration`（开发期用的是 destructive fallback，上线前必须换）
- [ ] 条码扫描（CameraX + ML Kit + Open Food Facts）
- [ ] 通知提醒（WorkManager）
- [ ] 食物库扩充到 2000+ 条，**生熟状态必须分开**（熟米饭 116 kcal/100g vs 生大米 346）
- [ ] 账号 / 云同步（Supabase 比 Firebase 省事且国内可用）
- [ ] 「按当前速度几周能到目标」的预测卡片
- [ ] 应用图标（现在是个临时矢量图 `res/drawable/ic_launcher.xml`）

---

## 7. 代码地图

```
app/src/main/java/com/whattoeat/app/
├── MainActivity.kt            单 Activity：开屏 → MainScreen
├── WhatToEatApp.kt            Application + AppContainer（手写 DI，替代 Hilt）
├── domain/                    纯 Kotlin，无 Android 依赖，可直接单测
│   ├── Nutrients.kt           营养模型（plus / times / scaledTo / sum）
│   ├── UserProfile.kt         Sex / ActivityLevel / Goal / 档案（含 manualTdeeKcal）
│   ├── MealType.kt            餐次 + 按时间猜默认餐次
│   ├── Metabolism.kt          BMR、体重回归、自适应 TDEE、DailyTargets、平均值常量
│   └── TdeeEstimator.kt       三档优先级仲裁 + TdeeSource 枚举
├── data/
│   ├── DietRepository.kt      查询 / 写记录（模板 & 自定义）/ 模板 CRUD / 实体↔领域转换
│   ├── db/Entities.kt         foods / log_entries / weight_logs / user_profile
│   ├── db/Daos.kt             4 个 DAO，查询返回 Flow
│   ├── db/AppDatabase.kt      Room 单例（v1，destructive fallback）
│   └── seed/FoodSeed.kt       50+ 条内置食物（每 100 克值）
└── ui/
    ├── MainScreen.kt          3 Tab + 全屏添加页 + FAB
    ├── splash/SplashScreen.kt 开屏动画
    ├── diary/                 每日页 + ViewModel（combine 5 条流）
    ├── add/AddFoodScreen.kt   模板录入 + 自定义录入
    ├── summary/               周期汇总 + ViewModel
    ├── settings/              设置 + 模板管理 + ViewModel
    ├── common/                环形进度、营养条、柱状图、格式化
    └── theme/                 配色（绿色系）+ NutrientColors
```

### 两个关键设计（改代码前务必理解）
1. **记录存快照**：`log_entries` 冗余保存 `foodName` 与营养素，不只存 `foodId`。这样改/删模板不会污染历史记录。**别把它"优化"成外键关联。**
2. **Flow 驱动刷新**：Room 查询返回 `Flow`，写入后 UI 自动刷新，全项目没有一处手动 `refresh()`。

---

## 8. 文件清单（哪些要带，哪些不要带）

**要带**：`app/`（不含 `app/build/`）、`gradle/`、`gradlew`、`gradlew.bat`、`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`.gitignore`、`README.md`、`HANDOVER.md`、`local.properties`（复制后改路径）

**不要带**：`.gradle/`、`app/build/`、`build-log.txt`、`local.properties` 里的旧 sdk 路径

---

## 9. 建议的下一步顺序

1. `assembleDebug` 跑通，修掉编译错误（预计 0.5–2 小时）
2. `testDebugUnitTest` 全绿
3. 装机实测开屏 + 记一餐 + 汇总页数据是否正确
4. `git init` + `gh repo create what-to-eat --private` 推送
5. 做**数据导出/导入**（这是 demo 之后第一个必须补的功能）
6. 再谈食物库扩充和条码扫描
