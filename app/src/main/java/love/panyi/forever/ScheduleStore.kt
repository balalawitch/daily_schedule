package love.panyi.forever

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 按日期把日程和后备活动以 JSON 存进 SharedPreferences。 */
class ScheduleStore(context: Context) {
    private val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    fun schedules(date: LocalDate): List<Schedule> {
        val array = JSONArray(prefs.getString("s_$date", "[]"))
        return List(array.length()) {
            val o = array.getJSONObject(it)
            Schedule(o.getLong("id"), o.getInt("start"), o.getInt("end"), o.getString("title"))
        }
    }

    fun saveSchedules(date: LocalDate, schedules: List<Schedule>) {
        val array = JSONArray()
        schedules.forEach {
            array.put(JSONObject().put("id", it.id).put("start", it.start).put("end", it.end).put("title", it.title))
        }
        prefs.edit().putString("s_$date", array.toString()).apply()
    }

    /** 后备活动：空闲块起始分钟 -> 内容。 */
    fun backups(date: LocalDate): Map<Int, String> {
        val o = JSONObject(prefs.getString("b_$date", "{}")!!)
        return o.keys().asSequence().associate { it.toInt() to o.getString(it) }
    }

    fun saveBackups(date: LocalDate, backups: Map<Int, String>) {
        val o = JSONObject()
        backups.forEach { (start, text) -> o.put(start.toString(), text) }
        prefs.edit().putString("b_$date", o.toString()).apply()
    }
}
