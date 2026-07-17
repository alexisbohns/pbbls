package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.lab.AnnouncementDetailContent
import app.pbbls.android.features.lab.LabContent
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.features.lab.models.LogPlatform
import app.pbbls.android.features.lab.models.LogSpecies
import app.pbbls.android.features.lab.models.LogStatus
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Lab previews (M44 #590): the full Lab body — community card, announcement
 * rows, the three timeline modes, reaction states — light and dark. The
 * screen reads services; this pure content layer is the review surface.
 */
private fun previewLog(
    id: String,
    titleEn: String,
    summaryEn: String,
    species: LogSpecies = LogSpecies.FEATURE,
    status: LogStatus = LogStatus.SHIPPED,
    releasedAt: OffsetDateTime? = null,
    reactionCount: Int = 0,
): Log =
    Log(
        id = id,
        species = species,
        platform = LogPlatform.ALL,
        status = status,
        titleEn = titleEn,
        summaryEn = summaryEn,
        published = true,
        publishedAt = OffsetDateTime.parse("2026-06-20T12:00:00Z"),
        releasedAt = releasedAt,
        createdAt = OffsetDateTime.parse("2026-06-01T12:00:00Z"),
        reactionCount = reactionCount,
    )

private val announcements =
    listOf(
        previewLog(
            id = "11111111-1111-1111-1111-111111111111",
            titleEn = "Pebbles turns one",
            summaryEn = "A year of tiny moments, and a look at what's next for the path ahead.",
            species = LogSpecies.ANNOUNCEMENT,
        ),
    )

private val changelog =
    listOf(
        previewLog(
            id = "22222222-2222-2222-2222-222222222222",
            titleEn = "Photos on pebbles",
            summaryEn = "Attach a snap to any pebble and watch it reveal on the detail page.",
            releasedAt = OffsetDateTime.parse("2026-07-10T09:00:00Z"),
        ),
        previewLog(
            id = "33333333-3333-3333-3333-333333333333",
            titleEn = "Glyph studio",
            summaryEn = "Carve your own glyphs and swap karma for community favorites.",
            releasedAt = OffsetDateTime.parse("2026-07-02T09:00:00Z"),
        ),
    )

private val initiatives =
    listOf(
        previewLog(
            id = "44444444-4444-4444-4444-444444444444",
            titleEn = "Android app",
            summaryEn = "The full Pebbles experience, natively on Android.",
            status = LogStatus.IN_PROGRESS,
        ),
    )

private val backlog =
    listOf(
        previewLog(
            id = "55555555-5555-5555-5555-555555555555",
            titleEn = "Widgets",
            summaryEn = "A home-screen widget for your latest pebble.",
            status = LogStatus.BACKLOG,
            reactionCount = 12,
        ),
        previewLog(
            id = "66666666-6666-6666-6666-666666666666",
            titleEn = "Yearly recap",
            summaryEn = "A look back over your path, one year at a time.",
            status = LogStatus.BACKLOG,
            reactionCount = 3,
        ),
    )

@Composable
private fun LabGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .width(400.dp),
    ) {
        LabContent(
            announcements = announcements,
            changelog = changelog,
            initiatives = initiatives,
            backlog = backlog,
            reactedIds = setOf("55555555-5555-5555-5555-555555555555"),
            coverUrl = { null },
            onOpenAnnouncement = {},
            onToggleReaction = {},
            onOpenCommunity = {},
            onSeeAllChangelog = {},
            onSeeAllBacklog = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// A body exercising the whole V1 markdown subset (design D5) — headings stay
// plain, paragraphs get inline styling, the list renders literal.
private val detailLog =
    previewLog(
        id = "77777777-7777-7777-7777-777777777777",
        titleEn = "Pebbles turns one",
        summaryEn = "A year of tiny moments.",
        species = LogSpecies.ANNOUNCEMENT,
    ).copy(
        bodyMdEn =
            "# A year in\n\nWe shipped **photos**, the *glyph studio*, and " +
                "`buy_glyph`.\n\n## What's next\n\nRead the [roadmap](https://pbbls.app) — " +
                "spoiler: ~nothing~ everything.\n\n- lists render literal\n- on purpose",
    )

@Composable
private fun DetailGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .width(400.dp),
    ) {
        AnnouncementDetailContent(log = detailLog, coverUrl = null)
    }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 1200)
@Composable
fun LabContentLight() {
    PebblesTheme { LabGallery() }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 1200, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun LabContentDark() {
    PebblesTheme { LabGallery() }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 900)
@Composable
fun AnnouncementDetailLight() {
    PebblesTheme { DetailGallery() }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 900, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AnnouncementDetailDark() {
    PebblesTheme { DetailGallery() }
}
