import type { PebbleDraftPayload } from "@/lib/data/data-provider"
import type { PebbleSnap } from "@/lib/types"

/**
 * Projection between the composer's flat state and the draft payload (M47).
 *
 * The payload is keyed like the `compose-pebble` wire payload, not like the
 * composer — note `glyph_id` where the composer says `markId`. That is what
 * makes publishing a pass-through and keeps the three surfaces in agreement.
 * See docs/superpowers/specs/2026-07-29-drafts-and-autosave-design.md (D2).
 *
 * Pure by design: no React, no provider, so both the server-draft path and the
 * localStorage autosave share one canonical projection.
 */

export type ComposerState = {
  name: string
  description: string
  happenedAt: string
  intensity: 1 | 2 | 3
  valence: -1 | 0 | 1
  emotionId: string
  domainIds: string[]
  soulIds: string[]
  markId: string | undefined
  collectionIds: string[]
  visibility: "private" | "public"
  pendingSnap: PebbleSnap | undefined
}

/** The composer's own initial values — also the hydration fallback. */
export function composerDefaults(): ComposerState {
  return {
    name: "",
    description: "",
    happenedAt: new Date().toISOString(),
    intensity: 2,
    valence: 0,
    emotionId: "",
    domainIds: [],
    soulIds: [],
    markId: undefined,
    collectionIds: [],
    visibility: "private",
    pendingSnap: undefined,
  }
}

/**
 * Composer state → draft payload. Unset keys are omitted, never nulled.
 *
 * `intensity`, `positiveness`, `visibility` and `happened_at` are always
 * written because on web they always hold a real selection (the valence grid
 * has no empty state). The native surfaces omit intensity/positiveness together
 * when their `valence` is nil; hydration below tolerates either.
 */
export function buildDraftPayload(state: ComposerState): PebbleDraftPayload {
  const name = state.name.trim()
  const description = state.description.trim()
  return {
    ...(name && { name }),
    ...(description && { description }),
    happened_at: state.happenedAt,
    intensity: state.intensity,
    positiveness: state.valence,
    visibility: state.visibility,
    ...(state.emotionId && { emotion_id: state.emotionId }),
    ...(state.domainIds.length > 0 && { domain_ids: state.domainIds }),
    ...(state.soulIds.length > 0 && { soul_ids: state.soulIds }),
    ...(state.collectionIds.length > 0 && { collection_ids: state.collectionIds }),
    ...(state.markId && { glyph_id: state.markId }),
    ...(state.pendingSnap && {
      snaps: [
        {
          id: state.pendingSnap.id,
          storage_path: state.pendingSnap.storage_path,
          sort_order: 0,
        },
      ],
    }),
  }
}

/**
 * Autosave variant: identical minus `snaps` (D3). A local snapshot is crash
 * insurance for the open composer, and the in-form preview is a `blob:` URL
 * that cannot survive a reload anyway — persisting the descriptor would only
 * buy a signed-URL fetch on every restore.
 */
export function buildAutosavePayload(state: ComposerState): PebbleDraftPayload {
  return buildDraftPayload({ ...state, pendingSnap: undefined })
}

/**
 * True when the payload carries nothing a user would recognise as their input.
 * `happened_at` / `intensity` / `positiveness` / `visibility` are always present
 * and always defaulted, so they never count as content — otherwise every
 * freshly-opened composer would look like an unsaved draft.
 */
export function isDraftEmpty(payload: PebbleDraftPayload): boolean {
  return (
    !payload.name &&
    !payload.description &&
    !payload.emotion_id &&
    !payload.glyph_id &&
    !payload.domain_ids?.length &&
    !payload.soul_ids?.length &&
    !payload.collection_ids?.length &&
    !payload.snaps?.length
  )
}

/**
 * Ids the composer can verify. Souls, collections and glyphs are user-owned and
 * deletable, so a draft can outlive them; emotions and domains are seeded
 * reference rows and are passed through untouched (a missing emotion already
 * renders as the composer's "no emotion yet" state).
 */
export type KnownIds = {
  soulIds: ReadonlySet<string>
  collectionIds: ReadonlySet<string>
  markIds: ReadonlySet<string>
}

/**
 * Draft payload → composer state, dropping references that no longer resolve
 * (D7). Sanitizing here rather than at publish means a deleted soul costs the
 * user a chip, not an opaque foreign-key error at the worst possible moment.
 */
export function applyDraftPayload(
  payload: PebbleDraftPayload,
  known: KnownIds,
): ComposerState {
  const defaults = composerDefaults()
  const markId =
    payload.glyph_id && known.markIds.has(payload.glyph_id) ? payload.glyph_id : undefined
  const snap = payload.snaps?.[0]
  return {
    name: payload.name ?? defaults.name,
    description: payload.description ?? defaults.description,
    // `happened_at` is restored verbatim (D6): for a quick capture, the moment
    // captured IS the moment it happened, so publishing later must not move it.
    happenedAt: payload.happened_at ?? defaults.happenedAt,
    intensity: payload.intensity ?? defaults.intensity,
    valence: payload.positiveness ?? defaults.valence,
    emotionId: payload.emotion_id ?? defaults.emotionId,
    domainIds: payload.domain_ids ?? defaults.domainIds,
    soulIds: (payload.soul_ids ?? []).filter((id) => known.soulIds.has(id)),
    collectionIds: (payload.collection_ids ?? []).filter((id) =>
      known.collectionIds.has(id),
    ),
    markId,
    visibility: payload.visibility ?? defaults.visibility,
    pendingSnap: snap
      ? { id: snap.id, storage_path: snap.storage_path, sort_order: 0 }
      : undefined,
  }
}
