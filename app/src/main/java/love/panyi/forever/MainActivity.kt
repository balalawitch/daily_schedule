package love.panyi.forever

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import love.panyi.forever.data.ScheduleStore
import love.panyi.forever.ui.schedule.ScheduleScreen
import love.panyi.forever.ui.schedule.ScheduleViewModel
import love.panyi.forever.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ScheduleViewModel by viewModels {
        viewModelFactory {
            initializer { ScheduleViewModel(createSavedStateHandle(), ScheduleStore(applicationContext)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ScheduleScreen(viewModel)
            }
        }
    }
}
