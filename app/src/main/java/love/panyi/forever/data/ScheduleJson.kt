package love.panyi.forever.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

fun schedulesToJson(list: List<Schedule>): String {
    val array = JSONArray()
    list.forEach {
        array.put(
            JSONObject().put("id", it.id).put("start", it.start).put("end", it.end).put("title", it.title)
                .put("description", it.description),
        )
    }
    return array.toString()
}

fun schedulesFromJson(json: String): List<Schedule> {
    val array = JSONArray(json)
    return List(array.length()) {
        val o = array.getJSONObject(it)
        Schedule(o.getLong("id"), o.getInt("start"), o.getInt("end"), o.getString("title"), o.optString("description"))
    }
}

/** 模板不存 id（无意义）；读出时 id 用 0 起的下标。 */
fun templatesToJson(list: List<ScheduleTemplate>): String {
    val array = JSONArray()
    list.forEach { t ->
        val items = JSONArray()
        t.items.forEach {
            items.put(
                JSONObject().put("start", it.start).put("end", it.end).put("title", it.title)
                    .put("description", it.description),
            )
        }
        array.put(JSONObject().put("name", t.name).put("items", items))
    }
    return array.toString()
}

fun templatesFromJson(json: String): List<ScheduleTemplate> {
    val array = JSONArray(json)
    return List(array.length()) { i ->
        val o = array.getJSONObject(i)
        val items = o.getJSONArray("items")
        ScheduleTemplate(
            o.getString("name"),
            List(items.length()) { j ->
                val item = items.getJSONObject(j)
                Schedule(j.toLong(), item.getInt("start"), item.getInt("end"), item.getString("title"), item.text("description"))
            },
        )
    }
}

/** AI 输出解析失败。[empty] 为 true 表示「没找到日程」，不必重试；[message] 是中文原因。 */
class PlanParseException(message: String, val empty: Boolean = false) : Exception(message)

/** 取字符串字段并 trim；缺失或 JSON null 都视为空串（Android 的 optString 会把 null 变成 "null"）。 */
private fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key).trim()

private val CLOCK = Regex("""^(\d{1,2}):(\d{2})$""")

/** 把 "H:MM" 解析成当天分钟数（24:00 = 1440）；格式不对返回 null。 */
private fun parseClock(text: String): Int? {
    val m = CLOCK.matchEntire(text.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (h > 24 || min > 59 || (h == 24 && min != 0)) return null
    return h * 60 + min
}

private fun parsePlanItem(o: JSONObject, id: Long): Schedule {
    val startText = o.text("start")
    val endText = o.text("end")
    val start = parseClock(startText)
    val end = parseClock(endText)
    if (start == null || start == DAY_MINUTES) throw PlanParseException("开始时间“$startText”不合法")
    if (end == null) throw PlanParseException("结束时间“$endText”不合法")
    val title = o.text("title")
    if (title.isEmpty()) throw PlanParseException("$startText 的日程缺少标题")
    // 结束 00:00 视为 24:00；跨午夜（end < start）截到 24:00
    val fixedEnd = if (end == 0 || end < start) DAY_MINUTES else end
    if (fixedEnd == start) throw PlanParseException("$startText–$endText 开始和结束时间相同")
    return Schedule(id, start, fixedEnd, title, o.text("description"))
}

/**
 * 把 AI 的输出解析成若干模板。取第一个 `{` 到最后一个 `}` 之间的内容解析，
 * 失败时抛 [PlanParseException]。所有日程 id 从 [idBase] 起递增。
 */
fun parsePlan(raw: String, idBase: Long = System.currentTimeMillis()): List<ScheduleTemplate> {
    val from = raw.indexOf('{')
    val to = raw.lastIndexOf('}')
    if (from < 0 || to <= from) throw PlanParseException("不是合法的 JSON")
    val root = try {
        JSONObject(raw.substring(from, to + 1))
    } catch (_: JSONException) {
        throw PlanParseException("不是合法的 JSON")
    }
    val days = root.optJSONArray("days") ?: throw PlanParseException("缺少 days")

    var nextId = idBase
    val result = mutableListOf<ScheduleTemplate>()
    for (i in 0 until days.length()) {
        val day = days.optJSONObject(i) ?: throw PlanParseException("days 第 ${i + 1} 项不是对象")
        val items = day.optJSONArray("items")
        if (items == null || items.length() == 0) continue
        val parsed = List(items.length()) { j ->
            val o = items.optJSONObject(j) ?: throw PlanParseException("第 ${i + 1} 组的第 ${j + 1} 条日程不是对象")
            parsePlanItem(o, nextId++)
        }.sortedBy { it.start }
        val accepted = mutableListOf<Schedule>()
        for (s in parsed) {
            accepted.firstOrNull { hasOverlap(listOf(it), s.start, s.end) }?.let {
                throw PlanParseException(
                    "${formatMinute(it.start)}–${formatMinute(it.end)} 与 ${formatMinute(s.start)}–${formatMinute(s.end)} 重叠",
                )
            }
            accepted += s
        }
        val name = day.text("label").ifEmpty { "模板 ${result.size + 1}" }
        result += ScheduleTemplate(name, accepted)
    }
    if (result.isEmpty()) throw PlanParseException("这段文字里没有找到日程安排", empty = true)
    return result
}
