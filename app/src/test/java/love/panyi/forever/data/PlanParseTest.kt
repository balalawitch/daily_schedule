package love.panyi.forever.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanParseTest {

    private fun day(label: String, vararg items: String) =
        """{"label":"$label","items":[${items.joinToString(",")}]}"""

    private fun item(s: String, e: String, t: String, d: String? = null) =
        """{"start":"$s","end":"$e","title":"$t"${d?.let { ",\"description\":\"$it\"" } ?: ""}}"""

    private fun plan(vararg days: String) = """{"days":[${days.joinToString(",")}]}"""

    @Test
    fun parsePlan_twoGroups() {
        val r = parsePlan(
            plan(
                day("周一–周五", item("07:00", "07:30", "起床", "先喝水")),
                day("周末", item("10:00", "11:00", "钢琴")),
            ),
            idBase = 100,
        )
        assertEquals(listOf("周一–周五", "周末"), r.map { it.name })
        assertEquals(Schedule(100, 420, 450, "起床", "先喝水"), r[0].items[0])
        assertEquals(Schedule(101, 600, 660, "钢琴", ""), r[1].items[0])
    }

    @Test
    fun parsePlan_stripsFencesAndProse() {
        val raw = "好的，结果如下：\n```json\n" + plan(day("每天", item("08:00", "09:00", "早读"))) + "\n```\n希望有帮助"
        assertEquals(1, parsePlan(raw).single().items.size)
    }

    @Test
    fun parsePlan_acceptsLooseClockFormats() {
        val r = parsePlan(
            plan(day("每天", item("7:10", "7:15", "起床"), item("23:00", "00:00", "睡前"), item("06:00", "07:00", "晨练"))),
        )
        assertEquals(
            listOf(360 to 420, 430 to 435, 1380 to 1440),
            r.single().items.sortedBy { it.start }.map { it.start to it.end },
        )
        assertEquals(1440, parsePlan(plan(day("每天", item("23:00", "24:00", "x")))).single().items.single().end)
    }

    @Test
    fun parsePlan_clipsCrossMidnightToDayEnd() {
        val s = parsePlan(plan(day("每天", item("21:30", "07:10", "睡觉")))).single().items.single()
        assertEquals(1290 to 1440, s.start to s.end)
    }

    @Test
    fun parsePlan_blankLabelGetsDefaultName() {
        assertEquals("模板 1", parsePlan(plan(day(" ", item("08:00", "09:00", "a")))).single().name)
    }

    @Test
    fun parsePlan_overlapIsRejectedWithTimes() {
        val e = assertThrows(PlanParseException::class.java) {
            parsePlan(plan(day("每天", item("16:00", "17:00", "作业"), item("16:30", "17:30", "练琴"))))
        }
        assertEquals("16:00–17:00 与 16:30–17:30 重叠", e.message)
    }

    @Test
    fun parsePlan_emptyIsFlagged() {
        val e = assertThrows(PlanParseException::class.java) { parsePlan(plan(day("每天"))) }
        assertTrue(e.empty)
    }

    @Test
    fun parsePlan_invalidInputsAreRejected() {
        for (
        raw in listOf(
            "没有 JSON", """{"x":1}""", plan(day("每天", item("25:00", "26:00", "a"))),
            plan(day("每天", item("08:00", "08:00", "a"))), plan(day("每天", item("08:00", "09:00", " "))),
            plan(day("每天", item("24:00", "24:00", "a"))),
        )
        ) {
            val e = assertThrows(PlanParseException::class.java) { parsePlan(raw) }
            assertFalse(raw, e.empty)
        }
    }
}
