<div align="center">
  <img src="docs/logo.png" alt="WhatToEat logo" width="128" height="128" />

  <h1 align="center">WhatToEat 🧐😋</h1>

  <p align="center">记录每日热量，对照目标判断是否达标，帮助控制饮食的安卓 App</p>

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

**核心用途是记录每天的饮食，判断当天热量是否达到控制目标。** 已摄入、每日目标、剩余可吃或超出的热量直接对照展示；按周期回顾达标天数和累计热量差，帮助持续控制饮食，而不只是记一本流水账。

- 按日期和餐次记录饮食：内置食物模板按克数换算，也可直接填写名称与热量
- 以「摄入不超过每日目标」为达标标准，同时对照蛋白质、脂肪、碳水和膳食纤维目标
- 汇总最近 7 / 14 / 30 天：有记录天的日均摄入、达标天数、累计热量差和体重变化
- **辅助功能**：可记录体重，用长期摄入和体重趋势反推 TDEE，校准每日目标；也可以手动填写 TDEE
- 喝水健康监控：按日记录饮水量，快捷加水、撤销误记，查看每日目标进度和周期达标情况
- ZIP 备份：一键导出或导入档案、食物模板、饮食、体重与饮水记录
- 自动检查 GitHub Releases，有新版本时在应用内下载 APK，完成后自动调起系统安装界面

饮食数据只存在本机 SQLite，**不需要账号**。仅更新检查和 APK 下载会访问公开 GitHub，不上传饮食数据。当前版本 v0.5.0。

---

## 实现

### 架构

单 Activity + Jetpack Compose，MVVM + 单向数据流。依赖注入用一个手写的 `AppContainer`，没引 Hilt；Room 查询直接返回 `Flow`，写入后 UI 自动刷新，全项目没有手动刷新的地方。

`domain/` 是纯 Kotlin，不依赖 Android，摄入汇总、达标判断和目标计算可直接单元测试。

### 饮食记录与达标判断（核心）

食物模板按「每 100 克营养素 × 实际克数 / 100」换算为单条摄入；自定义记录直接保存填写的营养素。当天各餐记录求和得到总摄入，再与同一套 `DailyTargets` 比较：

```
剩余可吃 = 每日目标热量 − 当天总摄入（结果为负即超出目标）
当天达标 = 当天有饮食记录 且 当天总摄入 ≤ 每日目标热量
累计热量差 = Σ(有记录天的总摄入 − 每日目标热量)
```

日均摄入、达标天数和累计热量差均只统计有饮食记录的日期，未记录不按 0 kcal 计入。记录或目标变化后，Room 数据流驱动每日页与周期汇总同步更新。

### 每日目标与代谢估算（辅助）

每日目标由个人档案、热量差设置和 TDEE 计算。TDEE 只是确定目标热量的辅助输入，并非项目的主要记录对象；来源按以下优先级选择：

| 优先级 | 来源 | 生效条件 |
|---|---|---|
| 1 | 手动填写值 | 设置页填了 TDEE 就用它，无条件优先 |
| 2 | 自适应反推 | 近 28 天 ≥5 天饮食记录且 ≥2 次体重记录 |
| 3 | 性别平均值兜底 | 男 2250 / 女 1800 kcal |

其中，自适应反推是可选的校准手段：对体重记录做一元线性回归（最小二乘）求每日变化，减少水分波动的影响，再结合平均摄入估算 TDEE：

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

### 备份与更新

- 设置页通过系统文件选择器导出 ZIP，不需要存储权限。ZIP 包含 `manifest.json` 与版本化的 `data.json`，完整保存五张数据表。
- 导入先读取并校验整个 ZIP，再在 Room 事务内替换数据；校验或写入失败会保留原数据。恢复会覆盖当前数据，操作前有确认提示。
- 「今日」页按日期记录饮水，支持 200 / 250 / 500 ml 快捷加水、自定义水量、撤销和清零。设置页可修改每日饮水目标，默认 2000 ml；「汇总」页展示日均饮水与达标天数，未记录日不计为 0。
- 饮水记录独立于饮食，不计入热量。备份格式版本独立于 Room 版本。新增备份字段使用默认值以兼容旧备份；Room 表结构变化仍必须新增 `Migration`，不能清库代替迁移。
- 更新检查使用公开仓库的 `releases/latest` API，仅在发现更高版本时下载其中的 APK 附件。下载完成后自动调起系统安装界面，由用户确认安装，不内置 GitHub Token。
- 首次更新可能需要用户允许 WhatToEat 安装未知来源应用；授权返回后继续安装。APK 下载到应用私有目录，通过 `FileProvider` 共享给系统安装器，不需要存储权限。

### CI 与发布

`.github/workflows/android.yml` 在 `main` 推送、PR 和手动触发时执行单元测试并构建 Release APK，APK 同时作为 Actions artifact 上传。标签与手动发布会使用固定密钥签名；没有 Secrets 的 PR 只构建未签名包，不能发布。

发布新版本时，先更新 `app/build.gradle.kts` 中的 `versionCode` 与 `versionName`，再推送匹配的版本标签：

```bat
git tag -a v0.5.0 -m "WhatToEat v0.5.0"
git push origin v0.5.0
```

标签构建成功后会自动创建 GitHub Release、生成发布说明并上传签名的 Release APK。CI 会校验签名，并直接发布构建阶段上传的同一个 APK，不重新生成密钥或重新打包。

发布前需要在仓库 **Settings → Secrets and variables → Actions** 配置四个 repository secrets：`RELEASE_KEYSTORE_BASE64`（固定 JKS 的 Base64）、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。缺少任何一项，标签发布会在构建前停止。

本地签名参考 `keystore.properties.example`。真实 `.jks`、`.signing/` 和 `keystore.properties` 均被 Git 忽略，不得提交。密钥库与密码必须离线备份；更换或丢失密钥会使后续 APK 无法覆盖旧版。

从 v0.2.0 Debug 测试包首次切换 Release：先在旧版导出 ZIP，再卸载 Debug 版并安装 Release，最后导入 ZIP。卸载会清空本机数据，不能跳过备份。相同 Release 密钥签名的后续版本可直接覆盖升级。

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
    ├── common/            摄入与目标展示组件
    └── theme/             配色与主题

app/src/test/java/...       单元测试（40 个用例，不依赖模拟器）
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
