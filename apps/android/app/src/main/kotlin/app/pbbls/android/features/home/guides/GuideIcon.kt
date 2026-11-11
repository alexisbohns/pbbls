package app.pbbls.android.features.home.guides

import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.glyph.GlyphForm
import app.pbbls.android.core.model.home.GuideIllustration

/**
 * The icon an About Pebbles row carries on its chip (#1383 D13), chosen by the guide's illustration. A guide
 * with none, or one this build does not know (read as null), wears the round pebble.
 *
 * Android's own choice: iOS puts the guide's drawn 24 pt mark on the chip (`GuideIllustrationView`'s `.mark`),
 * so there is no iOS table to mirror here.
 */
internal fun GuideIllustration?.chipIcon(): PbblsGlyph =
    when (this) {
        null, GuideIllustration.PEBBLES -> PbblsGlyph.PEBBLE_ROUND
        GuideIllustration.STONES -> PbblsGlyph.STONES_ALIGNED_BOTTOM
        GuideIllustration.EMOTIONS -> PbblsGlyph.STONES_TRACK
        GuideIllustration.VALENCE -> PbblsGlyph.SCALE
        GuideIllustration.DOMAINS -> PbblsGlyph.ABSTRACT_LANDSCAPE
        GuideIllustration.COLLECTIONS -> PbblsGlyph.COLLECTION
        GuideIllustration.SOULS -> PbblsGlyph.PEOPLE
        GuideIllustration.GLYPHS -> PbblsGlyph.PEBBLE_GLYPH
        GuideIllustration.KARMA -> PbblsGlyph.KARMA_DOT
    }

/** The form the chip draws [chipIcon] in: the glyphs guide shows the dotted line, the rest the regular drawing. */
internal fun GuideIllustration?.chipForm(): GlyphForm = if (this == GuideIllustration.GLYPHS) GlyphForm.DOTTED else GlyphForm.REGULAR
