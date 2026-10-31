package app.pbbls.android.features.home.guides

import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.model.home.GuideIllustration

/**
 * The icon an About Pebbles row carries on its chip (#1383 D13), chosen by the guide's illustration. A guide
 * with none, or one this build does not know (read as null), wears the round pebble.
 *
 * Android's own choice: iOS puts the guide's drawn 24 pt mark on the chip (`GuideIllustrationView`'s `.mark`),
 * so there is no iOS table to mirror here.
 */
internal fun GuideIllustration?.chipIcon(): PbblsIcon =
    when (this) {
        null, GuideIllustration.PEBBLES -> PbblsIcon.PEBBLE_ROUND
        GuideIllustration.STONES -> PbblsIcon.STONES_ALIGNED_BOTTOM
        GuideIllustration.EMOTIONS -> PbblsIcon.STONES_TRACK
        GuideIllustration.VALENCE -> PbblsIcon.SCALE
        GuideIllustration.DOMAINS -> PbblsIcon.ABSTRACT_LANDSCAPE
        GuideIllustration.COLLECTIONS -> PbblsIcon.COLLECTION
        GuideIllustration.SOULS -> PbblsIcon.PEOPLE
        GuideIllustration.GLYPHS -> PbblsIcon.PEBBLE_DOTTED_GLYPH
        GuideIllustration.KARMA -> PbblsIcon.KARMA_DOT
    }
