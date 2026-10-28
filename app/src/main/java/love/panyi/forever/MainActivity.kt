package love.panyi.forever

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import love.panyi.forever.data.AiEngine
import love.panyi.forever.data.AiSettingsStore
import love.panyi.forever.data.CloudGenerator
import love.panyi.forever.data.ScheduleStore
import love.panyi.forever.ui.schedule.ImportViewModel
import love.panyi.forever.ui.schedule.ScheduleScreen
import love.panyi.forever.ui.schedule.ScheduleViewModel
import love.panyi.forever.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    // 两个 ViewModel 共用同一个 ScheduleStore，保证它们看到同一份内存状态
    private val store by lazy { ScheduleStore(applicationContext) }

    private val viewModel: ScheduleViewModel by viewModels {
        viewModelFactory {
            initializer { ScheduleViewModel(createSavedStateHandle(), store) }
        }
    }

    private val importViewModel: ImportViewModel by viewModels {
        viewModelFactory {
            initializer {
                ImportViewModel(store, AiSettingsStore(applicationContext)) { engine, cloud ->
                    if (engine == AiEngine.CLOUD && cloud.isComplete) CloudGenerator(cloud) else null
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ScheduleScreen(viewModel, importViewModel)
            }
        }
    }
}
