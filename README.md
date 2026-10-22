# whattoeat 🧐😋

记录每天吃了什么、多少热量，并且**用长期记录反推你真实的每日代谢**的安卓 App。

当前是 demo 版（v0.1.0），数据全部保存在本机，不联网、不需要账号。

---

## 一、现在能做什么

### 1. 开屏动画
🧐 从屏幕下方弹到中央 → 变成 😋 → 慢慢放大 → 渐隐 → 进入主页面。
（纯 Compose `Animatable` 手写，没有引入 lottie。）

### 2. 今日（Tab 1）
- 按日期翻页（前一天 / 后一天 / 回到今天）
- 环形进度显示：今天还能吃多少 kcal（超额变红并显示超出量）
- 三大营养素进度条：蛋白质 / 脂肪 / 碳水，各自「已吃 / 目标」
- 四个餐次（早餐 / 午餐 / 晚餐 / 加餐）分块显示，可单独删除某一条

添加食物有两条路径：
- **选模板食物**：内置 50+ 常见中餐食物，输入克数自动换算热量与营养素
- **自定义记录**：只填「名称 + 热量」，三大营养素可选填（很多人根本不知道自己吃了多少蛋白）

### 3. 汇总（Tab 2）
- 周期切换：最近 7 / 14 / 30 天
- 日均摄入、每日目标、累计热量差（缺口 / 盈余）、记录天数、达标天数、体重变化
- 每日热量柱状图：虚线是目标，超标的柱子标红，灰线表示那天没记录
- **每日代谢 TDEE 估算**：展示当前用的是哪个来源，以及各个参考值
- 日均三大营养素 vs 目标

### 4. 设置（Tab 3）
- **账号**：占位（本版未接后端，硬编码文字）
- **个人档案**：性别、身高、体重、出生年份、活动量、目标（减脂 / 维持 / 增肌）
- **每日代谢 TDEE**：手填优先；留空则自动
- **食物模板管理**：新增 / 编辑 / 删除（内置模板也能改、能删；改模板不影响历史记录）
- 关于：版本、数据存储说明

---

## 二、代谢是怎么算的（核心逻辑）

### 优先级（高 → 低）

| 优先级 | 来源 | 何时生效 |
|---|---|---|
| 1 | **手动设置值** | 你在设置页填了「每日代谢 TDEE」就用它，无条件优先 |
| 2 | **自适应反推** | 最近 28 天里有 ≥5 天饮食记录 **且** ≥2 次体重记录 |
| 3 | **性别平均值（兜底）** | 什么都没有时：男 2250 kcal / 女 1800 kcal（轻体力参考值） |

设置页会实时告诉你「留空时的自动值约多少、来源是什么」，方便你决定要不要手动覆盖。

### 自适应反推的算法

```
每天体重变化 kg/天  ← 对体重记录做一元线性回归（最小二乘），不首尾相减，抗水分波动
TDEE = 平均日摄入 − (kg/天 × 7700)
```

例：日均摄入 2000 kcal，体重每天掉 0.05 kg
→ TDEE = 2000 − (−0.05 × 7700) = **2385 kcal**（体重在掉，说明消耗比吃进去的多）

### 每日目标

```
目标热量 = TDEE + 目标差值（减脂 −400 / 维持 0 / 增肌 +250），且不低于 1200 kcal
蛋白 = 体重 × 1.6 g
脂肪 = 目标热量 × 25% / 9
碳水 = 剩余热量 / 4
```

这些公式全在 `domain/Metabolism.kt` 和 `domain/TdeeEstimator.kt` 里，是**纯函数**，有单元测试覆盖。

---

## 三、技术栈

| 用途 | 选型 |
|---|---|
| 语言 | Kotlin 2.2.21 |
| UI | Jetpack Compose + Material 3 |
| 构建 | AGP 8.13.2 + Gradle 8.14.3 + JDK 17 |
| 本地数据库 | Room 2.7.2（KSP） |
| 架构 | 单 Activity + MVVM + 单向数据流（UDF） |
| 依赖注入 | 手写 `AppContainer`（没上 Hilt，减少构建复杂度） |
| 异步 | Coroutines + Flow（Room 返回 Flow，数据一变 UI 自动刷新） |
| 最低版本 | minSdk 26（Android 8.0，为了直接用 `java.time`） |

依赖版本集中在 `gradle/libs.versions.toml`（Version Catalog），升级只改这一个文件。

---

## 四、目录结构

```
app/src/main/java/com/whattoeat/app/
├── MainActivity.kt          单 Activity，开屏 → 主界面
├── WhatToEatApp.kt          Application + AppContainer（手写 DI）
├── domain/                  纯 Kotlin 业务逻辑，不依赖 Android
│   ├── Nutrients.kt         营养素模型（加总 / 按克数换算）
│   ├── UserProfile.kt       档案：性别/身高/体重/活动量/目标/手动代谢
│   ├── MealType.kt          餐次（按时间猜默认餐次）
│   ├── Metabolism.kt        BMR、体重趋势回归、自适应 TDEE、每日目标
│   └── TdeeEstimator.kt     TDEE 三档优先级仲裁
├── data/
│   ├── DietRepository.kt    仓库层：查询/写入/换算，实体↔领域模型转换
│   ├── db/                  Room 实体、DAO、数据库
│   └── seed/FoodSeed.kt     内置食物库（50+ 条）
└── ui/
    ├── MainScreen.kt        3 个 Tab + 全屏添加页
    ├── splash/              开屏动画
    ├── diary/               今日页 + ViewModel
    ├── add/                 添加食物（模板 / 自定义）
    ├── summary/             周期汇总 + ViewModel
    ├── settings/            设置（档案 / 模板管理 / 账号占位）
    ├── common/              通用组件（环形进度、营养条、柱状图）
    └── theme/               配色与主题

app/src/test/java/...        单元测试（不依赖模拟器，秒级跑完）
```

### 重要设计：记录存快照

`log_entries` 表里冗余保存了 `foodName` 和营养素，而不是只存 `foodId`。
原因：以后你修改或删除食物模板时，**历史记录不能跟着变**。这是记账类 App 的通用做法。

---

## 五、怎么运行

### 方式 A：Android Studio（推荐新手）
1. Android Studio → Open → 选择 `F:\workspace2\WhatToEat` 目录
2. 等 Gradle Sync 完成（第一次会下载依赖）
3. 连真机或开模拟器 → 点绿色 ▶ 运行

### 方式 B：命令行
```bat
cd F:\workspace2\WhatToEat
set JAVA_HOME=G:\javas\Java17
gradlew.bat assembleDebug           :: 编译
gradlew.bat testDebugUnitTest       :: 跑单元测试
gradlew.bat installDebug            :: 装到已连接的设备
```
产物：`app\build\outputs\apk\debug\app-debug.apk`

> 注意：本机 PATH 里的 `java` 是 1.8，**必须**把 `JAVA_HOME` 指向 JDK 17，否则构建会失败。

---

## 六、下一步（按优先级）

1. **数据导出 / 导入**（JSON 或 CSV）—— 本地优先的 App 必须有备份，否则换手机数据全丢
2. **数据库迁移**：现在开发期用了 `fallbackToDestructiveMigration`，上线前必须改成真正的 `Migration`
3. **记录编辑**（目前只能删除后重加）
4. **食物库**：补足到 2000+ 条（生熟状态要分开！熟米饭和生大米热量差 3 倍）
5. **条码扫描**（CameraX + ML Kit，对接 Open Food Facts）
6. **通知提醒**：WorkManager 定时提醒记录
7. **云同步 + 账号**：Supabase（比 Firebase 更省事且国内可用）
8. **周报 / 月度趋势**：把「按当前速度几周能到目标」做成主界面卡片

---

## 七、免责声明

食物营养数据为公开常识近似值，仅供记录与参考，**不构成医疗或营养学建议**。
