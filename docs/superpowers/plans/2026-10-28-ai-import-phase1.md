# AI 导入日程 · 第①期 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用户粘贴一段自然语言，云端 OpenAI 兼容模型生成日程，在时间轴预览里检查，确认后每组存成一个模板；日程新增「详细说明」，在此刻卡片里显示。

**Architecture:** Model 层新增纯逻辑（AI 输出解析、模板重名、JSON 转换，可在 JVM 上测试）、模板存储、AI 设置（Keystore 加密 Key）和 `ScheduleGenerator` 接口及其云端实现；新增 `ImportViewModel` 管理「输入 → 生成 → 预览 → 存模板」和引擎设置；View 层新增 `ImportSheets.kt`，由 `ScheduleScreen` 挂载。依赖方向保持 View → ViewModel → Model。

**Tech Stack:** Kotlin、Jetpack Compose Material3、`HttpURLConnection` + `org.json`（不加新的运行时依赖）、Android Keystore（AES/GCM）、JUnit 4。

**Spec:** `docs/superpowers/specs/2026-10-27-ai-import-design.md`（本计划只实现 §9 第①期）

## Global Constraints

- 遵守 `CLAUDE.md` 全部约束：MVVM，依赖方向只能是 View → ViewModel → Model；不引入 DI、导航库或数据库；UI 文案用中文硬编码；ViewModel 不依赖具体实现类。
- 不新增运行时依赖。只新增测试依赖 `testImplementation("org.json:json:20250517")`（JVM 单元测试里 Android 的 `org.json` 不可用）。
- 云端配置只有三个值：`url`、`apiKey`、`model`。`url` 必须以 `https://` 开头（D12）。
- API Key 用 Android Keystore AES/GCM 加密后才写进 SharedPreferences，永远不明文落盘（D7）。
- 输入最多 2000 字（D10）。
- AI 输出校验不通过时自动重试 1 次，重试消息里附上错误原因（D3）。
- 跨午夜（`end < start`）截到 24:00（D11）；结束时间 `00:00` 视为 24:00。
- 模板名重复时加后缀「 (2)」「 (3)」……，不覆盖（D1）。存完关闭预览，Snackbar 提示「已存为 N 个模板：A、B」。
- 第①期设置面板里 Gemini Nano、本地 Gemma 两张卡片显示「即将支持」且不可选。
- 在分支 `feature/ai-import` 上开发，每个 Task 一次提交，提交信息末尾加：
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- **跑 JVM 测试**（本机 `testDebugUnitTest` 必然失败，见 CLAUDE.md）：先 `./gradlew :app:compileDebugUnitTestKotlin`，再在 **PowerShell** 里：

  ```powershell
  $c = "E:\gradle-home\caches\modules-2\files-2.1"
  # kotlin-stdlib 用 app 实际依赖的版本（./gradlew :app:dependencies --configuration debugUnitTestRuntimeClasspath 查看）
  $jars = Get-ChildItem $c -Recurse -Include junit-4.13.2.jar,hamcrest-core-1.3.jar,kotlin-stdlib-<版本>.jar,json-20250517.jar |
          Where-Object { $_.Name -notmatch 'sources' } | ForEach-Object FullName
  $b = "app\build\intermediates\built_in_kotlinc"
  $cp = (@("$b\debugUnitTest\compileDebugUnitTestKotlin\classes", "$b\debug\compileDebugKotlin\classes") + $jars) -join ';'
  java -cp $cp org.junit.runner.JUnitCore <测试类全名…>
  ```
  通过的标志：输出末尾是 `OK (N tests)`。下文的「Run tests: X」都指用这个方法跑 X。

## Review Focus

1. 模型把 JSON 包在 ```json 代码块里，或前后带一句说明 → 仍能解析成功。（Task 3 `parsePlan_stripsFencesAndProse`）
2. 时间写成 `7:10`（一位数小时）、`24:00`，或结束时间 `00:00` → 正常接受。（Task 3 `parsePlan_acceptsLooseClockFormats`）
3. 「21:30 睡觉到 7:10」这类跨午夜条目 → 截成 21:30–24:00，不报错。（Task 3 `parsePlan_clipsCrossMidnightToDayEnd`）
4. 旧版本存的日程 JSON 没有 `description` 字段 → 读出来 `description == ""`，不崩溃。（Task 1 `schedulesFromJson_oldDataWithoutDescription`）
5. 用户填的 url 末尾带 `/`、已经以 `/chat/completions` 结尾，或用的是 `http://` → 拼出正确地址 / 明确拒绝。（Task 5 `chatEndpoint_*`）

---

### Task 1: 日程的 description 字段 + 共用校验 + 日程 JSON 转换

**Files:**
- Modify: `app/src/main/java/love/panyi/forever/data/Schedule.kt`
- Create: `app/src/main/java/love/panyi/forever/data/ScheduleJson.kt`
- Modify: `app/src/main/java/love/panyi/forever/data/ScheduleStore.kt`（`schedules()` / `saveSchedules()` 改用转换函数）
- Modify: `app/src/main/java/love/panyi/forever/ui/schedule/ScheduleViewModel.kt`（`saveSchedule` 改用 `validateSchedule`）
- Modify: `app/build.gradle.kts`、`gradle/libs.versions.toml`（测试依赖 `org.json:json:20250517`）
- Test: `app/src/test/java/love/panyi/forever/data/ScheduleJsonTest.kt`、`app/src/test/java/love/panyi/forever/data/TimelineTest.kt`

**Interfaces:**
- Produces:
  - `data class Schedule(val id: Long, val start: Int, val end: Int, val title: String, val description: String = "")`
  - `fun validateSchedule(s: Schedule, others: List<Schedule>): String?`（Schedule.kt）：返回 `"请填写标题"` / `"结束时间需晚于开始时间"` / `"与已有日程时间重叠"` / `null`，顺序和文案与现有 `ScheduleViewModel.saveSchedule` 完全一致；重叠检查用 `hasOverlap(others, s.start, s.end, ignoreId = s.id)`。
  - `fun schedulesToJson(list: List<Schedule>): String`、`fun schedulesFromJson(json: String): List<Schedule>`（ScheduleJson.kt）：字段 `id/start/end/title/description`，读 `description` 用 `optString("description")`。

- [ ] **Step 1: 建分支** `git switch -c feature/ai-import`
- [ ] **Step 2: 加测试依赖**：在 `libs.versions.toml` 加 `json = "20250517"` 与 `org-json = { group = "org.json", name = "json", version.ref = "json" }`，`app/build.gradle.kts` 加 `testImplementation(libs.org.json)`。
- [ ] **Step 3: 写失败的测试**

```kotlin
// ScheduleJsonTest.kt
@Test fun schedules_roundTripKeepsDescription() {
    val list = listOf(Schedule(1, 420, 450, "起床", "先喝水"), Schedule(2, 480, 540, "上学"))
    assertEquals(list, schedulesFromJson(schedulesToJson(list)))
}
@Test fun schedulesFromJson_oldDataWithoutDescription() {
    val old = """[{"id":5,"start":60,"end":120,"title":"午睡"}]"""
    assertEquals(listOf(Schedule(5, 60, 120, "午睡", "")), schedulesFromJson(old))
}

// TimelineTest.kt 追加
@Test fun validateSchedule_messages() {
    val other = Schedule(1, h(9), h(10), "开会")
    assertEquals("请填写标题", validateSchedule(Schedule(2, h(11), h(12), " "), listOf(other)))
    assertEquals("结束时间需晚于开始时间", validateSchedule(Schedule(2, h(12), h(12), "x"), listOf(other)))
    assertEquals("与已有日程时间重叠", validateSchedule(Schedule(2, h(9, 30), h(11), "x"), listOf(other)))
    assertNull(validateSchedule(other.copy(end = h(10, 30)), listOf(other)))  // 编辑自己不算重叠
}
```

- [ ] **Step 4: Run tests** `love.panyi.forever.data.ScheduleJsonTest love.panyi.forever.data.TimelineTest` → 编译失败（函数未定义）。
- [ ] **Step 5: 实现** `Schedule.description`、`validateSchedule`、`schedulesToJson/FromJson`；`ScheduleStore` 的日程读写改为调用这两个函数；`ScheduleViewModel.saveSchedule` 的 `when` 换成 `validateSchedule(s, state.schedules)`。
- [ ] **Step 6: Run tests** `love.panyi.forever.data.ScheduleJsonTest love.panyi.forever.data.TimelineTest love.panyi.forever.data.ProgressTest` → `OK`。
- [ ] **Step 7: Commit** `feat: add schedule description and shared validation`

### Task 2: 编辑弹窗与此刻卡片显示 description

**Files:**
- Modify: `app/src/main/java/love/panyi/forever/ui/schedule/ScheduleDialogs.kt:51-102`（`ScheduleDialog`）
- Modify: `app/src/main/java/love/panyi/forever/ui/schedule/ScheduleScreen.kt:493-498`（`NowCard` 中 `current != null` 分支）

**Interfaces:**
- Consumes: Task 1 的 `Schedule.description`。

- [ ] **Step 1: `ScheduleDialog`**：标题输入框下面加多行 `OutlinedTextField`，label「说明（选填）」，`minLines = 2`、`maxLines = 5`；保存时 `initial.copy(title = title.trim(), start = start, end = end, description = description.trim())`。
- [ ] **Step 2: `NowCard`**：在事项名 / 做法那一列下面，`current.description` 非空时显示一段 `bodyMedium`、颜色 `colors.onPrimary`；默认 `maxLines = 4` + `TextOverflow.Ellipsis`，点击这段文字切换成全部显示（`remember(current.id)` 保存展开状态）。时间轴行、`NowInlineRow`、`FloatingNow` 不显示说明。
- [ ] **Step 3: 验证** `./gradlew assembleDebug` 成功；在模拟器上给今天正在进行的日程填一段说明 → 此刻卡片里能看到，长文字先显示 4 行、点击展开。
- [ ] **Step 4: Commit** `feat: edit schedule description and show it on the now card`

### Task 3: 解析 AI 输出

**Files:**
- Modify: `app/src/main/java/love/panyi/forever/data/Schedule.kt`（`ScheduleTemplate`）
- Modify: `app/src/main/java/love/panyi/forever/data/ScheduleJson.kt`
- Test: `app/src/test/java/love/panyi/forever/data/PlanParseTest.kt`

**Interfaces:**
- Produces:
  - `data class ScheduleTemplate(val name: String, val items: List<Schedule>)`（Schedule.kt）
  - `class PlanParseException(message: String, val empty: Boolean = false) : Exception(message)`（ScheduleJson.kt）。`empty = true` 表示「没找到日程」，不重试。
  - `fun parsePlan(raw: String, idBase: Long = System.currentTimeMillis()): List<ScheduleTemplate>`：失败时抛 `PlanParseException`，`message` 是给用户 / 给模型重试看的中文原因。

**规则（规格 §6.2）：**
- 取 `raw` 里第一个 `{` 到最后一个 `}` 之间的子串再解析；找不到或解析失败 → `PlanParseException("不是合法的 JSON")`。
- 根对象没有 `days` 数组 → `PlanParseException("缺少 days")`。
- 跳过没有 items 的组；全部为空 → `PlanParseException("这段文字里没有找到日程安排", empty = true)`。
- `label` 去空白后为空 → 依次用「模板 1」「模板 2」……（按最终输出的组序号）。
- 时间正则 `^(\d{1,2}):(\d{2})$`，小时 0–24、分钟 0–59，`24` 只能配 `00`；`start` 不能是 24:00。`end` 为 `00:00` → 1440；`end < start` → 1440（D11）；`end == start` → 报错。
- `title` 去空白后为空 → 报错；`description` 用 `optString` 并 trim。
- 组内按 `start` 排序后逐条用 `hasOverlap` 检查，重叠报错，原因写成「16:00–17:00 与 16:30–17:30 重叠」。
- 所有条目 id 从 `idBase` 起递增，跨组也不重复。

- [ ] **Step 1: 写失败的测试**

```kotlin
private fun day(label: String, vararg items: String) =
    """{"label":"$label","items":[${items.joinToString(",")}]}"""
private fun item(s: String, e: String, t: String, d: String? = null) =
    """{"start":"$s","end":"$e","title":"$t"${d?.let { ",\"description\":\"$it\"" } ?: ""}}"""
private fun plan(vararg days: String) = """{"days":[${days.joinToString(",")}]}"""

@Test fun parsePlan_twoGroups() {
    val r = parsePlan(plan(day("周一–周五", item("07:00", "07:30", "起床", "先喝水")), day("周末", item("10:00", "11:00", "钢琴"))), idBase = 100)
    assertEquals(listOf("周一–周五", "周末"), r.map { it.name })
    assertEquals(Schedule(100, 420, 450, "起床", "先喝水"), r[0].items[0])
    assertEquals(Schedule(101, 600, 660, "钢琴", ""), r[1].items[0])
}
@Test fun parsePlan_stripsFencesAndProse() {
    val raw = "好的，结果如下：\n```json\n" + plan(day("每天", item("08:00", "09:00", "早读"))) + "\n```\n希望有帮助"
    assertEquals(1, parsePlan(raw).single().items.size)
}
@Test fun parsePlan_acceptsLooseClockFormats() {
    val r = parsePlan(plan(day("每天", item("7:10", "7:15", "起床"), item("23:00", "00:00", "睡前"), item("06:00", "07:00", "晨练"))))
    assertEquals(listOf(360 to 420, 430 to 435, 1380 to 1440), r.single().items.sortedBy { it.start }.map { it.start to it.end })
    assertEquals(1440, parsePlan(plan(day("每天", item("23:00", "24:00", "x")))).single().items.single().end)
}
@Test fun parsePlan_clipsCrossMidnightToDayEnd() {
    val s = parsePlan(plan(day("每天", item("21:30", "07:10", "睡觉")))).single().items.single()
    assertEquals(1290 to 1440, s.start to s.end)
}
@Test fun parsePlan_blankLabelGetsDefaultName() {
    assertEquals("模板 1", parsePlan(plan(day(" ", item("08:00", "09:00", "a")))).single().name)
}
@Test fun parsePlan_overlapIsRejectedWithTimes() {
    val e = assertThrows(PlanParseException::class.java) {
        parsePlan(plan(day("每天", item("16:00", "17:00", "作业"), item("16:30", "17:30", "练琴"))))
    }
    assertEquals("16:00–17:00 与 16:30–17:30 重叠", e.message)
}
@Test fun parsePlan_emptyIsFlagged() {
    val e = assertThrows(PlanParseException::class.java) { parsePlan(plan(day("每天"))) }
    assertTrue(e.empty)
}
@Test fun parsePlan_invalidInputsAreRejected() {
    for (raw in listOf("没有 JSON", """{"x":1}""", plan(day("每天", item("25:00", "26:00", "a"))),
                       plan(day("每天", item("08:00", "08:00", "a"))), plan(day("每天", item("08:00", "09:00", " "))),
                       plan(day("每天", item("24:00", "24:00", "a"))))) {
        val e = assertThrows(PlanParseException::class.java) { parsePlan(raw) }
        assertFalse(raw, e.empty)
    }
}
```

- [ ] **Step 2: Run tests** `love.panyi.forever.data.PlanParseTest` → 编译失败。
- [ ] **Step 3: 实现** `ScheduleTemplate`、`PlanParseException`、`parsePlan`。
- [ ] **Step 4: Run tests** `love.panyi.forever.data.PlanParseTest` → `OK (8 tests)`。
- [ ] **Step 5: Commit** `feat: parse AI schedule plans into templates`

### Task 4: 模板存储

**Files:**
- Modify: `app/src/main/java/love/panyi/forever/data/Schedule.kt`（`uniqueName`）
- Modify: `app/src/main/java/love/panyi/forever/data/ScheduleJson.kt`（模板转换）
- Modify: `app/src/main/java/love/panyi/forever/data/ScheduleRepository.kt`、`ScheduleStore.kt`
- Test: `app/src/test/java/love/panyi/forever/data/ScheduleJsonTest.kt`、`TimelineTest.kt`

**Interfaces:**
- Consumes: Task 3 的 `ScheduleTemplate`。
- Produces:
  - `fun uniqueName(name: String, taken: Set<String>): String`：不重名原样返回；否则依次试 `"$name (2)"`、`"$name (3)"`……
  - `fun templatesToJson(list: List<ScheduleTemplate>): String` / `fun templatesFromJson(json: String): List<ScheduleTemplate>`：格式 `[{"name":"…","items":[{"start":420,"end":450,"title":"…","description":"…"}]}]`，不存 id；读出时 id 用 `0L` 起的下标（模板 id 无意义）。
  - `ScheduleRepository` 新增 `suspend fun loadTemplates(): List<ScheduleTemplate>`、`suspend fun saveTemplates(templates: List<ScheduleTemplate>)`；`ScheduleStore` 用键 `templates`（缺省 `"[]"`）整体覆盖写入。

- [ ] **Step 1: 写失败的测试**

```kotlin
// TimelineTest.kt
@Test fun uniqueName_addsSuffix() {
    assertEquals("周末", uniqueName("周末", setOf("周一–周五")))
    assertEquals("周末 (2)", uniqueName("周末", setOf("周末")))
    assertEquals("周末 (3)", uniqueName("周末", setOf("周末", "周末 (2)")))
}
// ScheduleJsonTest.kt
@Test fun templates_roundTrip() {
    val t = listOf(ScheduleTemplate("周末", listOf(Schedule(0, 600, 660, "钢琴", "带琴谱"))))
    assertEquals(t, templatesFromJson(templatesToJson(t)))
    assertEquals(emptyList<ScheduleTemplate>(), templatesFromJson("[]"))
}
```

- [ ] **Step 2: Run tests** `love.panyi.forever.data.ScheduleJsonTest love.panyi.forever.data.TimelineTest` → 编译失败。
- [ ] **Step 3: 实现**上述函数、接口方法和 `ScheduleStore` 实现。
- [ ] **Step 4: Run tests** 同 Step 2 → `OK`。
- [ ] **Step 5: Commit** `feat: store schedule templates locally`

### Task 5: AI 设置与云端生成器

**Files:**
- Create: `app/src/main/java/love/panyi/forever/data/AiSettings.kt`
- Create: `app/src/main/java/love/panyi/forever/data/ScheduleGenerator.kt`
- Modify: `app/src/main/java/love/panyi/forever/data/ScheduleJson.kt`（云端请求 / 响应的纯函数）
- Modify: `app/src/main/AndroidManifest.xml`（`<uses-permission android:name="android.permission.INTERNET" />`）
- Test: `app/src/test/java/love/panyi/forever/data/CloudJsonTest.kt`

**Interfaces:**
- Consumes: Task 3 的 `parsePlan`、`PlanParseException`、`ScheduleTemplate`。
- Produces:
  - `enum class AiEngine(val label: String) { NANO("Gemini Nano"), GEMMA("本地 Gemma"), CLOUD("云端 API") }`
  - `data class CloudConfig(val url: String = "", val apiKey: String = "", val model: String = "")`，`val isComplete get()` = 三项都非空白且 `url.trim().startsWith("https://")`。
  - `interface AiSettings { val engine: AiEngine; fun selectEngine(e: AiEngine); fun cloudConfig(): CloudConfig; fun saveCloudConfig(c: CloudConfig) }`；`class AiSettingsStore(context: Context) : AiSettings`：SharedPreferences 文件 `ai_settings`，键 `engine`（缺省 `CLOUD`）、`cloud_url`、`cloud_model`、`cloud_key`（Keystore 别名 `ai_api_key` 的 AES/GCM/NoPadding 密文，Base64(iv + ciphertext)）。解密失败（例如密钥被系统清掉）当作空 Key。
  - `class AiException(message: String) : Exception(message)`：给用户看的错误（网络、HTTP）。
  - `interface ScheduleGenerator { suspend fun generate(text: String, retryHint: String?): List<ScheduleTemplate>; suspend fun ping() {} }`：`generate` 在模型输出不合格时抛 `PlanParseException`，其他失败抛 `AiException`；`ping` 用于「测试连接」，默认空实现，失败抛 `AiException`。
  - `class CloudGenerator(private val config: CloudConfig) : ScheduleGenerator`：`ping()` 发一条 user 消息「ping」的 chat 请求，HTTP 200 即成功，不解析内容。
  - `const val PLAN_SYSTEM_PROMPT: String`、`fun planUserMessage(text: String, retryHint: String?): String`（ScheduleGenerator.kt）。
  - 纯函数（ScheduleJson.kt）：`fun chatEndpoint(url: String): String`、`fun chatRequestJson(model: String, system: String, user: String): String`、`fun chatResponseContent(body: String): String`、`fun httpErrorMessage(code: Int, body: String): String`。

**细节：**
- `chatEndpoint`：trim、去掉末尾 `/`；已经以 `/chat/completions` 结尾就原样返回，否则补上 `/chat/completions`；不是 `https://` 开头抛 `AiException("接口地址必须以 https:// 开头")`。
- `chatRequestJson`：`{"model":…,"temperature":0.2,"messages":[{"role":"system","content":…},{"role":"user","content":…}]}`。
- `chatResponseContent`：取 `choices[0].message.content`；拿不到抛 `PlanParseException("模型没有返回内容")`。
- `httpErrorMessage`：401/403 → `"API Key 无效"`；其他 → `"请求失败（$code）"`，body 里有 `error.message` 时追加 `"：$message"`。
- 网络：`HttpURLConnection`，POST，`Authorization: Bearer <key>`，`Content-Type: application/json; charset=utf-8`，连接 / 读取超时都是 60 秒；放在 `runInterruptible(Dispatchers.IO)` 里，这样取消协程能打断阻塞读取。`IOException` → `AiException("网络连接失败，请检查网络")`。
- `PLAN_SYSTEM_PROMPT` 按规格 §6.3 写成中文：只提取日程相关内容；只输出 JSON（给出 §6.2 的结构）；同一组时间不重叠；没说结束时间的按常识估；`title` 简短、可用「·」分开事项名和做法、`⏰` 表示掐点；细节写进 `description`；原文区分不同的天就拆成多个 `days`；附 1 个输入 → 输出示例。`planUserMessage` 在 `retryHint` 非空时追加「上次的输出有问题：$retryHint，请修正后重新输出完整 JSON」。

- [ ] **Step 1: 写失败的测试**

```kotlin
@Test fun chatEndpoint_appendsPath() =
    assertEquals("https://api.deepseek.com/v1/chat/completions", chatEndpoint(" https://api.deepseek.com/v1/ "))
@Test fun chatEndpoint_keepsFullPath() =
    assertEquals("https://x.com/v1/chat/completions", chatEndpoint("https://x.com/v1/chat/completions"))
@Test fun chatEndpoint_rejectsHttp() {
    assertThrows(AiException::class.java) { chatEndpoint("http://10.0.2.2:11434/v1") }
}
@Test fun chatRequestJson_shape() {
    val o = JSONObject(chatRequestJson("m1", "sys", "usr"))
    assertEquals("m1", o.getString("model"))
    assertEquals(0.2, o.getDouble("temperature"), 1e-9)
    val msgs = o.getJSONArray("messages")
    assertEquals("system", msgs.getJSONObject(0).getString("role"))
    assertEquals("usr", msgs.getJSONObject(1).getString("content"))
}
@Test fun chatResponseContent_readsFirstChoice() =
    assertEquals("{}", chatResponseContent("""{"choices":[{"message":{"role":"assistant","content":"{}"}}]}"""))
@Test fun chatResponseContent_missingIsParseError() {
    assertThrows(PlanParseException::class.java) { chatResponseContent("""{"choices":[]}""") }
}
@Test fun httpErrorMessage_cases() {
    assertEquals("API Key 无效", httpErrorMessage(401, ""))
    assertEquals("请求失败（429）：Rate limit", httpErrorMessage(429, """{"error":{"message":"Rate limit"}}"""))
    assertEquals("请求失败（500）", httpErrorMessage(500, "<html>"))
}
@Test fun cloudConfig_requiresHttpsAndAllFields() {
    assertTrue(CloudConfig("https://a.com", "k", "m").isComplete)
    assertFalse(CloudConfig("http://a.com", "k", "m").isComplete)
    assertFalse(CloudConfig("https://a.com", " ", "m").isComplete)
}
```

- [ ] **Step 2: Run tests** `love.panyi.forever.data.CloudJsonTest` → 编译失败。
- [ ] **Step 3: 实现**本 Task 的全部接口；Manifest 加 INTERNET 权限。
- [ ] **Step 4: Run tests** `love.panyi.forever.data.CloudJsonTest love.panyi.forever.data.PlanParseTest` → `OK`；`./gradlew assembleDebug` 成功。
- [ ] **Step 5: Commit** `feat: add AI settings and OpenAI-compatible cloud generator`

### Task 6: ImportViewModel

**Files:**
- Create: `app/src/main/java/love/panyi/forever/ui/schedule/ImportViewModel.kt`（`ImportUiState` 写在同一文件）
- Modify: `app/src/main/java/love/panyi/forever/MainActivity.kt`

**Interfaces:**
- Consumes: Task 1 `validateSchedule`；Task 3 `ScheduleTemplate`、`PlanParseException`；Task 4 `uniqueName`、`ScheduleRepository.loadTemplates/saveTemplates`；Task 5 `AiSettings`、`AiEngine`、`CloudConfig`、`ScheduleGenerator`、`AiException`。
- Produces（Task 7 使用）：

```kotlin
data class ImportUiState(
    val open: Boolean = false,          // 输入面板是否打开
    val generating: Boolean = false,
    val error: String? = null,          // 输入面板里显示的错误
    val engine: AiEngine = AiEngine.CLOUD,
    val cloud: CloudConfig = CloudConfig(),
    val showSettings: Boolean = false,
    val testing: Boolean = false,
    val testResult: String? = null,     // "连接成功" 或错误原因
    val preview: List<ScheduleTemplate>? = null,  // 非 null 时显示预览
    val tab: Int = 0,
    val editing: Schedule? = null,      // 预览里正在编辑的条目
    val editError: String? = null,
    val message: String? = null,        // Snackbar，显示后调用 messageShown()
) { val ready get() = engine == AiEngine.CLOUD && cloud.isComplete }

class ImportViewModel(
    private val repository: ScheduleRepository,
    private val settings: AiSettings,
    private val createGenerator: (AiEngine, CloudConfig) -> ScheduleGenerator?,
) : ViewModel() {
    val uiState: StateFlow<ImportUiState>
    fun open(); fun close()
    fun generate(text: String); fun cancelGenerate(); fun regenerate()
    fun selectTab(index: Int)
    fun editItem(s: Schedule); fun dismissEdit(); fun saveItem(s: Schedule); fun deleteItem()
    fun saveTemplates(); fun closePreview()
    fun openSettings(); fun closeSettings()
    fun selectEngine(e: AiEngine); fun saveCloud(c: CloudConfig); fun testCloud(c: CloudConfig)
    fun messageShown()
}
```

**行为：**
- `open()`：打开输入面板，从 `settings` 读出 engine 和 cloud；`ready == false` 时同时打开设置面板。
- `generate(text)`：`text.trim()` 为空或超过 2000 字直接返回；记下 `lastText`；`generating = true` 启动 Job：`createGenerator(...)` 为 null → `error = "请先在设置里配置 AI 引擎"`。调用 `generate(text, null)`；捕获 `PlanParseException` 且 `!empty` → 再调一次 `generate(text, e.message)`。最终结果：成功 → `preview = 结果, tab = 0, open = false`；`PlanParseException.empty` → `error = e.message`；第二次仍是 `PlanParseException` → `error = "没能理解这段文字，换个说法试试：${e.message}"`；`AiException` → `error = e.message`。`finally` 里 `generating = false`。
- `cancelGenerate()`：取消 Job，`generating = false`，不显示错误。
- `regenerate()`：关闭预览，用 `lastText` 重新 `generate`（失败时重新打开输入面板显示错误）。
- `saveItem(s)`：`validateSchedule(s, 当前组 items)`，有错写 `editError`；否则替换（或新增）进当前组并关闭编辑。`deleteItem()` 从当前组删掉 `editing`；组被删空时整组移除，`tab` 收回到合法范围，全部删空则关闭预览。
- `saveTemplates()`：`loadTemplates()` → 对每组依次 `uniqueName(name, 已有名 + 本次已用名)` → 追加后 `saveTemplates()` → `preview = null`，`message = "已存为 ${n} 个模板：${names.joinToString("、")}"`。
- `selectEngine(e)`：第①期只接受 `CLOUD`（其他直接忽略）。`saveCloud(c)`：trim 三项后保存并更新 state。`testCloud(c)`：`testing = true`，`createGenerator(AiEngine.CLOUD, c)` 为 null 时 `testResult = "请填写完整，地址需以 https:// 开头"`；否则调用其 `ping()`，成功写「连接成功」，`AiException` 写 `e.message`。
- `MainActivity`：用和 `ScheduleViewModel` 同样的 `by viewModels { viewModelFactory { initializer { … } } }` 创建 `ImportViewModel(ScheduleStore(applicationContext), AiSettingsStore(applicationContext)) { engine, cloud -> if (engine == AiEngine.CLOUD && cloud.isComplete) CloudGenerator(cloud) else null }`，作为第二个参数传给 `ScheduleScreen`。

- [ ] **Step 1: 实现** `ImportUiState`、`ImportViewModel`、`MainActivity` 接线（`ScheduleScreen` 签名在 Task 7 改；本步先让 `ScheduleScreen(viewModel, importViewModel)` 接收参数但不使用）。
- [ ] **Step 2: 验证** `./gradlew assembleDebug` 成功；所有 JVM 测试仍 `OK`。
- [ ] **Step 3: Commit** `feat: add ImportViewModel for AI schedule import`

### Task 7: 导入界面

**Files:**
- Create: `app/src/main/java/love/panyi/forever/ui/schedule/ImportSheets.kt`
- Modify: `app/src/main/java/love/panyi/forever/ui/schedule/ScheduleScreen.kt`（`WeekHeader` 加入口；`ScheduleScreen` 挂载导入界面与 `SnackbarHost`；`TimeRow` 由 `private` 改为 `internal`）

**Interfaces:**
- Consumes: Task 6 的 `ImportUiState` 与 `ImportViewModel` 全部公开方法；Task 1 `ScheduleDialog`（已有）；`buildTimeline`、`TimeRow`。
- Produces: `@Composable fun ImportScreen(viewModel: ImportViewModel, snackbar: SnackbarHostState)`：收集 `uiState`，渲染下面四块；其余 composable 只接收数据和回调。

**界面（规格 §6.5，样式遵循设计系统「日程表」与 `Color.kt`）：**
- **入口**：`WeekHeader` 新增参数 `onImport: () -> Unit`，在 📅 `IconButton` 左边加一个 `IconButton`，内容为文字「✨」，`contentDescription = "AI 导入"`。
- **输入面板**（`ModalBottomSheet`）：标题「用一段话生成日程」；多行 `OutlinedTextField`（`minLines = 6`，超过 2000 字不再接受输入，右下角 `supportingText` 显示「${n} / 2000」；输入内容用 `rememberSaveable` 留在 composable 里）；一行「AI 引擎：云端 API · ${model} ›」（未配置时为「AI 引擎：未配置 ›」），点击 `openSettings()`；`error` 用 error 色显示；按钮「生成」（`ready` 且输入非空时可点）/「取消」。`generating` 时按钮区换成 `CircularProgressIndicator` +「正在理解…」+「取消」（`cancelGenerate()`）。
- **设置面板**（`ModalBottomSheet`）：三张卡片。Gemini Nano、本地 Gemma 置灰，副标题「即将支持」；云端卡片含 `url`（placeholder `https://api.deepseek.com/v1`）、`API Key`（`PasswordVisualTransformation`）、`模型`（placeholder `deepseek-chat`）三个输入框，按钮「测试连接」（`testing` 时转圈，结果显示 `testResult`）和「保存」（`saveCloud` 后 `closeSettings`）。url 非 https 时输入框下提示「必须以 https:// 开头」。
- **预览**（`Dialog` + `DialogProperties(usePlatformDefaultWidth = false)`，全屏 `Surface`）：顶栏「预览生成结果」+ 关闭（`closePreview()`）；多组时 `ScrollableTabRow` 显示各组 `name`；`tertiaryContainer` 提示条「AI 生成，请检查。点某一条可以修改。」；`LazyColumn` 用 `buildTimeline(items)` 过滤出 `[第一条 start, 最后一条 end)` 范围内的块，用 `TimeRow(block, backup = null, done = false, record = null, onClick = …)` 渲染，key 为 `"b${block.start}"`；点 Busy → `editItem`，点 Free 无动作；底部「重新生成」（`regenerate()`）/「存为模板」（`saveTemplates()`）。`editing != null` 时显示 `ScheduleDialog(initial = editing, error = editError, onSave = saveItem, onDelete = deleteItem, onDismiss = dismissEdit)`。
- **Snackbar**：`ScheduleScreen` 的 `Scaffold` 加 `snackbarHost = { SnackbarHost(snackbar) }`；`ImportScreen` 里 `LaunchedEffect(state.message)` 显示后调用 `messageShown()`。

- [ ] **Step 1: 实现** `ImportSheets.kt` 与 `ScheduleScreen.kt` 的改动。
- [ ] **Step 2: 验证** `./gradlew assembleDebug` 成功；所有 JVM 测试仍 `OK`。
- [ ] **Step 3: Commit** `feat: add AI import sheets, preview and settings`

### Task 8: 端到端验证

- [ ] **Step 1:** 跑全部 JVM 测试：`love.panyi.forever.data.TimelineTest love.panyi.forever.data.ProgressTest love.panyi.forever.data.ScheduleJsonTest love.panyi.forever.data.PlanParseTest love.panyi.forever.data.CloudJsonTest` → `OK`。
- [ ] **Step 2:** `./gradlew assembleDebug lint` 成功，lint 没有新增 error。
- [ ] **Step 3:** 模拟器手动走一遍（需要主人提供一个 https 的 OpenAI 兼容服务的 url / Key / 模型）：
  1. 首次点 ✨ → 自动弹出设置面板；填 http 地址 → 提示必须 https；填正确三项 →「测试连接」显示「连接成功」→ 保存。
  2. 粘贴规格 §4 草图里的那段话 → 生成 → 预览出现「周一–周五 / 周末」两个标签；点一条改标题、填说明 → 保存成功；故意改成重叠 → 弹窗里提示「与已有日程时间重叠」。
  3. 「存为模板」→ Snackbar「已存为 2 个模板：…」；再生成一次同样的话并保存 → 名字带「 (2)」（可用 `adb shell run-as love.panyi.forever cat shared_prefs/schedule.xml` 查看 `templates`）。
  4. 关掉网络再生成 →「网络连接失败，请检查网络」；Key 填错 →「API Key 无效」；生成中点「取消」→ 回到可输入状态、无报错。
  5. `adb shell run-as love.panyi.forever cat shared_prefs/ai_settings.xml` → 看不到明文 Key。
  6. 旋转屏幕：预览和设置面板状态保持（ViewModel 存活）。
- [ ] **Step 4:** 把发现的问题修掉并提交；全部通过后，按 superpowers:finishing-a-development-branch 处理分支。
