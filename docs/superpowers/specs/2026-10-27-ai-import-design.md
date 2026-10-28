# AI 导入日程 设计文档

日期：2026-10-27 状态：待主人审阅

## 1. 目的

用户（家长）手里常常有一段文字描述的作息安排，比如「周一到周五 7 点起床……周末上午学钢琴」。现在只能一条条手动录入。本功能让用户粘贴一段自然语言，由 AI 理解其中的日程部分，生成结构化日程，在时间轴预览里确认后**存成模板**，省掉手动录入。以后再做「挑选模板、把模板套用到指定日期」。

AI 引擎由用户自己选：手机支持就用系统自带的 Gemini Nano；不支持可以下载本地 Gemma 4 E2B 离线运行；也可以填 API Key，用更强的云端模型（OpenAI 兼容接口）。

## 2. 目标 / 非目标 / 反目标

**目标（本设计覆盖，分三期实现，见 §9）**
- 输入一段自然语言（粘贴或手打），AI 生成 1 天或多天的日程 JSON。
- 用时间轴样式预览结果，多组时用标签切换；用户确认后，**每一组存成一个模板，模板名取 `label`**。
- 新增模板的本地存储（只存，不做查看和套用界面）。
- 三种 AI 引擎可选：Gemini Nano、本地 Gemma 4 E2B、云端 OpenAI 兼容 API。
- 日程新增「详细说明」`description` 字段：AI 生成时填写，编辑弹窗里可以改，在「此刻」卡片里直接显示。

**非目标（以后再做）**
- 模板的查看、改名、删除；从多个模板里挑选（内置「平常日作息」也作为一个）；把模板套用到指定日期。
- 文本里写了具体日期时直接套用到这些日期。
- AI 结果直接写进日历。本期的产物只有模板。
- 导入图片、文件、Excel；OAuth 或账号登录；云同步。

**反目标（即使能跑也算失败）**
- AI 结果没经过用户确认就保存。
- API Key 明文保存。
- 存下互相重叠的模板（以后套用到日历时会导致 `LazyColumn` key 重复崩溃，见 CLAUDE.md）。
- 一次生成了多组，却只存下其中一组。
- 破坏现有约束：MVVM 依赖方向、单页面、不引入 DI、导航库或数据库。

## 3. 约束

- 沿用 CLAUDE.md 里的全部架构约束。UI 文案直接用中文硬编码。
- minSdk 26。AGP 9.4.1 内置 Kotlin 编译；compose 插件为 Kotlin 2.2.10。LiteRT-LM 要求 Kotlin ≥ 2.2，接入时要验证兼容性。
- release 开启 R8，新增依赖需要的 keep 规则放在 `app/src/main/keepRules/`。
- 本机直接跑 `testDebugUnitTest` 会失败，需要用 CLAUDE.md 里写的 JUnitCore 绕过方法。
- 模拟器跑不了本地 LLM 和 Nano，第 ②③ 期需要真机验证。

## 4. 主人已决定的部分

1. **输入**：一段自然语言。
2. **引擎可选**：App 先检测，再让用户选。有 Nano 就可以选 Nano；没有可以下载本地模型（Gemma 4 E2B）；还可以用云端模型，接口为 **OpenAI 兼容**，只保存和使用 `url`、`apiKey`、`model` 三个值（不做 OAuth）。
3. **流程**：先生成格式化的日程 JSON，用**时间轴预览**，确认后再保存。
4. **保存 = 存成模板**：生成多组时，每组按 `label` 各存成一个模板。以后做挑选模板、套用到指定日期；**本期只做到存成模板**。
5. **界面流程**（草图已认可）：
   - ① 顶部日期栏右侧、📅 日期选择按钮旁边加一个「✨ 导入」按钮；
   - ② 点击后弹出底部面板：多行输入框、当前 AI 引擎、「生成 / 取消」；
   - ③ 预览：时间轴样式，多组用标签切换，顶部提示「AI 生成，请检查」，底部是「重新生成 / 存为模板」。
6. **每个 item 带 `description`（详细说明）**，在「此刻」卡片里直接显示。

## 5. 我做的决定（主人可以推翻）

| # | 决定 | 理由 |
|---|---|---|
| D1 | 模板名重复时自动加后缀，例如「周一–周五 (2)」，不覆盖旧模板。「存为模板」一次存下全部分组，存完关闭预览，用 Snackbar 提示「已存为 2 个模板：周一–周五、周末」 | 不丢数据；本期没有模板管理界面，覆盖了也没法找回 |
| D2 | 开发顺序：① 云端 API + 整条流程 → ② Gemini Nano → ③ 本地 Gemma | 云端最容易跑通，模拟器上能测，先把链路打通 |
| D3 | JSON 格式见 §6.2；校验不通过时自动重试一次，再失败就提示用户 | 小模型输出不稳定 |
| D4 | 预览里点某一条，可以用现有的 `ScheduleDialog` 修改或删除，校验对象是当前这一组 | 草图里提过，主人认可了 |
| D5 | 引擎设置用底部面板，从输入面板的「AI 引擎」那一行进入，不新增页面 | 单页面约定 |
| D6 | 云端请求用 `HttpURLConnection` + `org.json`，不引入 OkHttp、Retrofit 或序列化库 | 项目已经在用 `org.json`，如无必要勿增实体 |
| D7 | API Key 用 Android Keystore（AES-GCM）加密，密文存进 SharedPreferences | 不明文保存，也不依赖已废弃的 security-crypto |
| D8 | 本地模型用系统 `DownloadManager` 下载到 `getExternalFilesDir("models")` | 系统自带断点续传、通知栏进度、可限定 Wi-Fi，不用引入库 |
| D9 | 导入功能单独一个 `ImportViewModel`，不塞进 `ScheduleViewModel` | `ScheduleViewModel` 已经不小；导入和设置是独立的一块状态 |
| D10 | 输入最多 2000 字 | Nano 输入上限约 4000 token，给提示词留余量 |
| D11 | 跨午夜的条目（如「21:30 睡觉到 7:10 起床」，即 `end < start`）截到 24:00，存成 21:30–24:00，不当作错误 | 一句睡觉不该让整次导入失败；现有作息模板也只到当晚为止 |
| D12 | 云端 `url` 只允许 https，不放开明文网络 | API Key 不能明文传输 |
| D13 | 第③期模型默认从镜像 hf-mirror.com 下载，下载地址可以在设置里改；地址按 Google 官方 App 的做法锁定 commit（见 §6.4）。不需要 HF Token（2026-10-28 实测：官方地址和镜像都不用登录就能 302 跳转到下载） | 国内通常连不上 huggingface.co |

## 6. 设计

### 6.1 结构

```
data/
├── Schedule.kt            Schedule 增加 description；新增 ScheduleTemplate；新增纯逻辑：解析 + 校验 AI 输出、模板重名加后缀（可在 JVM 测试）
├── ScheduleRepository.kt  接口新增 loadTemplates / saveTemplates
├── ScheduleStore.kt       读写 description（旧数据没有这个字段 → ""）；读写模板
├── ScheduleGenerator.kt   接口：suspend fun generate(text: String): GeneratedPlan
├── CloudGenerator.kt      OpenAI 兼容 /chat/completions          （第①期）
├── NanoGenerator.kt       ML Kit Prompt API + 结构化输出          （第②期）
├── GemmaGenerator.kt      LiteRT-LM，Engine 加载一次后复用         （第③期）
└── AiSettings.kt          当前引擎、云端配置（Key 加密）、本地模型状态
ui/schedule/
├── ImportViewModel.kt     输入 → 生成 → 预览 → 保存；引擎检测与设置
└── ImportSheets.kt        输入面板、预览、引擎设置面板
```

- 依赖方向不变：View → ViewModel → Model。`ImportViewModel` 依赖 `ScheduleRepository`（用于存模板）和一个按当前设置选出 `ScheduleGenerator` 的入口，不依赖具体实现类。
- `MainActivity` 用和现在一样的 `viewModelFactory` 方式创建 `ImportViewModel`。
- 文件拆分只是建议，构建者可以合并，例如三个 Generator 放在同一个文件里。

### 6.2 数据格式

AI 输出（以及 `GeneratedPlan` 对应的结构）：

```json
{
  "days": [
    {
      "label": "周一–周五",
      "items": [
        {
          "start": "16:00",
          "end": "17:00",
          "title": "写作业·先难后易",
          "description": "先做数学和英语，做完自己检查一遍，不会的题标出来，晚饭后问爸妈。"
        }
      ]
    }
  ]
}
```

- `label`：这一组日程适用的天，由 AI 根据原文起名，例如「周一–周五」「周六」「每天」。
- `start` / `end`：`HH:mm`；`end` 允许 `24:00`，`00:00` 也当作 24:00，和时间选择器一致。
- `title`：沿用现有规则：「·」前是事项名、后是做法，`⏰` 开头表示掐点。要短。
- `description`：原文里的细节（要求、注意事项、要带的东西），没有就填空字符串。

**解析 + 校验**（纯 Kotlin，放在 `data/`）：
- 去掉模型可能带的 ```json 代码块包裹和前后多余文字，取第一个完整的 JSON 对象。
- 每条：时间格式合法，`end > start`（跨午夜按 D11 截到 24:00），标题非空；同一组内用 `hasOverlap` 检查不重叠。
- 至少要有 1 组、1 条；`label` 为空时用「模板 1」「模板 2」……
- 失败时给出具体原因，用于重试和提示。
- 预览和编辑时需要 `Schedule`（`ScheduleDialog`、时间轴行都接收它），可以临时用 `base + i` 生成 id，和 `weekdayRoutine()` 一样。

**日程存储**：`Schedule` 改成 `Schedule(id, start, end, title, description = "")`；`ScheduleStore` 写入 `description`，读取用 `optString("description")`，旧数据自动兼容。

**模板存储**：
- `data class ScheduleTemplate(val name: String, val items: List<Schedule>)`。模板里的 id 没有意义，以后套用到日期时重新生成。
- `ScheduleRepository` 新增 `suspend fun loadTemplates(): List<ScheduleTemplate>` 和 `suspend fun saveTemplates(templates: List<ScheduleTemplate>)`（整体覆盖写入，和按天存储一样）。本期没有界面要观察模板变化，不做 Flow。
- `ScheduleStore` 用键 `templates` 存 JSON 数组：`[{"name": "...", "items": [{"start": 420, "end": 450, "title": "...", "description": "..."}]}]`，时间用当天分钟数，和日程存储一致。
- 存模板 = 读出已有模板 → 新模板按 D1 改名去重 → 追加 → 整体写回。

### 6.3 提示词（Gemma 和云端共用；Nano 用结构化输出，规则相同）

用中文写系统提示词，要点：
- 只提取跟日程安排有关的内容，忽略其他。
- 只输出符合上面格式的 JSON，不要解释。
- 时间不重叠；原文没说结束时间的，按常识估一个合理时长。
- `title` 短，细节写进 `description`。
- 原文区分不同的天（工作日 / 周末 / 某天）就拆成多个 `days`，否则只输出 1 个。

带 1 个示例（输入 → 输出），temperature 设低（约 0.2）。重试时把上次的错误原因附在消息里。具体措辞由构建者调。

### 6.4 三种引擎

| | 云端（第①期） | Gemini Nano（第②期） | 本地 Gemma 4 E2B（第③期） |
|---|---|---|---|
| 依赖 | 无新依赖；Manifest 加 `INTERNET` 权限 | `com.google.mlkit:genai-prompt`（写作时为 1.0.0-beta4）；结构化输出需要 KSP + `genai-schema-compiler` | `com.google.ai.edge.litertlm:litertlm-android`（固定一个具体版本，不用 `latest.release`） |
| 检测 | 地址、Key、模型名都已填写 | `checkStatus()`：AVAILABLE / DOWNLOADABLE / DOWNLOADING / UNAVAILABLE | 模型文件存在且大小等于 2588147712 字节；内存不足 8 GB 时提示可能跑不动，但不禁止 |
| 准备 | 填三项 +「测试连接」（发一个最小请求） | DOWNLOADABLE 时提供「下载」按钮，调用 `download()` | 「下载（2.6 GB，建议连 Wi-Fi）」→ 进度 → 已就绪；可删除模型 |
| 调用 | POST `{baseUrl}/chat/completions`，`messages` = system + user，超时 60 秒 | `generateTypedContentRequest`，`@Generable` 数据类映射到 `GeneratedPlan` | `Engine` 在后台线程 `initialize()`（约 10 秒），之后复用；CPU backend；`ImportViewModel` 被清除时关闭 |
| 限制 | 需要联网 | 只能在前台调用；有每个 App 的配额；解锁 BL 的手机不支持；国行机大多不可用 | 首次加载慢；占用约 1.5–1.7 GB 内存 |

- 本地模型来源：以 Google AI Edge Gallery 的模型清单 `model_allowlists/1_0_20.json` 为准：
  - `modelId` = `litert-community/gemma-4-E2B-it-litert-lm`，`modelFile` = `gemma-4-E2B-it.litertlm`，`sizeInBytes` = 2588147712，`minDeviceMemoryInGb` = 8；
  - 最新文件的 commit = `7fa1d78473894f7e736a21d920c3aa80f950c0db`（带 Multi Token Prediction）；
  - 下载地址：`https://{host}/{modelId}/resolve/{commit}/{modelFile}?download=true`，`host` 默认 `hf-mirror.com`，可改成 `huggingface.co`。锁定 commit，避免仓库更新后文件大小对不上。
- 不可用的引擎在设置面板里置灰并写明原因；只能选一个；选择保存在 SharedPreferences。
- 第一次使用时如果没有选过引擎：Nano 可用就默认选 Nano，否则打开设置面板让用户选。

### 6.5 界面

- **入口**：`WeekHeader` 里 📅 按钮旁边加 ✨ 按钮。
- **输入面板**（`ModalBottomSheet`）：多行输入框（显示字数 / 2000）、「AI 引擎：xxx ›」一行（点击进入设置面板）、「生成」按钮。生成中显示「正在理解…」，可以取消。
- **预览**：全屏弹窗，复用现有时间轴行的样式；多组时顶部是标签（显示 `label`）；「AI 生成，请检查」提示条；点某一条打开 `ScheduleDialog`（D4）；底部「重新生成 / 存为模板」。存完关闭并提示（D1）。
- **设置面板**：三种引擎各一张卡片，显示状态和操作（见 §6.4）。
- **`ScheduleDialog`**：加一个多行「说明」输入框，选填。
- **`NowCard`**：标题下方直接显示 `description`。很长时怎么处理（折叠或限制行数）由构建者决定，但默认要能看到前几行。其他地方（时间轴行、`NowInlineRow`、`FloatingNow`）不显示。
- 样式遵循设计系统「日程表」和 `Color.kt`。

### 6.6 错误提示

| 情况 | 提示 |
|---|---|
| 没有网络或请求超时 | 「网络连接失败，请检查网络」 |
| HTTP 401/403 | 「API Key 无效」 |
| 其他 HTTP 错误 | 显示状态码和服务端 message |
| 重试后输出仍然不合法 | 「没能理解这段文字，换个说法试试」+ 具体原因（如「16:00–17:00 和 16:30–17:30 重叠」） |
| 没有找到任何日程 | 「这段文字里没有找到日程安排」 |
| Nano 配额用完或不在前台 | 按错误码提示稍后再试 |
| 本地模型加载失败或内存不足 | 「本地模型启动失败」+ 建议换引擎 |

## 7. 测试

- JVM 单元测试（`data/`）：JSON 解析（正常、带代码块包裹、前后有多余文字、字段缺失、时间非法、`24:00`、`00:00` 作为结束时间、跨午夜截断、同一组重叠、空结果、空 `label`）；模板重名加后缀；`ScheduleStore` 兼容没有 `description` 的旧数据，需要的话把 JSON 转换抽成纯函数来测。
- 云端：手动用一个 OpenAI 兼容服务在模拟器上跑通。
- Nano / Gemma：真机手动验证，记录机型和耗时。

## 8. 留给构建者决定的

- 提示词的具体措辞和示例。
- 文件怎么拆分、类名。
- LiteRT-LM 和 ML Kit 的具体版本号，以及 keep 规则。
- 预览和设置面板的具体样式细节（遵循设计系统）。
- `NowCard` 里长说明的展示方式。
- 是否开启 GPU backend。

## 9. 分期

1. **第①期**：`description` 字段（存储、编辑弹窗、此刻卡）+ 解析校验 + 模板存储 + 云端引擎 + 输入面板 / 预览 / 设置面板（Nano 和 Gemma 卡片先显示「即将支持」）。
2. **第②期**：Gemini Nano 检测、下载、结构化输出。
3. **第③期**：本地 Gemma 下载管理 + LiteRT-LM 推理。

## 参考

- LiteRT-LM Android：https://developers.google.com/edge/litert-lm/android
- 模型：https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- ML Kit Prompt API：https://developers.google.com/ml-kit/genai/prompt/android/get-started
- 结构化输出：https://developers.google.com/ml-kit/genai/prompt/android/structured-output
- 支持机型：https://developers.google.com/ml-kit/genai
- Google AI Edge Gallery（参考实现）：https://github.com/google-ai-edge/gallery
