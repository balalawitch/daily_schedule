package love.panyi.forever.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {

    private fun free(start: Int, end: Int) = TimeBlock.Free(start, end)
    private fun h(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun noSchedules_gives24HourlyBlocks() {
        val blocks = buildTimeline(emptyList())
        assertEquals((0 until 24).map { free(h(it), h(it + 1)) }, blocks)
    }

    @Test
    fun gapsAreSplitAtHourBoundaries() {
        val meeting = Schedule(1, h(9, 30), h(11), "开会")
        val blocks = buildTimeline(listOf(meeting))
        assertEquals(free(h(8), h(9)), blocks[8])
        assertEquals(free(h(9), h(9, 30)), blocks[9])
        assertEquals(TimeBlock.Busy(meeting), blocks[10])
        assertEquals(free(h(11), h(12)), blocks[11])
        assertEquals(free(h(23), h(24)), blocks.last())
    }

    @Test
    fun unsortedSchedulesAndDayEdges() {
        val late = Schedule(2, h(22, 15), h(24), "睡觉")
        val early = Schedule(1, h(0), h(0, 45), "夜宵")
        val blocks = buildTimeline(listOf(late, early))
        assertEquals(TimeBlock.Busy(early), blocks.first())
        assertEquals(free(h(0, 45), h(1)), blocks[1])
        assertEquals(free(h(22), h(22, 15)), blocks[blocks.size - 2])
        assertEquals(TimeBlock.Busy(late), blocks.last())
    }

    @Test
    fun blocksCoverWholeDayContiguously() {
        val blocks = buildTimeline(
            listOf(Schedule(1, h(7, 10), h(8, 50), "a"), Schedule(2, h(8, 50), h(13), "b"))
        )
        assertEquals(0, blocks.first().start)
        assertEquals(h(24), blocks.last().end)
        blocks.zipWithNext { a, b -> assertEquals(a.end, b.start) }
    }

    @Test
    fun weekdayRoutine_isValidAndNonOverlapping() {
        val routine = weekdayRoutine()
        assertEquals(routine.size, routine.map { it.id }.toSet().size)
        routine.forEach {
            assertTrue(it.title, it.start in 0 until it.end && it.end <= DAY_MINUTES)
            assertFalse(it.title, hasOverlap(routine, it.start, it.end, ignoreId = it.id))
        }
        assertEquals(h(7, 10), routine.minOf { it.start })
    }

    @Test
    fun overlapDetection() {
        val existing = listOf(Schedule(1, h(9), h(10), "a"))
        assertTrue(hasOverlap(existing, h(9, 30), h(10, 30)))
        assertFalse(hasOverlap(existing, h(10), h(11)))
        assertFalse(hasOverlap(existing, h(9), h(10), ignoreId = 1))
    }
}
