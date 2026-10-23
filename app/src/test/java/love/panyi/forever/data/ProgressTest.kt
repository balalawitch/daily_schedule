package love.panyi.forever.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    private fun h(hour: Int, minute: Int = 0) = hour * 60 + minute

    private val tea = Schedule(1, h(16, 20), h(16, 35), "下午茶")
    private val outdoor = Schedule(2, h(16, 35), h(17, 10), "户外活动")
    private val chores = Schedule(3, h(17, 15), h(17, 35), "摆碗筷")
    private val dinner = Schedule(4, h(17, 40), h(18, 10), "⏰ 晚餐 · 坐餐桌")
    private val day = listOf(dinner, outdoor, chores, tea)

    @Test
    fun withoutRecords_passedIsDoneAndCurrentContainsNow() {
        val p = dayProgress(day, emptyMap(), h(16, 40))
        assertEquals(listOf(tea), p.done)
        assertEquals(outdoor, p.current)
        assertEquals(listOf(chores, dinner), p.upcoming)
    }

    @Test
    fun completeEarly_movesCurrentToDone() {
        val records = mapOf(outdoor.id to ScheduleRecord(Completion.EARLY, outdoor.start, h(16, 40)))
        val p = dayProgress(day, records, h(16, 40))
        assertEquals(listOf(tea, outdoor), p.done)
        assertNull(p.current)
        assertEquals(listOf(chores, dinner), p.upcoming)
    }

    @Test
    fun undone_keepsPassedScheduleOutOfDone() {
        val records = mapOf(tea.id to ScheduleRecord(Completion.UNDONE, tea.start, tea.end))
        val p = dayProgress(day, records, h(16, 40))
        assertTrue(p.done.isEmpty())
        assertEquals(listOf(tea, chores, dinner), p.upcoming)
    }

    @Test
    fun nowInGap_hasNoCurrent() {
        val p = dayProgress(day, emptyMap(), h(17, 12))
        assertNull(p.current)
        assertEquals(listOf(tea, outdoor), p.done)
    }

    @Test
    fun isDone_recordWinsOverTime() {
        assertTrue(isDone(null, passed = true))
        assertFalse(isDone(null, passed = false))
        assertTrue(isDone(ScheduleRecord(Completion.SKIPPED, 0, 10), passed = false))
        assertFalse(isDone(ScheduleRecord(Completion.UNDONE, 0, 10), passed = true))
    }

    @Test
    fun splitTitle_namesHintAndTimed() {
        assertEquals(TitleParts("晚餐", "坐餐桌，30 分钟到点收", true), splitTitle("⏰ 晚餐 · 坐餐桌，30 分钟到点收"))
        assertEquals(TitleParts("下午茶（小份）", "坐小桌", false), splitTitle("下午茶（小份） · 坐小桌"))
        assertEquals(TitleParts("开会", null, false), splitTitle("开会"))
    }

    @Test
    fun weekdayRoutine_titlesSplitCleanly() {
        weekdayRoutine().forEach {
            val parts = splitTitle(it.title)
            assertTrue(it.title, parts.name.isNotEmpty() && !parts.name.contains('⏰'))
        }
        assertEquals(4, weekdayRoutine().count { splitTitle(it.title).timed })
    }
}
