package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.profile.CollectionModePicker
import app.pbbls.android.features.profile.components.CollectionModeBadge
import app.pbbls.android.features.profile.models.CollectionMode
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Collections-management previews (#569): the mode badge in all three modes
 * (null renders nothing) and the form's mode picker in none-selected and
 * mode-selected states — light and dark. The collections screens themselves
 * read services, so the review surface is this pure-component gallery plus
 * the on-device pass.
 */
@Composable
private fun CollectionsGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .padding(24.dp)
                .width(360.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CollectionModeBadge(mode = CollectionMode.STACK)
            CollectionModeBadge(mode = CollectionMode.PACK)
            CollectionModeBadge(mode = CollectionMode.TRACK)
            CollectionModeBadge(mode = null)
        }
        CollectionModePicker(selected = null, onSelect = {})
        CollectionModePicker(selected = CollectionMode.TRACK, onSelect = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun CollectionsGalleryLight() {
    PebblesTheme { CollectionsGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CollectionsGalleryDark() {
    PebblesTheme { CollectionsGallery() }
}
