package app.pbbls.android.features.path.complete

import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * The Complete flow (record-flow D1, D10), in order: a valence, an emotion, then Life, where domains,
 * people and the button that makes the pebble share one page. Valence before emotion keeps the
 * emotion categories ordered by the valence just chosen. No glyph step: the glyph is derived.
 * Collection and visibility are not steps any more: they are what the end screen lets you add.
 */
enum class CompleteStep(
    @StringRes val titleRes: Int,
    /** The body scrolls itself; the scaffold must not nest a second scroll. */
    val bringsOwnScroll: Boolean = false,
) {
    VALENCE(R.string.record_valence_title),
    EMOTION(R.string.record_emotion_title, bringsOwnScroll = true),
    LIFE(R.string.record_life_title, bringsOwnScroll = true),
    SUCCESS(R.string.record_success_title),
    ;

    val dotIndex: Int? get() = if (this == SUCCESS) null else ordinal
    val next: CompleteStep? get() = entries.getOrNull(ordinal + 1)
    val previous: CompleteStep? get() = if (ordinal == 0) null else entries[ordinal - 1]

    companion object {
        val counted: List<CompleteStep> = entries.filter { it != SUCCESS }
    }
}
