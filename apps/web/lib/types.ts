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

// A pebble's first snap as the Spine shows it. The thumb has the original's
// ratio at a fraction of its bytes, so the card probes it for the ratio before
// the photo loads. Either URL is null when it failed to sign: the card still
// holds its place and says the photo is unavailable.
export type PebbleCover = {
  snapId: string
  url: string | null
  thumbUrl: string | null
}

/**
 * Privacy grade (M51): `secret` = owner-only, `private` = owner + mutual
 * connections, `public` = any signed-in user, plus share-by-link via /p/[id].
 */
export type Visibility = "secret" | "private" | "public"

export type Intensity = 1 | 2 | 3
export type Positiveness = -1 | 0 | 1

/**
 * The caller's pebble on a moment, as `v_moments_full.pebble` nests it. It
 * exists only once the moment is completed (#1046 D2), so a bare moment has no
 * intensity, emotion or domain to pretend about.
 */
export type MomentPebble = {
  /** pebbles.id: the share URL (/p/[id]), collection_pebbles, karma ref_id. */
  id: string
  intensity: Intensity
  positiveness: Positiveness
  visibility: Visibility
  emotion_id: string
  soul_ids: string[]
  domain_ids: string[]
  collection_ids: string[]
  cards: PebbleCard[]
  /** Derived: the oldest collection's glyph, else the first domain's default (D5). */
  glyph: PebbleGlyph | null
  created_at: string
  updated_at: string
}

/**
 * What happened (#1046 D1): one row of `v_moments_full`. Its id is the route
 * key everywhere (/pebble/[id] is a MOMENT id).
 */
export type Moment = {
  id: string
  /** Null = untitled (a photo-only moment); never coerced to "". */
  name: string | null
  description: string | null
  happened_at: string
  /** sort_order ascending. */
  snaps: PebbleSnap[]
  /** Signed `original.jpg` URLs, display only. */
  instants: string[]
  /** The first snap, original and thumb signed. */
  cover: PebbleCover | null
  created_at: string
  updated_at: string
  pebble: MomentPebble | null
}

export type CompletedMoment = Moment & { pebble: MomentPebble }

/**
 * What a stone is drawn from (#1050): the pebble's look, never a whole entity,
 * so success, the read page, lists and the sandbox share one input. Where a
 * stone takes `StoneLook | null`, null is a bare moment: the blank stone (R4).
 */
export type StoneLook = Pick<MomentPebble, "intensity" | "positiveness" | "emotion_id" | "glyph"> & {
  /** With `created_at`, whether the engine draws the retro fossil (recorded 28+ days later). */
  happened_at: string
  created_at?: string
  /** For the stone's accessible label; null = untitled. */
  name: string | null
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
  /** The collection's glyph (R11): its pebbles carry it (D5). Null for none. */
  glyph_id: string | null
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

// This union lags the catalog: the server also serves first_moment,
// first_pebble, first_five (#1254), first_stone and set_complete (#1305). The
// web icon migration (#1313) widens it together with the two `FAMILY_ICONS`
// records it owns; until then a value outside it arrives at runtime, so every
// consumer keyed by family must fall back (an icon, a slug, `t.has`).
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

// ---------------------------------------------------------------------------
// The achievements page (#1308): `get_achievements(p_tz)` decoded (spec
// 2026-10-29 §3.3, §4). The server decides every number, order, level, family
// and flag; these types only carry them. An absent section is null and its
// card is hidden. Inside a section the shared tolerance rules hold: an
// unknown enum is kept as null (drawn as a ghost or a fallback, so seats never
// shift), a missing id, day or instant is null, and a missing count or flag
// takes iOS's default (0, false). Instants are parsed tolerantly into `Date`;
// days stay `YYYY-MM-DD`, local to the zone the page was read in.
// ---------------------------------------------------------------------------

/** The five cards whose sheets `mark_achievements_seen` records (D10). */
export type AchievementCard = "explorer" | "emotions" | "domains" | "pebbles" | "year"

/** The three sets whose completion is offered, then pulled (D9). */
export type CeremonyStack = "emotions" | "domains" | "six_firsts"

/** The six firsts (D5): three intensities, three valences. */
export type StoneKind = "small" | "medium" | "large" | "lowlight" | "neutral" | "highlight"

/** The Explorer wheel's five rings, centre out (D6). */
export type ExplorerRing = "first_five" | "six_firsts" | "twenty_five" | "domains" | "emotions"

/** The first five's tasks, as the Home checklist names them. */
export type FirstFiveTask = "first-moment" | "first-pebble" | "first-collection" | "first-soul" | "first-glyph"

/** What a first-five task made. */
export type FirstFiveSubjectKind = "moment" | "pebble" | "collection" | "soul" | "glyph"

/** What the domains' coverage means (D11), never a count. */
export type DomainCoverage = "none" | "few" | "third" | "half" | "most" | "all"

/** A month as the year disc draws it (D12). An unknown state reads as quiet. */
export type MonthState = "upcoming" | "quiet" | "kept"

/** What the current month's next level needs (D12). */
export type YearNeeds = "moments" | "pebbles"

/** The crossing pebble's snapshot (D15). One without a moment id draws but does not open. */
export type CrossingPebble = {
  momentId: string | null
  /** Absent for an unnamed moment. */
  name: string | null
  happenedAt: Date | null
  emotionId: string | null
  /** Out of range reads as the Path's neutral medium (2, 0). */
  intensity: Intensity
  positiveness: Positiveness
}

/** A set made whole (D9). `ready` is the server's clock, so no client compares one. */
export type AchievementCompletion = {
  stack: CeremonyStack
  completedAt: Date | null
  offeredFrom: Date | null
  ready: boolean
  /** Null until pulled. */
  pulledAt: Date | null
  /** The stone that made the set whole. */
  pebble: CrossingPebble | null
}

export type FirstFiveSubject = {
  /** Null for a kind this build does not know: the row stays. */
  kind: FirstFiveSubjectKind | null
  id: string | null
  name: string | null
}

export type FirstFiveItem = {
  /** Null for a slug this build does not know: its seat draws as a ghost. */
  task: FirstFiveTask | null
  done: boolean
  /** The day its subject was made. */
  on: string | null
  subject: FirstFiveSubject | null
  isNew: boolean
}

export type SixFirst = {
  /** Null for a kind this build does not know: its seat draws as a ghost. */
  kind: StoneKind | null
  done: boolean
  on: string | null
  pebble: CrossingPebble | null
  isNew: boolean
}

export type TwentyFive = {
  /** Capped at 25 by the server. */
  count: number
  done: boolean
  on: string | null
  pebble: CrossingPebble | null
  isNew: boolean
}

export type AchievementsExplorer = {
  /** The wheel's centre: the crossing pebble of `pebble-count-1`. */
  firstPebble: CrossingPebble | null
  /** Home order, as served. */
  firstFive: FirstFiveItem[]
  /** Catalog order, as served. */
  sixFirsts: SixFirst[]
  twentyFive: TwentyFive | null
  /** A set of flags: a ring this build does not know has nothing to draw, so it is left out. */
  ringsDone: ExplorerRing[]
  completion: AchievementCompletion | null
}

export type EmotionSeat = {
  /** The seat's index on the wheel; null when the server sent none. */
  seat: number | null
  emotionId: string | null
  /** The family's slug as served; one with no palette draws in ink. */
  family: string | null
  met: boolean
  on: string | null
  pebble: CrossingPebble | null
  isNew: boolean
}

/** One family of the Emotions sheet: its met seats by the day met, then the rest (seat indices). */
export type EmotionFamilyGroup = {
  family: string | null
  met: number[]
  notYet: number[]
}

export type AchievementsEmotions = {
  count: number
  of: number
  /** Wheel order, then slug, as served. */
  seats: EmotionSeat[]
  /** The sheet's order: families on the wheel, as served. */
  families: EmotionFamilyGroup[]
  /** The card's "since": the first emotion's day. */
  since: string | null
  completion: AchievementCompletion | null
}

export type DomainEntry = {
  domainId: string | null
  on: string | null
  pebble: CrossingPebble | null
  isNew: boolean
}

export type AchievementsDomains = {
  count: number
  of: number
  /** Null for a tier this build does not know: its sentence is hidden. */
  coverage: DomainCoverage | null
  /** Newest first, as served. */
  entered: DomainEntry[]
  notYet: string[]
  completion: AchievementCompletion | null
}

export type LadderLevel = { threshold: number; on: string | null; pebble: CrossingPebble | null; isNew: boolean }

export type LadderNext = {
  threshold: number
  left: number
  /** Toward `threshold` from the level before it, clamped to [0, 1]. */
  progress: number
}

/** One counting stack (D3): pebbles, glyphs carved or glyph sales. */
export type CountingLadder = {
  count: number
  levels: LadderLevel[]
  next: LadderNext | null
  /** The steps the figure draws. */
  ladder: number[]
  /** Pebbles only. */
  thenEvery: number | null
  /** The card's "since": the day of its first level. */
  since: string | null
}

/** Which of a month's counters reached their target: the done style is the server's call (D12). */
export type MonthDone = { moments: boolean; pebbles: boolean; weeks: boolean }

export type YearMonth = {
  month: number
  state: MonthState
  /** The month's colour (D12) as served; null without a pebble. */
  family: string | null
  momentDays: number
  pebbleDays: number
  fullWeeks: number
  weeksFilled: number
  /** 0 at the centre, 1 on the inner disc, 2 on the rim (clamped). */
  level: 0 | 1 | 2
  /** Toward the next level, clamped to [0, 1]; 0 on the rim. */
  progress: number
  done: MonthDone
}

export type YearNextStep = {
  month: number
  /** Null for a need this build does not know: its sentence is hidden. */
  needs: YearNeeds | null
  daysLeft: number
  /** Absent reads as true. */
  reachable: boolean
}

export type AchievementYear = {
  year: number
  current: boolean
  /** The days a level needs, as served. */
  daysTarget: number | null
  /** Exactly twelve, January to December: a year without them is dropped. */
  months: YearMonth[]
  /** The table's months, newest first, as served (values outside 1…12 left out). */
  listed: number[]
  nextStep: YearNextStep | null
}

/** One badge on the owner's Profile shelf (D18); titles and icons resolve from the catalog by slug. */
export type ShelfItem = {
  slug: string
  family: string
  /** An explicit null for the families that count nothing. */
  threshold: number | null
  unlockedOn: string | null
}

export type AchievementsPage = {
  /** The account's first day, local to the reader (D2). */
  since: string | null
  daysKept: number | null
  explorer: AchievementsExplorer | null
  emotions: AchievementsEmotions | null
  domains: AchievementsDomains | null
  pebbles: CountingLadder | null
  glyphsCarved: CountingLadder | null
  glyphSales: CountingLadder | null
  /** Newest first, as served. */
  years: AchievementYear[]
  /** When each card was last looked at (D10). */
  seen: Partial<Record<AchievementCard, Date>>
  /** Newest first, as served. */
  shelf: ShelfItem[]
  shelfCount: number | null
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

// A glyph as `v_moments_full.pebble.glyph` embeds it: geometry only, no timestamps.
export type PebbleGlyph = Pick<Mark, "id" | "name" | "strokes" | "viewBox">

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
  /** Unchecked: may be a family the union does not list yet (see `AchievementFamily`). */
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

/**
 * The `get_shared_pebble` projection (M51) — what an anonymous `/p/[id]`
 * visitor sees of a `public`-grade pebble. Snake_case because it comes off the
 * jsonb projection; only `name` and `glyph` are mapped (`sharedPebbleFromRpc`).
 * Deliberately owner-less: the RPC never returns `user_id`.
 */
export type SharedPebble = {
  id: string
  name: string
  description: string | null
  /** Whole-second UTC ISO timestamp (e.g. "2026-08-01T12:00:00Z"). */
  happened_at: string
  intensity: number
  positiveness: number
  /**
   * The resolved glyph (the sharer's collection's, else the domain's), drawn
   * client-side since the server stopped rendering (#1046). Null for none.
   */
  glyph: PebbleGlyph | null
  emotion: {
    id: string
    slug: string
    name: string
    color: string
    /** Category palette tints; null when the emotion has no category linked. */
    primary_color: string | null
    secondary_color: string | null
  }
}

/**
 * One pebble a connection shares with the viewer (M51) — the RLS-visible
 * subset of their pebbles ('private' + 'public'), core row only: enrichments
 * stay owner-only and must not be rendered.
 */
export type SharedConnectionPebble = {
  id: string
  name: string
  /** ISO timestamptz as returned by PostgREST. */
  happened_at: string
  visibility: Visibility
  intensity: number
  positiveness: number
  emotion: { id: string; slug: string; name: string; color: string }
  /** The resolved glyph, drawn client-side (#1046). Null for none. */
  glyph: PebbleGlyph | null
}

export type RegisterInput = {
  email: string
  password: string
  terms_accepted: boolean
  privacy_accepted: boolean
  /** Art. 9 explicit consent for emotional/health-adjacent data. */
  health_data_consent: boolean
  /** The 16+ age attestation. An attestation, never a birthdate. */
  age_attested: boolean
}
export type LoginInput = { email: string; password: string }
// `handle` is deliberately excluded: claiming/releasing a handle goes through
// the `set_handle` RPC (normalization + stable error codes), never a direct
// column write. `public_profile` stays here — a single-column toggle is the
// sanctioned direct-update case, guarded by the DB CHECK.
export type UpdateProfileInput = Partial<
  Omit<Profile, "id" | "user_id" | "handle" | "created_at" | "updated_at">
>
