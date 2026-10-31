package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.data.ProfileRow
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.Log
import app.pbbls.android.core.model.LogPlatform
import app.pbbls.android.core.model.LogSpecies
import app.pbbls.android.core.model.LogStatus
import app.pbbls.android.core.model.RippleSummary
import app.pbbls.android.features.profile.ProfileContent
import app.pbbls.android.features.profile.ProfileUiState
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * You (#1383, spec D14): the header (pentagon avatar, under the top bar's settings action, the name in the hand,
 * "Member since", the Ripples line), the counters cut from one stone, the three Glyphs rows and
 * the achievements as cards (four badges, "12 earned"; the invitation when nothing is served) and
 * the Lab (the announcement on its split-stone band, three dated entries, "All updates"; with
 * nothing served, only the pill). Paper (with 2 × font, where
 * the counters stack, and French), Night, wide (the readable column holding), the dotted pebble
 * with the counts not served yet (the count-free lines), and a long name at 1 × shrinking to hold
 * its line (D18). Every value is pinned: a mid-month join date, so no zone moves the month.
 */
private val previewStrokes =
    listOf(
        GlyphStroke(
            d = "M40,40 C80,20 120,20 160,40 S180,120 160,160 S80,180 40,160 S20,80 40,40",
            width = 6.0,
        ),
    )

/** Four badges of families whose titles need no reference data, newest first (the server's order). */
private val previewShelf =
    listOf(
        "pebble_count" to 10,
        "first_soul" to null,
        "first_collection" to null,
        "first_glyph" to null,
    ).mapIndexed { i, (family, threshold) ->
        AchievementRecord(
            id = "a$i",
            slug = "$family-$i",
            family = family,
            threshold = threshold,
            sortOrder = i,
            karmaReward = 0,
            isActive = true,
        )
    }

/** A Lab note at noon UTC, so no zone moves its day; French copy alongside, as the admin writes it. */
private fun previewLog(
    n: Int,
    species: LogSpecies,
    day: Int,
    title: Pair<String, String>,
    summary: Pair<String, String>,
): Log {
    val at = OffsetDateTime.parse("2026-10-${day}T12:00:00Z")
    return Log(
        id = "00000000-0000-0000-0000-00000000000$n",
        species = species,
        platform = LogPlatform.ALL,
        status = LogStatus.SHIPPED,
        titleEn = title.first,
        titleFr = title.second,
        summaryEn = summary.first,
        summaryFr = summary.second,
        published = true,
        publishedAt = at,
        releasedAt = at,
        createdAt = at,
        reactionCount = 0,
    )
}

private val previewPost =
    previewLog(
        0,
        LogSpecies.ANNOUNCEMENT,
        31,
        "Cut from one stone" to "Taillées dans une pierre",
        "How the three quick actions on Home became one rock, split." to
            "Comment les trois actions rapides de l'accueil sont devenues une seule pierre, fendue.",
    )

private val previewEntries =
    listOf(
        previewLog(1, LogSpecies.FEATURE, 31, "Quick actions, cut from one stone" to "Actions rapides, d'une pierre", "Home" to "Accueil"),
        previewLog(
            2,
            LogSpecies.FEATURE,
            30,
            "Hand-drawn icons, everywhere" to "Des icônes dessinées, partout",
            "iOS · Android" to "iOS · Android",
        ),
        previewLog(3, LogSpecies.FEATURE, 28, "The year wheel" to "La roue de l'année", "Achievements" to "Succès"),
    )

private fun content(
    name: String = "Alexis",
    strokes: List<GlyphStroke>? = previewStrokes,
    served: Boolean = true,
) = ProfileUiState.Content(
    profile =
        ProfileRow(
            displayName = name,
            createdAt = OffsetDateTime.parse("2026-03-15T12:00:00Z"),
            glyphId = strokes?.let { "glyph-1" },
        ),
    glyphStrokes = strokes,
    karma = if (served) 1240 else null,
    pebbles = if (served) 318 else null,
    daysPracticed = if (served) 112 else null,
    ripple = if (served) RippleSummary(rippleLevel = 3, pebbles28d = 11, activeToday = true) else null,
    glyphsCarved = if (served) 12 else null,
    glyphsOwned = if (served) 5 else null,
    email = null,
    providers = emptyList(),
    shelf = if (served) previewShelf else emptyList(),
    shelfCount = if (served) 12 else null,
    announcement = if (served) previewPost else null,
    announcementCoverUrl = null,
    labEntries = if (served) previewEntries else emptyList(),
)

@Composable
private fun YouPreview(state: ProfileUiState) {
    PebblesTheme {
        ProfileContent(
            state = state,
            onRetry = {},
            onOpenSettings = {},
            onOpenGlyphs = {},
            onOpenAchievements = {},
            onOpenLab = {},
            onOpenAnnouncement = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, heightDp = YOU_HEIGHT)
@Preview(name = "fs2", showBackground = true, fontScale = 2f, heightDp = YOU_HEIGHT_LARGE_FONT)
@Preview(name = "fr", showBackground = true, locale = "fr", heightDp = YOU_HEIGHT)
@Composable
fun YouPaper() {
    YouPreview(content())
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES, heightDp = YOU_HEIGHT)
@Composable
fun YouNight() {
    YouPreview(content())
}

@PreviewTest
@Preview(name = "w840", showBackground = true, widthDp = 840, heightDp = YOU_HEIGHT)
@Preview(name = "w1024", showBackground = true, widthDp = 1024, heightDp = YOU_HEIGHT)
@Composable
fun YouWide() {
    YouPreview(content())
}

@PreviewTest
@Preview(showBackground = true, heightDp = YOU_HEIGHT)
@Preview(name = "fr", showBackground = true, locale = "fr", heightDp = YOU_HEIGHT)
@Composable
fun YouNotServed() {
    YouPreview(content(strokes = null, served = false))
}

@PreviewTest
@Preview(showBackground = true, heightDp = YOU_HEIGHT)
@Composable
fun YouLongName() {
    YouPreview(content(name = "Alexandrine Delacroix-Beaumont"))
}

/** Tall enough for the whole page, the Lab included, at 1 × and at 2 × font. */
private const val YOU_HEIGHT = 1900
private const val YOU_HEIGHT_LARGE_FONT = 3400
