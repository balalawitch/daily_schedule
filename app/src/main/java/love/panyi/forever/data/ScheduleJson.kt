package love.panyi.forever.data

import org.json.JSONArray
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
