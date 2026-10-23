package love.panyi.forever.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 按日期把日程和后备活动以 JSON 存进 SharedPreferences。 */
class ScheduleStore(context: Context) : ScheduleRepository {
    private val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    /** 每次写入 +1，驱动 observeDay 重新读取。 */
    private val version = MutableStateFlow(0)

    override fun observeDay(date: LocalDate): Flow<DayData> =
        version.map { DayData(schedules(date), backups(date)) }

    private fun schedules(date: LocalDate): List<Schedule> {
        val array = JSONArray(prefs.getString("s_$date", "[]"))
        return List(array.length()) {
            val o = array.getJSONObject(it)
            Schedule(o.getLong("id"), o.getInt("start"), o.getInt("end"), o.getString("title"))
        }
    }

    override suspend fun saveSchedules(date: LocalDate, schedules: List<Schedule>) {
        val array = JSONArray()
        schedules.forEach {
            array.put(JSONObject().put("id", it.id).put("start", it.start).put("end", it.end).put("title", it.title))
        }
        prefs.edit().putString("s_$date", array.toString()).apply()
        version.update { it + 1 }
    }

    private fun backups(date: LocalDate): Map<Int, String> {
        val o = JSONObject(prefs.getString("b_$date", "{}")!!)
        return o.keys().asSequence().associate { it.toInt() to o.getString(it) }
    }

    override suspend fun saveBackups(date: LocalDate, backups: Map<Int, String>) {
        val o = JSONObject()
        backups.forEach { (start, text) -> o.put(start.toString(), text) }
        prefs.edit().putString("b_$date", o.toString()).apply()
        version.update { it + 1 }
    }
}
