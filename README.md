<div align="center">
  <img src="docs/logo.png" alt="WhatToEat logo" width="128" height="128" />

  <h1 align="center">WhatToEat 🧐😋</h1>

  <p align="center">记录每日饮食，并用长期记录反推真实每日代谢的安卓 App</p>

  <p align="center">
    <img src="https://img.shields.io/badge/Kotlin-2.2.21-purple?style=flat-square" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-00897B?style=flat-square" alt="Compose" />
    <img src="https://img.shields.io/badge/Room-2.7.2-FFB300?style=flat-square" alt="Room" />
    <img src="https://img.shields.io/badge/minSdk-26-green?style=flat-square" alt="minSdk" />
    <img src="https://img.shields.io/badge/license-PolyForm%20Noncommercial%201.0.0-orange?style=flat-square" alt="License" />
  </p>
</div>

---

## 用途

市面上的热量 App 只能告诉你"吃了多少"，却答不出"你一天到底消耗多少"。而 TDEE 每个人差别很大，公式算出来的值经常偏 10% 以上。

本 App 的做法是：你只负责记饮食和体重，它用你的长期数据把真实代谢**反推**出来，再据此给出每日目标热量与营养素目标。

- 记录每日饮食：内置食物模板按克数换算，也支持只填名称 + 热量的自定义记录
- 每日录入体重，用于修正代谢估算
- 按档案（性别 / 身高 / 体重 / 活动量 / 目标）给出每日目标热量，以及蛋白 / 脂肪 / 碳水 / 膳食纤维目标
- 周期汇总：日均摄入、热量缺口或盈余、达标天数、体重趋势

数据只存在本机 SQLite，**不联网、不需要账号**。当前版本 v0.1.0（demo）。

---

## 实现

### 架构

单 Activity + Jetpack Compose，MVVM + 单向数据流。依赖注入用一个手写的 `AppContainer`，没引 Hilt；Room 查询直接返回 `Flow`，写入后 UI 自动刷新，全项目没有手动刷新的地方。

`domain/` 是纯 Kotlin，不依赖 Android，代谢相关的公式全在这里，可直接单元测试。

### 代谢反推（核心）

三档优先级，高到低：

| 优先级 | 来源 | 生效条件 |
|---|---|---|
| 1 | 手动填写值 | 设置页填了 TDEE 就用它，无条件优先 |
| 2 | 自适应反推 | 近 28 天 ≥5 天饮食记录且 ≥2 次体重记录 |
| 3 | 性别平均值兜底 | 男 2250 / 女 1800 kcal |

反推算法对全部体重记录做一元线性回归（最小二乘）求每日变化，避免首尾相减被水分波动带偏：

```
TDEE = 平均日摄入 − (kg/天 × 7700)
```

每日目标（`DailyTargets.from`）：

```
目标热量 = TDEE + 每日热量差（默认按目标 −400 / 0 / +250，可在设置里改），下限 1200 kcal
蛋白 = 体重 × g/kg（默认 1.6，可改）
脂肪 = 目标热量 × 25% / 9
碳水 = 剩余热量 / 4
纤维 = 目标热量 / 1000 × 14 g
```

需要公式参考值时用 Mifflin-St Jeor 算 BMR 再乘活动系数。代码在 `domain/Metabolism.kt`、`domain/TdeeEstimator.kt`。

### 数据模型

`log_entries` 冗余保存 `foodName` 和营养素，而不是只存 `foodId`。改或删食物模板时历史记录不受影响 —— 记账类应用的标准做法。

### 技术栈

| 用途 | 选型 |
|---|---|
| 语言 | Kotlin 2.2.21 |
| UI | Jetpack Compose + Material 3 |
| 构建 | AGP 8.13.2 + Gradle 8.14.3 + JDK 17（本机用 Android Studio 自带 JBR 21 亦可） |
| 数据库 | Room 2.7.2（KSP） |
| 异步 | Coroutines + Flow |
| 最低版本 | minSdk 26（Android 8.0，直接用 `java.time`） |

依赖版本集中在 `gradle/libs.versions.toml`。

### 目录结构

```
app/src/main/java/com/whattoeat/app/
├── MainActivity.kt        单 Activity
├── WhatToEatApp.kt        Application + AppContainer（手写 DI）
├── domain/                纯 Kotlin 业务逻辑
│   ├── Nutrients.kt       营养素模型
│   ├── UserProfile.kt     档案与目标
│   ├── MealType.kt        餐次
│   ├── Metabolism.kt      BMR / 体重回归 / 自适应 TDEE / 每日目标
│   └── TdeeEstimator.kt   三档优先级仲裁
├── data/
│   ├── DietRepository.kt  仓库层
│   ├── db/                Room 实体、DAO、数据库
│   └── seed/FoodSeed.kt   内置食物库（50+ 条）
└── ui/
    ├── MainScreen.kt      三个 Tab + 添加页
    ├── splash/  diary/  add/  summary/  settings/
    ├── common/            环形进度、营养条、柱状图
    └── theme/             配色与主题

app/src/test/java/...       单元测试（31 个用例，不依赖模拟器）
tools/gen_icons.py          生成 logo 与各密度应用图标
```

---

## 运行

Android Studio 直接 Open 项目根目录，Sync 后运行即可。

命令行：

```bat
cd E:\workspace2\WhatToEat
set JAVA_HOME=E:\AndroidStudio\jbr
gradlew.bat assembleDebug        :: 编译
gradlew.bat testDebugUnitTest    :: 单元测试
gradlew.bat installDebug         :: 装到已连接设备
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

> 本机 PATH 里的 `java` 是 1.8，必须先设 `JAVA_HOME`，否则构建失败。

---

## 许可协议

本项目采用 **[PolyForm Noncommercial License 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0)**，全文见 [LICENSE](LICENSE)。

- **允许**：个人学习、研究、实验、业余项目；慈善、教育、公共研究、公共卫生、环保、政府机构等非商业主体使用；阅读、修改、分发（须随附本协议或其 URL 及 `Required Notice` 版权行）
- **禁止**：任何以商业优势或金钱报酬为目的的使用，包括出售、收费服务、企业内部生产经营使用、广告变现、作为商业产品组成部分；不得再许可或附加额外限制

> 带非商业限制的协议不属于 OSI 认可的开源协议，本项目按「源码可见（Source Available）」发布。

> 商业使用需另行取得书面授权，请通过 [GitHub Issues](https://github.com/feSHt377/what-to-eat/issues) 联系作者。

---

## 免责声明

食物营养数据为公开常识近似值，仅供记录与参考，**不构成医疗或营养学建议**。
