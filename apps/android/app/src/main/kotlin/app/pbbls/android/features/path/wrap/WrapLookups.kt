package app.pbbls.android.features.path.wrap

import androidx.compose.runtime.Immutable
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionFamily
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.SoulWithGlyph

/**
 * What a wrap's ids point at (#1207): the payload names moments, souls, families and domains by id
 * or slug, and the wraps resolve them here. The screen builds it from the feed and the reference
 * services; a preview builds it from fixtures. A null is a row the device does not know yet, and
 * the row is skipped rather than shown wrong.
 */
@Immutable
class WrapLookups(
    val moment: (String) -> PathMoment?,
    val soul: (String) -> SoulWithGlyph?,
    val family: (String) -> EmotionFamily?,
    val domain: (String) -> Domain?,
    val paletteFor: (PathMoment) -> EmotionPalette?,
)
