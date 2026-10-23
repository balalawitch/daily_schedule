package love.panyi.forever

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import love.panyi.forever.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = ScheduleStore(this)
        setContent {
            MyApplicationTheme {
                ScheduleScreen(store)
            }
        }
    }
}
