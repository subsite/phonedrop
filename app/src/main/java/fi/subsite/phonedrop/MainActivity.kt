package fi.subsite.phonedrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import fi.subsite.phonedrop.ui.KeysScreen
import fi.subsite.phonedrop.ui.MainScreen
import fi.subsite.phonedrop.ui.PhoneDropTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneDropTheme {
                var showKeys by rememberSaveable { mutableStateOf(false) }
                if (showKeys) {
                    KeysScreen(onBack = { showKeys = false })
                } else {
                    MainScreen(onOpenKeys = { showKeys = true })
                }
            }
        }
    }
}
