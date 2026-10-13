package app.pbbls.android.features.path.complete

import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * The Complete flow (spec §6), in order. Valence before emotion keeps the
 * emotion categories ordered by the valence just chosen; privacy last sits
 * against the button that makes the pebble. No glyph step: the glyph is derived.
 */
enum class CompleteStep(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int? = null,
    val isOptional: Boolean = false,
    /** The body scrolls itself; the scaffold must not nest a second scroll. */
    val bringsOwnScroll: Boolean = false,
) {
    VALENCE(R.string.record_valence_title, R.string.record_valence_subtitle),
    EMOTION(R.string.record_emotion_title, bringsOwnScroll = true),
    DOMAIN(R.string.record_domain_title),
    SOULS(R.string.record_souls_title, isOptional = true, bringsOwnScroll = true),
    COLLECTION(R.string.record_collection_title, isOptional = true),
    PRIVACY(R.string.record_privacy_title),
    SUCCESS(R.string.record_success_title),
    ;

    val dotIndex: Int? get() = if (this == SUCCESS) null else ordinal
    val next: CompleteStep? get() = entries.getOrNull(ordinal + 1)
    val previous: CompleteStep? get() = if (ordinal == 0) null else entries[ordinal - 1]

    companion object {
        val counted: List<CompleteStep> = entries.filter { it != SUCCESS }
    }
}
