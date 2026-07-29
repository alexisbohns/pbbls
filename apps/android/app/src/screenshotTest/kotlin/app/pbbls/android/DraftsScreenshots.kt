package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.features.path.DraftsContent
import app.pbbls.android.features.path.models.PebbleDraftPayload
import app.pbbls.android.services.PebbleDraftRecord
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Render-to-view previews for the M47 drafts cover — the SDK-less maintainer
 * reviews these as the `ui-screenshots` CI artifact. Drives the stateless
 * [DraftsContent], never the service-reading `DraftsScreen`.
 *
 * A fixed `now` keeps the "saved N ago" labels stable across runs.
 */
private val NOW: OffsetDateTime = OffsetDateTime.of(2026, 7, 30, 12, 0, 0, 0, ZoneOffset.UTC)

private fun record(
    id: String,
    name: String?,
    minutesAgo: Long,
): PebbleDraftRecord =
    PebbleDraftRecord(
        id = id,
        payload = PebbleDraftPayload(name = name),
        updatedAt = NOW.minusMinutes(minutesAgo),
    )

private val sampleDrafts =
    listOf(
        record("d1", "Argument with Lea", 12),
        // The "just a name" case is the whole point of quick capture, and the
        // no-name case is what the placeholder copy is for.
        record("d2", null, 60 * 5),
        record("d3", "That thing at the market I want to come back to", 60 * 26),
    )

@PreviewTest
@Preview(showBackground = true)
@Composable
fun DraftsListLight() {
    PebblesTheme {
        DraftsContent(
            drafts = sampleDrafts,
            isLoading = false,
            loadFailed = false,
            onRetry = {},
            onResume = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DraftsListDark() {
    PebblesTheme {
        DraftsContent(
            drafts = sampleDrafts,
            isLoading = false,
            loadFailed = false,
            onRetry = {},
            onResume = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun DraftsEmptyLight() {
    PebblesTheme {
        DraftsContent(
            drafts = emptyList(),
            isLoading = false,
            loadFailed = false,
            onRetry = {},
            onResume = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DraftsLoadErrorDark() {
    PebblesTheme {
        DraftsContent(
            drafts = emptyList(),
            isLoading = false,
            loadFailed = true,
            onRetry = {},
            onResume = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}
