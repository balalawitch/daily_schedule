package love.panyi.forever

const val DAY_MINUTES = 24 * 60

/** 一条日程，start/end 为当天的分钟数，[start, end)。 */
data class Schedule(val id: Long, val start: Int, val end: Int, val title: String)

/** 时间轴上的一个区块：日程块或空闲块。 */
sealed interface TimeBlock {
    val start: Int
    val end: Int

    data class Busy(val schedule: Schedule) : TimeBlock {
        override val start get() = schedule.start
        override val end get() = schedule.end
    }

    data class Free(override val start: Int, override val end: Int) : TimeBlock
}

/** 按日程切分一天；日程之间的空隙按整点再切开。无日程时即 24 个 1h 块。 */
fun buildTimeline(schedules: List<Schedule>): List<TimeBlock> {
    val blocks = mutableListOf<TimeBlock>()
    var cursor = 0
    fun fillFree(until: Int) {
        while (cursor < until) {
            val next = minOf((cursor / 60 + 1) * 60, until)
            blocks += TimeBlock.Free(cursor, next)
            cursor = next
        }
    }
    for (s in schedules.sortedBy { it.start }) {
        fillFree(s.start)
        blocks += TimeBlock.Busy(s)
        cursor = s.end
    }
    fillFree(DAY_MINUTES)
    return blocks
}

fun hasOverlap(schedules: List<Schedule>, start: Int, end: Int, ignoreId: Long? = null) =
    schedules.any { it.id != ignoreId && start < it.end && it.start < end }

fun formatMinute(m: Int) = "%02d:%02d".format(m / 60, m % 60)

/** 平常日作息模板（试行一周版，2026-10-20），⏰ 为四个硬时间点。 */
fun weekdayRoutine(): List<Schedule> {
    fun t(hm: String) = hm.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
    val base = System.currentTimeMillis()
    return listOf(
        "7:10" to "7:15" to "起床 · 抱一抱缓一缓，不催",
        "7:15" to "7:35" to "上厕所、洗脸、穿衣 · 一次说一件事；用图画流程表或一首歌计时",
        "7:40" to "8:05" to "⏰ 早餐 · 主食＋鸡蛋＋水果，最后喝 150ml 奶；坐餐桌，25 分钟到点收",
        "8:05" to "8:10" to "刷牙 · 吃完奶和早餐再刷",
        "8:10" to "8:15" to "穿鞋、背书包 · 书包和鞋前一晚摆在门口",
        "8:15" to "8:25" to "⏰ 出门 · 约 8:25 到教室，留 5 分钟余量",
        "8:25" to "16:00" to "在校 · 9:30 加餐、11:00 午餐、15:00 点心，家里不用管",
        "16:00" to "16:15" to "放学接他 · 路上聊聊今天，先不吃东西",
        "16:15" to "16:20" to "到家：洗手、换鞋、喝水 · 这一步是切换状态",
        "16:20" to "16:35" to "下午茶（小份） · 坐小桌，一两样软食，15 分钟内收",
        "16:35" to "17:10" to "户外活动 · 5 分钟热身 → 20 分钟主游戏 → 10 分钟降温",
        "17:15" to "17:35" to "帮忙摆碗筷、洗菜 · 自然过渡到吃饭",
        "17:40" to "18:10" to "⏰ 晚餐 · 坐餐桌，30 分钟到点收，不强迫吃完",
        "18:10" to "18:30" to "洗鼻子、坐马桶 · 留 20 分钟弹性，不赶",
        "18:30" to "19:00" to "自由玩；固定视频时段 · 18:30 开始，建议不超过 30 分钟",
        "19:00" to "19:30" to "亲子游戏、收玩具 · 安静一点，逐渐降温",
        "19:30" to "19:45" to "晚奶 180ml · 坐着喝，不边玩边喝",
        "19:45" to "19:55" to "刷牙 · 奶之后立刻刷，刷完只喝水",
        "19:55" to "20:20" to "洗澡 · 温水；洗完不再吃东西",
        "20:20" to "20:50" to "夜读 · 灯光调暗，同一套顺序",
        "20:50" to "21:30" to "⏰ 关灯 · 争取 21:15–21:30 前入睡",
    ).mapIndexed { i, (range, title) -> Schedule(base + i, t(range.first), t(range.second), title) }
}
