import type { Store } from "@/lib/data/data-provider"
import type { ConsentRow } from "@/lib/data/consent"
import { compareMomentsNewestFirst } from "@/lib/data/moment-row"
import type { Account, Collection, CompletedMoment, Moment, Pebble, Profile, RippleSummary } from "@/lib/types"
import { SANDBOX_MARKS, SANDBOX_SCENARIOS, SANDBOX_SOULS } from "./sandbox-pebbles"

// ---------------------------------------------------------------------------
// Fixture content for /sandbox/design (#986). Builds on the /sandbox/path seed
// so it stays network-free: fixture glyphs, local pictures, sandbox
// palettes primed by the page.
// ---------------------------------------------------------------------------

const HOUR = 3_600_000
const SIGNED_UP = "2025-11-02T09:00:00Z"

/**
 * The sandbox "mixed" pebbles, re-dated to the hours before `now` (newest
 * first) so the Path view opens on a filled current week.
 */
export function designPebbles(now: Date): Pebble[] {
  const mixed = SANDBOX_SCENARIOS.find((s) => s.key === "mixed")
  if (!mixed) throw new Error("[design-fixtures] the sandbox 'mixed' scenario is missing")
  return mixed.pebbles.map((pebble, i) => {
    const at = new Date(now.getTime() - (i + 1) * 5 * HOUR).toISOString()
    return { ...pebble, happened_at: at, created_at: at, updated_at: at }
  })
}

export function designCollections(pebbles: Pebble[]): Collection[] {
  const ids = pebbles.map((p) => p.id)
  return [
    { id: "design-col-stack", name: "Summer by the canal", mode: "stack", glyph_id: SANDBOX_MARKS[2].id, pebble_ids: ids.slice(0, 3), created_at: SIGNED_UP, updated_at: SIGNED_UP },
    { id: "design-col-pack", name: "Little wins", mode: "pack", glyph_id: null, pebble_ids: ids.slice(3, 5), created_at: SIGNED_UP, updated_at: SIGNED_UP },
    { id: "design-col-track", name: "Morning runs", mode: "track", glyph_id: null, pebble_ids: [], created_at: SIGNED_UP, updated_at: SIGNED_UP },
  ]
}

/** The moment a fixture pebble completes, for the screens already on `Moment` (#1050). */
export function designMomentFromPebble(p: Pebble): CompletedMoment {
  return {
    id: p.moment_id,
    name: p.name || null,
    description: p.description ?? null,
    happened_at: p.happened_at,
    snaps: p.snaps,
    instants: p.instants,
    cover: p.cover,
    created_at: p.created_at,
    updated_at: p.updated_at,
    pebble: {
      id: p.id,
      intensity: p.intensity,
      positiveness: p.positiveness,
      visibility: p.visibility,
      emotion_id: p.emotion_id,
      soul_ids: p.soul_ids,
      domain_ids: p.domain_ids,
      collection_ids: p.collection_ids,
      cards: p.cards,
      glyph: p.glyph,
      created_at: p.created_at,
      updated_at: p.updated_at,
    },
  }
}

/**
 * Two moments that are not pebbles yet (#1050), set between the first fixture
 * pebbles so the Path's opening week draws them as blank stones: one named,
 * set as type, and one untitled photo, on a card.
 */
export function designBareMoments(now: Date): Moment[] {
  const at = (hours: number) => new Date(now.getTime() - hours * HOUR).toISOString()
  const photo = "/sandbox/fountain.jpg"
  return [
    {
      id: "design-bare-named",
      name: "Bread from the corner bakery",
      description: null,
      happened_at: at(7),
      snaps: [],
      instants: [],
      cover: null,
      created_at: at(7),
      updated_at: at(7),
      pebble: null,
    },
    {
      id: "design-bare-photo",
      name: null,
      description: null,
      happened_at: at(12),
      snaps: [{ id: "design-bare-snap", storage_path: "fixture/design-bare-snap", sort_order: 0 }],
      instants: [photo],
      cover: { snapId: "design-bare-snap", url: photo, thumbUrl: photo },
      created_at: at(12),
      updated_at: at(12),
      pebble: null,
    },
  ]
}

export function designStore(now: Date): Store {
  const pebbles = designPebbles(now)
  return {
    // Newest first, as the provider loads them; the flat pebbles stay the
    // completed ones for the screens not yet on moments.
    moments: [...pebbles.map(designMomentFromPebble), ...designBareMoments(now)].sort(compareMomentsNewestFirst),
    pebbles,
    souls: SANDBOX_SOULS,
    collections: designCollections(pebbles),
    marks: SANDBOX_MARKS,
    entitledMarks: [],
    pebbles_count: pebbles.length,
    karma: 42,
    karma_log: [],
    bounce: 3,
    bounce_window: [],
  }
}

export const DESIGN_ACCOUNT: Account = {
  id: "design-user",
  email: "mia@example.com",
  created_at: SIGNED_UP,
  providers: ["email", "google"],
}

export const DESIGN_PROFILE: Profile = {
  id: "design-profile",
  user_id: DESIGN_ACCOUNT.id,
  display_name: "Mia",
  glyph_id: SANDBOX_MARKS[0].id,
  handle: "mia",
  public_profile: true,
  onboarding_completed: true,
  color_world: "blush-quartz",
  terms_accepted_at: SIGNED_UP,
  privacy_accepted_at: SIGNED_UP,
  created_at: SIGNED_UP,
  updated_at: SIGNED_UP,
}

export const DESIGN_RIPPLE: RippleSummary = { level: 3, activeToday: true, pebbles28d: 17 }

/** 28 days, most practised, a gap every third day. */
export const DESIGN_ASSIDUITY: boolean[] = Array.from({ length: 28 }, (_, i) => i % 3 !== 1)

export const DESIGN_CONSENT: ConsentRow = {
  id: "design-consent",
  kind: "health_data",
  document_version: "2026-09",
  source: "web_settings",
  granted_at: "2026-09-01T10:00:00Z",
  withdrawn_at: null,
  superseded_at: null,
}
