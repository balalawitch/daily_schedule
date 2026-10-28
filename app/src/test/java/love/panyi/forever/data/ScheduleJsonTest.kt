package love.panyi.forever.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleJsonTest {

    @Test
    fun schedules_roundTripKeepsDescription() {
        val list = listOf(Schedule(1, 420, 450, "起床", "先喝水"), Schedule(2, 480, 540, "上学"))
        assertEquals(list, schedulesFromJson(schedulesToJson(list)))
    }

    @Test
    fun schedulesFromJson_oldDataWithoutDescription() {
        val old = """[{"id":5,"start":60,"end":120,"title":"午睡"}]"""
        assertEquals(listOf(Schedule(5, 60, 120, "午睡", "")), schedulesFromJson(old))
    }
}
