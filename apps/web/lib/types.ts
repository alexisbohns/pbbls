// Re-export static config types for convenience
export type { Emotion, Domain, CardType } from "./config"

export type ColorWorld =
  | "blush-quartz"
  | "stoic-rock"
  | "cave-pigment"
  | "dusk-stone"
  | "moss-pool"

export type PebbleCard = {
  species_id: string
  value: string
}

// One row from `public.snaps`. `storage_path` is the per-snap directory under
// `pebbles-media` (`{userId}/{snapId}`) — actual files live at
// `{storage_path}/original.jpg` and `{storage_path}/thumb.jpg`.
export type PebbleSnap = {
  id: string
  storage_path: string
  sort_order: number
}

export type Pebble = {
  id: string
  name: string
  description?: string
  happened_at: string
  intensity: 1 | 2 | 3
  positiveness: -1 | 0 | 1
  visibility: "private" | "public"
  emotion_id: string
  soul_ids: string[]
  domain_ids: string[]
  collection_ids: string[]
  mark_id?: string
  // Signed `original.jpg` URLs derived from `snaps` at load time. Display-only.
  instants: string[]
  snaps: PebbleSnap[]
  cards: PebbleCard[]
  // Server-rendered pebble. Populated by the compose-pebble / compose-pebble-update
  // edge functions; null for legacy rows or anonymous (LocalProvider) pebbles.
  render_svg: string | null
  render_version: string | null
  created_at: string
  updated_at: string
}

export type Soul = {
  id: string
  name: string
  glyph_id: string
  created_at: string
  updated_at: string
}

export type Collection = {
  id: string
  name: string
  mode?: "stack" | "pack" | "track"
  pebble_ids: string[]
  created_at: string
  updated_at: string
}

export type KarmaReason =
  | "pebble_created"
  | "pebble_enriched"
  | "pebble_deleted"
  | "grant"
  | "purchase"
  | "refund"

export type KarmaEvent = {
  id: string
  type: "credit" | "withdraw"
  delta: number
  reason: KarmaReason
  ref_id?: string
  created_at: string
}

// Read-only wallet summary projected from v_wallet_summary.
export type WalletSnapshot = {
  balance: number
  totalEarned: number
  totalSpent: number
}

// Read-only ripple summary projected from v_ripple. Mirrors the iOS
// `RippleSummary`: pebbles created in the trailing 28 days bucketed into
// 7 levels (0–6), plus whether the user created any pebble today.
export type RippleSummary = {
  level: number
  activeToday: boolean
  pebbles28d: number
}

// Read-only engagement stats projected from the get_profile_engagement RPC.
// `assiduity` is a 28-element window (index 0 = 27 days ago … index 27 = today).
export type ProfileEngagement = {
  daysPracticed: number
  assiduity: boolean[]
}

// ---------------------------------------------------------------------------
// Achievements (M48) — public catalog + owner-scoped unlock ledger
// ---------------------------------------------------------------------------

export type AchievementFamily =
  | "pebble_count"
  | "emotion_first"
  | "domain_first"
  | "first_collection"
  | "first_glyph"
  | "first_soul"
  | "glyph_count"
  | "glyph_sales"

// One row of the public `achievements` catalog. Copy is composed client-side
// from family-keyed i18n; the nullable EN/FR pairs are admin overrides that
// win when present (D7). `isActive: false` retires a badge: it stops
// evaluating and the grid hides it while locked, but earned unlocks keep
// rendering.
export type Achievement = {
  id: string
  slug: string
  family: AchievementFamily
  threshold: number | null
  emotionId: string | null // emotion_first only
  domainId: string | null // domain_first only
  sortOrder: number
  glyphId: string | null // system-owned visual; null → family fallback icon
  karmaReward: number
  isActive: boolean
  titleEn: string | null
  titleFr: string | null
  descriptionEn: string | null
  descriptionFr: string | null
}

// The subset a badge needs to draw itself: the family that picks its icon, and
// everything `useAchievementCopy` interpolates. `Achievement` satisfies this,
// and so does the leaner row `get_public_profile` projects for a visitor — which
// is why the badge components take this and not the full catalog row.
export type AchievementDisplay = Pick<
  Achievement,
  | "id"
  | "slug"
  | "family"
  | "threshold"
  | "emotionId"
  | "domainId"
  | "titleEn"
  | "titleFr"
  | "descriptionEn"
  | "descriptionFr"
>

// One row of the caller's `achievement_unlocks` ledger (permanent by design).
export type AchievementUnlock = {
  achievementId: string
  unlockedAt: string
}

export type MarkStroke = {
  d: string
  width: number
}

export type Mark = {
  id: string
  name?: string
  strokes: MarkStroke[]
  viewBox: string
  created_at: string
  updated_at: string
}

export type GlyphSubmissionStatus = "pending" | "approved" | "rejected"

// A community-market glyph: the glyph plus its listing price and the caller's
// relationship to it. Extends Mark (becomes Glyph when #488 lands).
export type MarketGlyph = Mark & {
  price: number
  owned: boolean // caller is entitled (bought)
  favourited: boolean // caller has favourited
}

export type GlyphSubmission = {
  id: string
  glyph_id: string
  status: GlyphSubmissionStatus
  price: number
  created_at: string
  review_note?: string | null // admin's reason when status === "rejected"
}

// ---------------------------------------------------------------------------
// Connections (M49) — mutual connections between users.
// ---------------------------------------------------------------------------

// The cross-user glyph projection returned by the connections RPCs: render
// geometry only (strokes + view_box), never a full Mark — the RPC deliberately
// omits id/created_at/ownership (design D5/D7). Same narrow shape as
// `DomainGlyph` in `useDomains.ts`.
export type PeerGlyph = {
  strokes: MarkStroke[]
  viewBox: string
}

// A peer's display projection — display name + avatar glyph geometry, never a
// profiles row (roadmap §5 item 8).
export type ConnectionPeer = {
  displayName: string
  glyph: PeerGlyph | null
}

export type Connection = {
  id: string
  connectedAt: string
  peer: ConnectionPeer
}

// ---------------------------------------------------------------------------
// Lab — product-transparency feed (announcements, changelog, backlog)
// ---------------------------------------------------------------------------

export type LogSpecies = "announcement" | "feature"
export type LogStatus = "backlog" | "planned" | "in_progress" | "shipped"
export type LogPlatform = "webapp" | "ios" | "android" | "all" | "project" | "infra"

export type Log = {
  id: string
  species: LogSpecies
  platform: LogPlatform
  status: LogStatus
  title_en: string
  title_fr: string | null
  summary_en: string
  summary_fr: string | null
  body_md_en: string | null
  body_md_fr: string | null
  cover_image_path: string | null
  external_url: string | null
  published: boolean
  published_at: string | null
  released_at: string | null
  created_at: string
  reaction_count: number
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

export type Account = {
  id: string
  email: string
  created_at: string
  /**
   * Linked auth providers for this account (e.g. `["email"]`, `["apple"]`,
   * `["google", "email"]`). Derived from the Supabase session identities.
   * Drives the Settings surface's Providers vs. Password sections.
   */
  providers?: string[]
}

export type Profile = {
  id: string
  user_id: string
  display_name: string
  /** FK to the user's chosen profile glyph (glyphs.id). Null when unset. */
  glyph_id: string | null
  /** Public URL identity (/u/<handle>). Null until claimed via set_handle. */
  handle: string | null
  /** Opt-in flag for the public profile page. Requires a handle (DB CHECK). */
  public_profile: boolean
  onboarding_completed: boolean
  color_world: ColorWorld
  terms_accepted_at: string | null
  privacy_accepted_at: string | null
  created_at: string
  updated_at: string
}

/**
 * The `get_public_profile` RPC projection, verbatim wire shape (M50). Null
 * from the RPC means "unknown or not public" — indistinguishable by design.
 */
export type PublicProfile = {
  display_name: string
  handle: string
  /** Avatar glyph geometry, or null when the owner has none set. */
  glyph: { strokes: MarkStroke[]; view_box: string } | null
  pebbles_count: number
  /** 0–6, same buckets as v_ripple. */
  ripple_level: number
  /** 0–7, same buckets as v_bounce. */
  bounce_level: number
  /** 28 booleans, index 0 = 27 days ago … index 27 = today (UTC). */
  assiduity: boolean[]
  days_practiced: number
  /** ISO date (YYYY-MM-DD). */
  member_since: string
  /**
   * The visitor's view of the owner's shelf: unlocked badges only, most
   * recently earned first, capped at six by the RPC (#688). Snake_case because
   * these come straight off the jsonb projection, unmapped.
   */
  achievements: PublicAchievement[]
  /** Total unlocked, untruncated — the array above is a slice of it. */
  achievements_count: number
}

/** One badge of `get_public_profile`'s shelf slice. */
export type PublicAchievement = {
  id: string
  slug: string
  family: AchievementFamily
  threshold: number | null
  emotion_id: string | null
  domain_id: string | null
  title_en: string | null
  title_fr: string | null
  description_en: string | null
  description_fr: string | null
  /** Admin-assigned badge visual; null on every seeded row today. */
  glyph: { strokes: MarkStroke[]; view_box: string } | null
  /** UTC date (YYYY-MM-DD) — coarsened, like `member_since`. */
  unlocked_at: string
}

export type RegisterInput = {
  email: string
  password: string
  terms_accepted: boolean
  privacy_accepted: boolean
}
export type LoginInput = { email: string; password: string }
// `handle` is deliberately excluded: claiming/releasing a handle goes through
// the `set_handle` RPC (normalization + stable error codes), never a direct
// column write. `public_profile` stays here — a single-column toggle is the
// sanctioned direct-update case, guarded by the DB CHECK.
export type UpdateProfileInput = Partial<
  Omit<Profile, "id" | "user_id" | "handle" | "created_at" | "updated_at">
>
