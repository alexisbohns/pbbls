package app.pbbls.android.features.profile.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.glyph.views.GlyphBanner
import app.pbbls.android.features.glyph.views.GlyphBannerSubtitle
import app.pbbls.android.theme.PebblesTypography
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Profile header — ports iOS `ProfileBanner.swift`: the [GlyphBanner] with the
 * display name in the hand face and a "Member since <Month Year>" meta line,
 * formatted via the active locale (never pinned — repo rule).
 */
@Composable
fun ProfileBanner(
    displayName: String?,
    memberSince: OffsetDateTime?,
    glyphStrokes: List<GlyphStroke>?,
    modifier: Modifier = Modifier,
) {
    val subtitle =
        memberSince?.let {
            val formatted =
                it
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            GlyphBannerSubtitle.Meta(stringResource(R.string.profile_member_since, formatted))
        }
    GlyphBanner(
        title = displayName.orEmpty(),
        strokes = glyphStrokes,
        titleStyle = PebblesTypography.largeTitleHand,
        subtitle = subtitle,
        modifier = modifier,
    )
}
