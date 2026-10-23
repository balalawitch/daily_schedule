# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概况

单模块 Android 应用「日程表」（`:app`，包名 `love.panyi.forever`），Kotlin + Jetpack Compose（Material3）。按天展示 24 小时时间轴：日程块 + 空闲块，空闲块可挂「后备活动」。内置一套儿童平常日作息模板，可一键套用。

工具链：AGP 9.4.1（使用 AGP 内置 Kotlin 编译，未单独应用 `kotlin-android` 插件）、Gradle 9.6.0、Daemon JVM 25（foojay 自动下载），compileSdk/targetSdk 37，minSdk 26。依赖版本统一在 `gradle/libs.versions.toml`。本目录不是 git 仓库。

## 常用命令（Windows 下用 `gradlew.bat` 或 `./gradlew`）

```bash
./gradlew assembleDebug                 # 构建 debug APK
./gradlew installDebug                  # 安装到已连接设备/模拟器
./gradlew test                          # JVM 单元测试
./gradlew :app:testDebugUnitTest --tests "love.panyi.forever.TimelineTest"                    # 单个测试类
./gradlew :app:testDebugUnitTest --tests "love.panyi.forever.TimelineTest.overlapDetection"   # 单个测试方法
./gradlew connectedAndroidTest          # 仪器测试（需设备）
./gradlew lint
```

release 构建通过 `optimization { enable = true }` 启用 R8，keep 规则放在 `app/src/main/keepRules/`（新版 AGP 写法，不是 `proguard-rules.pro`）。

## 架构

刻意保持极简：没有 ViewModel、DI、导航或数据库，全部代码在 `app/src/main/java/love/panyi/forever/`。

- **`Schedule.kt` —— 纯逻辑层（无 Android 依赖，可在 JVM 测试）**
  - 时间统一用「当天分钟数」`Int` 表示，区间为 `[start, end)`，`end` 可为 `DAY_MINUTES`(1440) 表示 24:00。
  - `buildTimeline()` 把一天切成首尾相接、覆盖 0–1440 的 `TimeBlock` 列表：日程为 `Busy`，日程间空隙为 `Free` 且按整点再切开（无日程时即 24 个 1h 块）。
  - `hasOverlap()` 用于校验；`weekdayRoutine()` 是作息模板，修改后需保证 `TimelineTest.weekdayRoutine_isValidAndNonOverlapping` 仍通过。
- **`ScheduleStore.kt` —— 持久化**：SharedPreferences（文件名 `schedule`）按日期存 JSON，键为 `s_<yyyy-MM-dd>`（日程数组）与 `b_<yyyy-MM-dd>`（后备活动对象）。每次修改都整天覆盖写入。
- **`ScheduleScreen.kt` —— 全部 UI 与状态**：在 composable 内用 `remember(date)` 从 store 读出当天数据，修改时先更新本地 state 再调 `store.save*`；`MainActivity` 只负责创建 store 并挂上主题。

### 需要注意的隐含约束

- **日程不得重叠**：`LazyColumn` 以 `block.start` 作为 item key，重叠会导致 key 重复崩溃；保存前由 `hasOverlap` 校验拦截。
- **后备活动以空闲块的起始分钟为键**（`Map<Int, String>`），并非绑定到具体时间段；增删日程改变空闲块切分后，旧键可能不再对应任何块（静默隐藏，不会被清理）。
- `Schedule.id == 0L` 表示「新建中」，保存时用 `System.currentTimeMillis()` 生成 id；`weekdayRoutine()` 用 `base + i` 保证批量 id 唯一。
- 时间选择器里结束时间选 `00:00` 会被当作 24:00。
- UI 文案直接以中文硬编码在 Kotlin 中（未使用 `strings.xml`），沿用此做法即可。
