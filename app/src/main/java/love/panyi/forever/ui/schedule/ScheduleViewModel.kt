package love.panyi.forever.ui.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import love.panyi.forever.data.Completion
import love.panyi.forever.data.DAY_MINUTES
import love.panyi.forever.data.DayData
import love.panyi.forever.data.DayProgress
import love.panyi.forever.data.Schedule
import love.panyi.forever.data.ScheduleRecord
import love.panyi.forever.data.ScheduleRepository
import love.panyi.forever.data.TimeBlock
import love.panyi.forever.data.buildTimeline
import love.panyi.forever.data.dayProgress
import love.panyi.forever.data.hasOverlap
import love.panyi.forever.data.isDone
import love.panyi.forever.data.weekdayRoutine
import java.time.LocalDate
import java.time.LocalTime

data class ScheduleUiState(
    val date: LocalDate,
    val nowMinute: Int,
    /** null 表示该日期的数据尚未加载完。 */
    val day: DayData? = null,
    /** 正在编辑的日程（id == 0 表示新建）。 */
    val editingSchedule: Schedule? = null,
    val scheduleError: String? = null,
    /** 正在编辑后备活动的空闲块。 */
    val editingBackup: TimeBlock.Free? = null,
    /** 正在补记完成情况的日程。 */
    val editingRecord: Schedule? = null,
) {
    val schedules get() = day?.schedules.orEmpty()
    val backups get() = day?.backups.orEmpty()
    val records get() = day?.records.orEmpty()
    val timeline = buildTimeline(schedules)
    val today: LocalDate = LocalDate.now()
    val isToday get() = date == today

    /** 只有今天才有「此刻」，其他日期为 null。 */
    val progress: DayProgress? = if (isToday) dayProgress(schedules, records, nowMinute) else null

    /** 比今天早的日期整天算已过去；今天按结束时间判断。 */
    fun isDone(s: Schedule) =
        isDone(records[s.id], date < today || (date == today && s.end <= nowMinute))
}

private const val KEY_DATE = "date"

private fun currentMinute() = LocalTime.now().let { it.hour * 60 + it.minute }

class ScheduleViewModel(
    private val savedState: SavedStateHandle,
    private val repository: ScheduleRepository,
) : ViewModel() {

    private val dateFlow = savedState.getStateFlow(KEY_DATE, LocalDate.now().toString())

    private val _uiState = MutableStateFlow(ScheduleUiState(LocalDate.parse(dateFlow.value), currentMinute()))
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val state get() = _uiState.value

    init {
        viewModelScope.launch {
            dateFlow.collectLatest { value ->
                val date = LocalDate.parse(value)
                _uiState.update { it.copy(date = date, day = null) }
                repository.observeDay(date).collect { day -> _uiState.update { it.copy(day = day) } }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                _uiState.update { it.copy(nowMinute = currentMinute()) }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        savedState[KEY_DATE] = date.toString()
    }

    fun goToToday() = selectDate(LocalDate.now())

    /** 当天还没有日程时，一键填入平常日作息。 */
    fun applyRoutine() {
        val date = state.date
        viewModelScope.launch { repository.saveSchedules(date, weekdayRoutine()) }
    }

    fun newSchedule() {
        val start = if (state.isToday) state.nowMinute / 60 * 60 else 9 * 60
        editSchedule(Schedule(0, start, minOf(start + 60, DAY_MINUTES), ""))
    }

    fun editSchedule(schedule: Schedule) =
        _uiState.update { it.copy(editingSchedule = schedule, scheduleError = null, editingRecord = null) }

    fun editBackup(block: TimeBlock.Free) = _uiState.update { it.copy(editingBackup = block) }

    fun editRecord(schedule: Schedule) = _uiState.update { it.copy(editingRecord = schedule) }

    fun dismissDialog() = _uiState.update {
        it.copy(editingSchedule = null, scheduleError = null, editingBackup = null, editingRecord = null)
    }

    fun saveSchedule(s: Schedule) {
        val error = when {
            s.title.isBlank() -> "请填写标题"
            s.end <= s.start -> "结束时间需晚于开始时间"
            hasOverlap(state.schedules, s.start, s.end, ignoreId = s.id) -> "与已有日程时间重叠"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(scheduleError = error) }
            return
        }
        val saved = if (s.id == 0L) s.copy(id = System.currentTimeMillis()) else s
        writeSchedules(state.schedules.filter { it.id != saved.id } + saved)
    }

    fun deleteSchedule() {
        val id = state.editingSchedule?.id ?: return
        val date = state.date
        val records = state.records - id
        writeSchedules(state.schedules.filter { it.id != id })
        viewModelScope.launch { repository.saveRecords(date, records) }
    }

    private fun writeSchedules(schedules: List<Schedule>) {
        val date = state.date
        dismissDialog()
        viewModelScope.launch { repository.saveSchedules(date, schedules) }
    }

    fun saveBackup(text: String) {
        val block = state.editingBackup ?: return
        val date = state.date
        val backups = if (text.isBlank()) state.backups - block.start else state.backups + (block.start to text.trim())
        dismissDialog()
        viewModelScope.launch { repository.saveBackups(date, backups) }
    }

    /** 此刻卡上的「提前完成」：结束时间记为现在。 */
    fun completeEarly(schedule: Schedule) =
        writeRecord(schedule.id, ScheduleRecord(Completion.EARLY, schedule.start, state.nowMinute))

    fun saveRecord(record: ScheduleRecord) {
        val id = state.editingRecord?.id ?: return
        dismissDialog()
        writeRecord(id, record.copy(note = record.note.trim()))
    }

    /** 改回未完成：到点也不会再自动算完成。 */
    fun reopenRecord() {
        val s = state.editingRecord ?: return
        dismissDialog()
        writeRecord(s.id, ScheduleRecord(Completion.UNDONE, s.start, s.end))
    }

    private fun writeRecord(id: Long, record: ScheduleRecord) {
        val date = state.date
        val records = state.records + (id to record)
        viewModelScope.launch { repository.saveRecords(date, records) }
    }
}
