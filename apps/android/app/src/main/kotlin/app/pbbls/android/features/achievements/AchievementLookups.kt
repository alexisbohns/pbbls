package app.pbbls.android.features.achievements

import androidx.compose.runtime.Immutable
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionFamily
import app.pbbls.android.core.model.EmotionWithPalette

/**
 * What the payload's ids and slugs point at (#1307), as the wraps' `WrapLookups` does for theirs:
 * a family by category slug, an emotion by id, a domain by id. The screen builds it from the
 * reference services; a preview builds it from fixtures. A null is a row the device does not know
 * yet: it draws in ink, or its name falls back.
 */
@Immutable
class AchievementLookups(
    val family: (String) -> EmotionFamily?,
    val emotion: (String) -> EmotionWithPalette?,
    val domain: (String) -> Domain?,
)
