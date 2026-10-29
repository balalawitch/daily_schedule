package love.panyi.forever.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 把一段文字整理成若干日程模板。
 * [generate] 在模型输出不合格时抛 [PlanParseException]，其他失败（网络、HTTP）抛 [AiException]。
 */
interface ScheduleGenerator {
    suspend fun generate(text: String, retryHint: String?): List<ScheduleTemplate>

    /** 「测试连接」：默认什么都不做；失败抛 [AiException]。 */
    suspend fun ping() {}
}

const val PLAN_SYSTEM_PROMPT: String = """你是日程整理助手。用户会给你一段文字，请把其中的日程安排整理成 JSON。

规则：
1. 只提取和日程安排有关的内容，其他内容一律忽略。
2. 只输出 JSON，不要任何解释，也不要加代码块标记。格式如下：
{"days":[{"label":"这组日程适用的天","items":[{"start":"HH:mm","end":"HH:mm","title":"标题","description":"细节"}]}]}
3. start、end 用 24 小时制 HH:mm；结束时间可以是 24:00。
4. 同一组 items 里的时间不能重叠。原文没说结束时间的，按常识估一个合理的时长。
5. title 要简短，可以用“·”把事项名和做法分开，例如“写作业·先难后易”；必须准点完成的事项在 title 开头加“⏰”。
6. 原文里的要求、注意事项、要带的东西等细节写进 description；没有细节就填空字符串。
7. 原文区分了不同的天（例如工作日、周末、某一天）就拆成多个 days，每个 days 用 label 起个名字（如“周一–周五”“周六”）；没有区分就只输出 1 个，label 写“每天”。

示例
输入：
工作日放学回家先休息半小时，4点半开始写作业，先做数学，再做语文，6点吃饭。周六上午9点去上游泳课，两小时。
输出：
{"days":[{"label":"周一–周五","items":[{"start":"16:00","end":"16:30","title":"休息","description":""},{"start":"16:30","end":"18:00","title":"写作业·先做数学","description":"先做数学，再做语文。"},{"start":"18:00","end":"18:30","title":"吃饭","description":""}]},{"label":"周六","items":[{"start":"09:00","end":"11:00","title":"⏰游泳课","description":""}]}]}"""

fun planUserMessage(text: String, retryHint: String?): String =
    if (retryHint.isNullOrBlank()) text else "$text\n\n上次的输出有问题：$retryHint，请修正后重新输出完整 JSON"

/** OpenAI 兼容的 chat/completions 接口（DeepSeek、通义、Kimi、OpenAI、Ollama 等）。 */
class CloudGenerator(private val config: CloudConfig) : ScheduleGenerator {

    override suspend fun generate(text: String, retryHint: String?): List<ScheduleTemplate> {
        val body = chatRequestJson(config.model.trim(), PLAN_SYSTEM_PROMPT, planUserMessage(text, retryHint))
        return parsePlan(chatResponseContent(post(body)))
    }

    override suspend fun ping() {
        post(chatRequestJson(config.model.trim(), "", "ping"))
    }

    /**
     * 发送请求，返回 HTTP 200 的响应体；其余情况抛 [AiException]。
     * 协程被取消时从取消方调用 `disconnect()` 打断阻塞的连接 / 读取（Thread.interrupt 对 socket 无效），
     * 并抛 CancellationException，不会被当成网络错误。
     */
    private suspend fun post(body: String): String {
        val endpoint = chatEndpoint(config.url)
        val conn = try {
            URL(endpoint).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            throw AiException("接口地址不正确")
        }
        try {
            return suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { conn.disconnect() }
                CoroutineScope(Dispatchers.IO).launch {
                    cont.resumeWith(runCatching { exchange(conn, body) })
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** 阻塞的请求 / 响应过程，只在 IO 线程调用；失败一律转成 [AiException]。 */
    private fun exchange(conn: HttpURLConnection, body: String): String {
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            try {
                conn.setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
            } catch (_: IllegalArgumentException) {
                throw AiException("API Key 或接口地址包含非法字符")
            }
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code != 200) throw AiException(httpErrorMessage(code, text))
            return text
        } catch (e: IOException) {
            throw AiException("网络连接失败，请检查网络")
        } catch (e: RuntimeException) {
            throw AiException("请求失败，请检查接口地址和 API Key")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 60_000
    }
}
