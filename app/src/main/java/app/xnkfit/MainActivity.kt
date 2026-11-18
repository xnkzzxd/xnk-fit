package app.xnkfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.xnkfit.ui.XnkApp
import app.xnkfit.ui.common.XnkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XnkTheme {
                XnkApp()
            }
        }
    }
}
