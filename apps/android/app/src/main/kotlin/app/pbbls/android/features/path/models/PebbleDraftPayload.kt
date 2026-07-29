package app.pbbls.android.features.path.models

import app.pbbls.android.features.pebblemedia.models.FormSnap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * The `payload` jsonb of a `pebble_drafts` row (M47) — a **partial**
 * [PebbleCreatePayload].
 *
 * Keyed like the wire, not like [PebbleDraft]: web and iOS write this same
 * shape, so a draft saved on one surface resumes on the others and publishing is
 * a straight pass-through. See
 * `docs/superpowers/specs/2026-07-29-drafts-and-autosave-design.md` (D2).
 *
 * Every field is nullable with a `null` default and **`encodeDefaults` stays
 * off** on the encoding `Json`, which is what implements "omit unset keys".
 * Note this is the opposite configuration from [PebbleCreatePayload], which
 * needs `explicitNulls = true` so edit can clear `description` / `glyph_id`; a
 * draft has no prior value to clear, so absence is the right encoding.
 *
 * Deliberately absent: `valence`. It is stored decomposed as [intensity] +
 * [positiveness] exactly as the wire wants it, which is why neither [Valence]
 * nor [Visibility] needed a serialization change for M47 — this holds
 * primitives only.
 */
@Serializable
data class PebbleDraftPayload(
    val name: String? = null,
    val description: String? = null,
    @SerialName("happened_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val happenedAt: OffsetDateTime? = null,
    val intensity: Int? = null,
    val positiveness: Int? = null,
    val visibility: Visibility? = null,
    @SerialName("emotion_id")
    val emotionId: String? = null,
    @SerialName("domain_ids")
    val domainIds: List<String>? = null,
    @SerialName("soul_ids")
    val soulIds: List<String>? = null,
    @SerialName("collection_ids")
    val collectionIds: List<String>? = null,
    @SerialName("glyph_id")
    val glyphId: String? = null,
    val snaps: List<PebbleSnapPayload>? = null,
) {
    /**
     * True when nothing a user would recognise as their own input is present.
     * `happenedAt` / `intensity` / `positiveness` / `visibility` never count:
     * they are always defaulted, and counting them would make every freshly
     * opened composer look like an unsaved draft.
     */
    val isEmpty: Boolean
        get() =
            name == null &&
                description == null &&
                emotionId == null &&
                glyphId == null &&
                domainIds.isNullOrEmpty() &&
                soulIds.isNullOrEmpty() &&
                collectionIds.isNullOrEmpty() &&
                snaps.isNullOrEmpty()

    /** The snap to seed onto a `SnapUploadCoordinator` on resume, if any. */
    val existingSnap: FormSnap.Existing?
        get() = snaps?.firstOrNull()?.let { FormSnap.Existing(id = it.id, storagePath = it.storagePath) }

    companion object {
        /**
         * Composer state → draft payload. Empty strings and empty lists become
         * `null` so [isEmpty] can tell "untouched" from "deliberately blank".
         *
         * `formSnap` is passed separately because snap state lives on the
         * `SnapUploadCoordinator`, not on the draft — the same split
         * [PebbleCreatePayload] makes. Pass `null` for the local autosave
         * snapshot, which carries no media (design D3).
         */
        fun from(
            draft: PebbleDraft,
            formSnap: FormSnap? = null,
            userId: String? = null,
        ): PebbleDraftPayload {
            // trim() drops newlines as well as spaces: the description field is
            // multiline, so a draft holding only newlines is still empty.
            val trimmedName = draft.name.trim()
            val trimmedDescription = draft.description.trim()
            return PebbleDraftPayload(
                name = trimmedName.ifEmpty { null },
                description = trimmedDescription.ifEmpty { null },
                happenedAt = draft.happenedAt,
                // Both or neither: a null valence means the user has not picked
                // one, and hydration must not invent an intensity for them.
                intensity = draft.valence?.intensity,
                positiveness = draft.valence?.positiveness,
                visibility = draft.visibility,
                emotionId = draft.emotionId,
                domainIds = draft.domainId?.let { listOf(it) },
                soulIds = draft.soulIds.ifEmpty { null },
                collectionIds = draft.collectionId?.let { listOf(it) },
                glyphId = draft.glyphId,
                snaps = snapPayloads(formSnap, userId),
            )
        }

        private fun snapPayloads(
            formSnap: FormSnap?,
            userId: String?,
        ): List<PebbleSnapPayload>? =
            when (formSnap) {
                null -> null
                is FormSnap.Existing ->
                    listOf(PebbleSnapPayload(id = formSnap.id, storagePath = formSnap.storagePath, sortOrder = 0))
                is FormSnap.Pending -> {
                    // The bytes are already in Storage (upload happens at pick
                    // time), so the descriptor alone carries the photo.
                    userId?.let {
                        listOf(
                            PebbleSnapPayload(
                                id = formSnap.snap.id,
                                storagePath = formSnap.snap.storagePrefix(it),
                                sortOrder = 0,
                            ),
                        )
                    }
                }
            }
    }
}

/**
 * Ids the composer can verify synchronously from `ReferenceDataService`. Souls
 * and collections are user-owned and deletable, so a draft can outlive them;
 * emotions and domains are seeded reference rows and pass through untouched.
 *
 * Glyphs are deliberately absent: usability is `can_use_glyph(id, user)` (own ∪
 * system ∪ entitled), the same predicate `create_pebble` enforces, and only the
 * server can answer it. `CreatePebbleScreen` verifies it after hydrating.
 */
data class KnownDraftIds(
    val soulIds: Set<String>,
    val collectionIds: Set<String>,
)

/**
 * Draft payload → composer state, dropping references that no longer resolve
 * (design D7). Sanitizing here rather than at publish means a deleted soul costs
 * the user a chip, not an opaque foreign-key error at the worst possible moment.
 */
fun PebbleDraftPayload.toDraft(known: KnownDraftIds): PebbleDraft {
    val defaults = PebbleDraft()
    return PebbleDraft(
        // Restored verbatim (design D6): for a quick capture the moment captured
        // IS the moment it happened, so publishing later must not move it.
        happenedAt = happenedAt ?: defaults.happenedAt,
        name = name ?: defaults.name,
        description = description ?: defaults.description,
        emotionId = emotionId,
        domainId = domainIds?.firstOrNull(),
        valence = Valence.orNull(positiveness, intensity),
        soulIds = soulIds.orEmpty().filter { it in known.soulIds },
        collectionId = collectionIds?.firstOrNull()?.takeIf { it in known.collectionIds },
        // Kept as-is; `can_use_glyph` is checked asynchronously by the composer,
        // which clears it if the glyph is no longer usable.
        glyphId = glyphId,
        visibility = visibility ?: defaults.visibility,
    )
}

/**
 * Relaxed counterpart to [PebbleDraft.isValid] (design D5): what "Save as draft"
 * allows. `isValid` must stay strict because [PebbleCreatePayload.from]
 * `require`s it and force-unwraps the mandatory fields.
 *
 * Takes `formSnap` so a photo-only draft counts as savable — the photo is real
 * input, and it lives outside [PebbleDraft].
 */
fun PebbleDraft.isSavableAsDraft(
    formSnap: FormSnap? = null,
    userId: String? = null,
): Boolean = !PebbleDraftPayload.from(this, formSnap, userId).isEmpty
