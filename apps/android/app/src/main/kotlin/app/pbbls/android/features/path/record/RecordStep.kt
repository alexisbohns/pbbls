package app.pbbls.android.features.path.record

import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * The eleven screens of the record flow, in order — ports iOS `RecordStep`
 * (M58 design D2).
 *
 * The order carries three deliberate dependencies, and a port that keeps the
 * eleven screens but shuffles them has kept the cost and dropped the reason:
 * - [PHOTO] before [WHEN], so the date step arrives pre-filled from the photo's
 *   EXIF `DateTimeOriginal` instead of mutating under the user.
 * - [VALENCE] before [EMOTION], so `EmotionPickerGrouping` has a valence to
 *   order the categories by. In the form the two rows sit side by side and the
 *   ordering is a lucky accident of which one the user opened first.
 * - [PRIVACY] last, against the publish button, because the grade is the
 *   decision most coupled to "am I ready for other people to see this".
 *
 * [SUCCESS] is terminal: no dot, no back, no close — only the exit button.
 *
 * Copy lives in `strings.xml` (the [OnboardingStep] convention); the view layer
 * renders [titleRes] / [subtitleRes] without branching on which step it is.
 */
enum class RecordStep(
    @StringRes val titleRes: Int,
    /** Only the steps whose ask needs a second line carry one. */
    @StringRes val subtitleRes: Int? = null,
    /** Steps the user may pass without answering. Everything else gates. */
    val isOptional: Boolean = false,
    /**
     * True when the step's content is already scrollable, so [RecordStepScaffold]
     * must **not** wrap it in one of its own.
     *
     * This is a correctness flag, not a style one: the picker bodies the flow
     * reuses (`EmotionPickerBody`, `SoulPickerBody`, `GlyphPickerGrid`) each
     * carry a `verticalScroll`, and a vertically scrollable child measured
     * inside another vertical scroll gets an infinite height constraint and
     * throws. The sheets never hit this because a `ModalBottomSheet` does not
     * scroll its own content. The valence fan is fixed-height content, so it
     * takes the scaffold's scroll — its roll wins the vertical drag by
     * consuming it.
     */
    val bringsOwnScroll: Boolean = false,
) {
    PHOTO(R.string.record_photo_title, R.string.record_photo_subtitle, isOptional = true),
    WHEN(R.string.record_when_title),
    NAME(R.string.record_name_title),
    VALENCE(R.string.record_valence_title, R.string.record_valence_subtitle),
    EMOTION(R.string.record_emotion_title, bringsOwnScroll = true),
    DOMAIN(R.string.record_domain_title),
    SOULS(R.string.record_souls_title, isOptional = true, bringsOwnScroll = true),
    COLLECTION(R.string.record_collection_title, isOptional = true),
    GLYPH(R.string.record_glyph_title, R.string.record_glyph_subtitle, isOptional = true, bringsOwnScroll = true),
    PRIVACY(R.string.record_privacy_title),
    SUCCESS(R.string.record_success_title),
    ;

    /** 0-based dot index, or null for the uncounted terminal step. */
    val dotIndex: Int?
        get() = if (this == SUCCESS) null else ordinal

    val next: RecordStep?
        get() = entries.getOrNull(ordinal + 1)

    val previous: RecordStep?
        get() = if (ordinal == 0) null else entries[ordinal - 1]

    companion object {
        /** The steps the progress dots represent. */
        val counted: List<RecordStep> = entries.filter { it != SUCCESS }
    }
}
