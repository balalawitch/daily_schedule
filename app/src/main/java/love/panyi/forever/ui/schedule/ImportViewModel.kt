package love.panyi.forever.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import love.panyi.forever.data.AiEngine
import love.panyi.forever.data.AiException
import love.panyi.forever.data.AiSettings
import love.panyi.forever.data.CloudConfig
import love.panyi.forever.data.PlanParseException
import love.panyi.forever.data.Schedule
import love.panyi.forever.data.ScheduleGenerator
import love.panyi.forever.data.ScheduleRepository
import love.panyi.forever.data.ScheduleTemplate
import love.panyi.forever.data.uniqueName
import love.panyi.forever.data.validateSchedule

data class ImportUiState(
    /** 输入面板是否打开。 */
    val open: Boolean = false,
    val generating: Boolean = false,
    /** 输入面板里显示的错误。 */
    val error: String? = null,
    val engine: AiEngine = AiEngine.CLOUD,
    val cloud: CloudConfig = CloudConfig(),
    val showSettings: Boolean = false,
    val testing: Boolean = false,
    /** "连接成功" 或错误原因。 */
    val testResult: String? = null,
    /** 非 null 时显示预览。 */
    val preview: List<ScheduleTemplate>? = null,
    val tab: Int = 0,
    /** 预览里正在编辑的条目。 */
    val editing: Schedule? = null,
    val editError: String? = null,
    /** Snackbar 文案，显示后调用 messageShown()。 */
    val message: String? = null,
) {
    val ready get() = engine == AiEngine.CLOUD && cloud.isComplete
}

private const val MAX_INPUT = 2000

class ImportViewModel(
    private val repository: ScheduleRepository,
    private val settings: AiSettings,
    private val createGenerator: (AiEngine, CloudConfig) -> ScheduleGenerator?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val state get() = _uiState.value

    private var lastText = ""
    private var generateJob: Job? = null
    private var testJob: Job? = null
    private var saveJob: Job? = null

    /** 每次启动 / 取消生成都自增，避免被取代的旧任务在 finally 里把新任务的 generating 清掉。 */
    private var generation = 0

    fun open() {
        _uiState.update { it.copy(open = true, error = null) }
        viewModelScope.launch {
            // Keystore 解密可能较慢，放到 IO 线程
            val (engine, cloud) = withContext(Dispatchers.IO) { settings.engine to settings.cloudConfig() }
            _uiState.update {
                val next = it.copy(engine = engine, cloud = cloud)
                next.copy(showSettings = !next.ready)
            }
        }
    }

    fun close() {
        cancelGenerate()
        _uiState.update { it.copy(open = false, error = null, showSettings = false) }
    }

    fun generate(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_INPUT) return
        lastText = trimmed
        generateJob?.cancel()
        val token = ++generation
        _uiState.update { it.copy(generating = true, error = null) }
        generateJob = viewModelScope.launch {
            try {
                val generator = createGenerator(state.engine, state.cloud)
                if (generator == null) {
                    fail("请先在设置里配置 AI 引擎")
                    return@launch
                }
                val result = try {
                    generator.generate(trimmed, null)
                } catch (e: PlanParseException) {
                    if (e.empty) throw e
                    generator.generate(trimmed, e.message)
                }
                _uiState.update { it.copy(preview = result, tab = 0, open = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlanParseException) {
                fail(if (e.empty) e.message.orEmpty() else "没能理解这段文字，换个说法试试：${e.message}")
            } catch (e: AiException) {
                fail(e.message.orEmpty())
            } catch (_: Exception) {
                fail("生成失败，请稍后重试")
            } finally {
                if (token == generation) _uiState.update { it.copy(generating = false) }
            }
        }
    }

    /** 失败时一律回到输入面板显示错误（重新生成失败时输入面板此前是关着的）。 */
    private fun fail(message: String) {
        _uiState.update { it.copy(error = message, open = true) }
    }

    fun cancelGenerate() {
        generation++
        generateJob?.cancel()
        generateJob = null
        _uiState.update { it.copy(generating = false) }
    }

    /**
     * 关闭预览，回到输入面板并用上次的文字重新生成：生成中在输入面板里显示进度、可取消，
     * 成功后输入面板关闭、显示新预览；失败则停在输入面板显示错误。
     */
    fun regenerate() {
        if (lastText.isEmpty()) return
        _uiState.update { it.copy(preview = null, editing = null, editError = null, tab = 0, open = true) }
        generate(lastText)
    }

    fun selectTab(index: Int) {
        val size = state.preview?.size ?: return
        _uiState.update { it.copy(tab = index.coerceIn(0, size - 1), editing = null, editError = null) }
    }

    fun editItem(s: Schedule) {
        _uiState.update { it.copy(editing = s, editError = null) }
    }

    fun dismissEdit() {
        _uiState.update { it.copy(editing = null, editError = null) }
    }

    /** 按 id 替换当前组里的条目；id 不在组里则当作新增（分配组内唯一的新 id）。 */
    fun saveItem(s: Schedule) {
        val preview = state.preview ?: return
        val group = preview.getOrNull(state.tab) ?: return
        val error = validateSchedule(s, group.items)
        if (error != null) {
            _uiState.update { it.copy(editError = error) }
            return
        }
        val items = if (group.items.any { it.id == s.id }) {
            group.items.map { if (it.id == s.id) s else it }
        } else {
            group.items + s.copy(id = (group.items.maxOfOrNull { it.id } ?: -1L) + 1)
        }.sortedBy { it.start }
        val updated = preview.mapIndexed { i, t -> if (i == state.tab) t.copy(items = items) else t }
        _uiState.update { it.copy(preview = updated, editing = null, editError = null) }
    }

    fun deleteItem() {
        val editing = state.editing ?: return
        val preview = state.preview ?: return
        val updated = preview
            .mapIndexed { i, t -> if (i == state.tab) t.copy(items = t.items.filter { it.id != editing.id }) else t }
            .filter { it.items.isNotEmpty() }
        _uiState.update {
            it.copy(
                preview = updated.ifEmpty { null },
                tab = it.tab.coerceIn(0, (updated.size - 1).coerceAtLeast(0)),
                editing = null,
                editError = null,
            )
        }
    }

    fun saveTemplates() {
        val preview = state.preview ?: return
        if (saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            val existing = repository.loadTemplates()
            val taken = existing.mapTo(mutableSetOf()) { it.name }
            val added = preview.map { t ->
                val name = uniqueName(t.name, taken)
                taken += name
                t.copy(name = name)
            }
            repository.saveTemplates(existing + added)
            _uiState.update {
                it.copy(
                    preview = null,
                    tab = 0,
                    editing = null,
                    editError = null,
                    message = "已存为 ${added.size} 个模板：${added.joinToString("、") { t -> t.name }}",
                )
            }
        }
    }

    fun closePreview() {
        _uiState.update { it.copy(preview = null, tab = 0, editing = null, editError = null) }
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettings = true, testResult = null) }
    }

    fun closeSettings() {
        testJob?.cancel()
        testJob = null
        _uiState.update { it.copy(showSettings = false, testing = false, testResult = null) }
    }

    /** 第①期只支持云端，其他引擎忽略。 */
    fun selectEngine(e: AiEngine) {
        if (e != AiEngine.CLOUD) return
        settings.selectEngine(e)
        _uiState.update { it.copy(engine = e) }
    }

    fun saveCloud(c: CloudConfig) {
        val trimmed = CloudConfig(c.url.trim(), c.apiKey.trim(), c.model.trim())
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settings.saveCloudConfig(trimmed) }
            _uiState.update { it.copy(cloud = trimmed, error = null) }
        }
    }

    fun testCloud(c: CloudConfig) {
        val trimmed = CloudConfig(c.url.trim(), c.apiKey.trim(), c.model.trim())
        testJob?.cancel()
        _uiState.update { it.copy(testing = true, testResult = null) }
        testJob = viewModelScope.launch {
            val result = try {
                val generator = createGenerator(AiEngine.CLOUD, trimmed)
                if (generator == null) {
                    "请填写完整，地址需以 https:// 开头"
                } else {
                    generator.ping()
                    "连接成功"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                e.message.orEmpty()
            } catch (e: PlanParseException) {
                e.message.orEmpty()
            } catch (_: Exception) {
                "连接失败，请稍后重试"
            }
            _uiState.update { it.copy(testing = false, testResult = result) }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }
}
