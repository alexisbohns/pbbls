package app.pbbls.android.features.profile.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.theme.PebblesIconToken
import app.pbbls.android.theme.PebblesSectionHeader
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.profileCard

/**
 * Profile "Collections" card — ports iOS `ProfileCollectionsCard.swift`: a
 * chevroned header and a horizontal scroller of [ProfileCollectionCard]s,
 * with the dashed "New collection" tile when the list is empty.
 *
 * Tap destinations (list, detail, create) belong to sub-project E — the
 * optional callbacks keep the card informational until they're wired, so no
 * dead tap targets ship in the interim (design D11's no-dead-chrome rule).
 */
@Composable
fun ProfileCollectionsCard(
    collections: List<Collection>,
    hasLoaded: Boolean,
    modifier: Modifier = Modifier,
    onOpenList: (() -> Unit)? = null,
    onOpenCollection: ((Collection) -> Unit)? = null,
    onCreate: (() -> Unit)? = null,
) {
    val system = PebblesTheme.colors.system
    Column(
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
        modifier = modifier.fillMaxWidth().profileCard(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                if (onOpenList != null) {
                    Modifier.fillMaxWidth().clickable(onClick = onOpenList)
                } else {
                    Modifier.fillMaxWidth()
                },
        ) {
            PebblesSectionHeader(text = stringResource(R.string.profile_collections_header))
            Spacer(Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = system.muted,
                modifier = Modifier.size(PebblesIconToken.MEDIUM.size),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
            if (collections.isEmpty() && hasLoaded) {
                item {
                    ProfileCollectionCard(
                        collection = null,
                        modifier =
                            if (onCreate != null) Modifier.clickable(onClick = onCreate) else Modifier,
                    )
                }
            } else {
                items(collections, key = { it.id }) { collection ->
                    ProfileCollectionCard(
                        collection = collection,
                        modifier =
                            if (onOpenCollection != null) {
                                Modifier.clickable { onOpenCollection(collection) }
                            } else {
                                Modifier
                            },
                    )
                }
            }
        }
    }
}
