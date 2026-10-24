package app.pbbls.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pbbls.android.core.designsystem.PebblesTheme

/**
 * The Spine lab (#1122, plan Task 4.5): a debug-only launcher entry showing the
 * lit stones and the fixture week on a device before Part 6 wires the Spine into
 * the Path. No Hilt, no services, no network: the brand scheme following the
 * system's dark mode, three tabs, pressable stones.
 */
class SpineLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PebblesTheme {
                Surface(color = PebblesTheme.spine.colors.ground, modifier = Modifier.fillMaxSize()) {
                    SpineLab()
                }
            }
        }
    }
}

private val TABS = listOf("Stones", "Week", "Month")

@Composable
private fun SpineLab() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PrimaryTabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) {
            0 ->
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    LitStoneGallery(pressable = true)
                    LitStoneSeeds(pressable = true)
                }
            1 -> SpineLabWeek(Modifier.fillMaxSize())
            else -> SpineLabMonth(Modifier.fillMaxSize())
        }
    }
}
