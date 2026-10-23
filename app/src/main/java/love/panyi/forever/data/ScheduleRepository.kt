package love.panyi.forever.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 某一天的全部数据：日程 + 后备活动（空闲块起始分钟 -> 内容）+ 完成记录（日程 id -> 记录）。 */
data class DayData(
    val schedules: List<Schedule>,
    val backups: Map<Int, String>,
    val records: Map<Long, ScheduleRecord> = emptyMap(),
)

/** ViewModel 唯一依赖的数据接口；读取以 Flow 形式提供，数据变化时自动重新发射。 */
interface ScheduleRepository {
    fun observeDay(date: LocalDate): Flow<DayData>
    suspend fun saveSchedules(date: LocalDate, schedules: List<Schedule>)
    suspend fun saveBackups(date: LocalDate, backups: Map<Int, String>)
    suspend fun saveRecords(date: LocalDate, records: Map<Long, ScheduleRecord>)
}
