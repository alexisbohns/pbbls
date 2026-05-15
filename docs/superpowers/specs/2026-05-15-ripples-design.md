# Ripples — iOS Bottom-Bar Badge

**Issue:** [#442 — [Feat] Introduce Ripples in iOS](https://github.com/anthropics-style-todo/pbbls/issues/442) (labels: `core`, `feat`, `ios`, `ui`)
**Date:** 2026-05-15
**Status:** Design approved; awaiting user review of this spec before plan writing.

## Summary

Introduce **Ripples**, a new 0–6 engagement signal surfaced as a dynamic ring-and-digit badge in the iOS Path bottom bar. Replaces the visual display of Bounce on Path (Bounce-the-data stays untouched). Ripples uses a different counting rule from Bounce: number of pebbles created (by `created_at`) in the trailing 28 days, not distinct active days, so it is a deliberate parallel signal rather than a rename.

## Goals (V1)

1. New DB view `public.v_ripple` returning `(user_id, ripple_level, pebbles_28d, active_today)`.
2. New `RippleBadge` SwiftUI view, 44×44, six bezier strokes + center digit, fully reactive to `(level, activeToday)`.
3. Three new theme-aware color tokens in `Assets.xcassets`: `RippleDefault`, `RippleActive`, `RippleInactive`.
4. `RippleSummary` decodable model + `ripple` field on `PathStatsService`, loaded in parallel with karma/bounce.
5. `PathBottomBar` updated: bounce stat replaced by `RippleBadge`; karma cluster and badge become two separate `Button`s, both routing to `ProfileView` for V1.
6. Call `stats.refresh()` after pebble create / update / delete in `PathView` so the badge (and karma) update immediately. Incidentally fixes a dormant karma-staleness bug.

## Non-goals (deferred, separate issues)

- **Perlin / turbulence "dissolve" effect** on inactive strokes — out of V1 per Q1.
- **Profile screen** — `BounceExplainerSheet`, Profile's "Bounce" stat row, and `BounceSummary` are untouched per Q2.
- **Ripples-specific explainer sheet** — splitting the tap target in Q6 leaves the door open without committing now.
- **28-day calendar grid** view — when it lands it gets its own purpose-built DB shape, not a column on `v_ripple`.
- **Any change to `v_bounce`, the `bounces` snapshot table, or admin analytics distribution.**

## Architecture overview

```
┌─────────────────────────────────────────┐
│ DB (Postgres / Supabase)                │
│   public.v_ripple ── SELECT only        │
│     ripple_level smallint  (0–6)        │
│     pebbles_28d   integer               │
│     active_today  boolean               │
└─────────────────┬───────────────────────┘
                  │ PostgREST .from("v_ripple")
┌─────────────────▼───────────────────────┐
│ PathStatsService (@Observable)          │
│   var karma : Int?                      │
│   var bounce: Int?         (unchanged)  │
│   var ripple: RippleSummary?     (NEW)  │
│   load() / refresh()                    │
└─────────────────┬───────────────────────┘
                  │ @Environment
┌─────────────────▼───────────────────────┐
│ PathBottomBar                           │
│   [avatar] … [karma cluster] [badge]    │
│                                ▲        │
│                                │        │
│                  RippleBadge(level,     │
│                              activeToday)│
└─────────────────────────────────────────┘
```

## Data layer

### Migration: `packages/supabase/supabase/migrations/<timestamp>_v_ripple.sql`

```sql
create view public.v_ripple as
select
  u.id as user_id,
  coalesce(stats.pebbles_28d, 0) as pebbles_28d,
  coalesce(stats.active_today, false) as active_today,
  case
    when coalesce(stats.pebbles_28d, 0) = 0   then 0
    when stats.pebbles_28d between  1 and  4  then 1
    when stats.pebbles_28d between  5 and  8  then 2
    when stats.pebbles_28d between  9 and 12  then 3
    when stats.pebbles_28d between 13 and 16  then 4
    when stats.pebbles_28d between 17 and 20  then 5
    else 6
  end::smallint as ripple_level
from auth.users u
left join lateral (
  select
    count(*) as pebbles_28d,
    bool_or(pb.created_at::date = current_date) as active_today
  from public.pebbles pb
  where pb.user_id = u.id
    and pb.created_at >= now() - interval '28 days'
) stats on true;
```

Notes:

- Counts by `created_at`, not `happened_at` — explicit per the issue. This is the key behavioural difference from `v_bounce`.
- `active_today` uses server-side `current_date`. Single lateral scan returns both metrics.
- Permissions and RLS inherit from the existing `pebbles` table policies, exactly like `v_bounce`.

### Pipeline (mandatory per AGENTS.md)

```bash
npm run db:reset --workspace=packages/supabase
npm run db:types --workspace=packages/supabase
git add packages/supabase/types/database.ts
```

### Swift model: `apps/ios/Pebbles/Features/Path/Models/RippleSummary.swift`

```swift
struct RippleSummary: Decodable {
    let rippleLevel: Int
    let pebbles28d: Int
    let activeToday: Bool

    enum CodingKeys: String, CodingKey {
        case rippleLevel = "ripple_level"
        case pebbles28d  = "pebbles_28d"
        case activeToday = "active_today"
    }
}
```

### `PathStatsService` changes

- Add `var ripple: RippleSummary?` alongside `karma` and `bounce`.
- In `performLoad()`, fire a third parallel query:
  ```swift
  async let rippleResult: RippleSummary = supabase.client
      .from("v_ripple")
      .select("ripple_level, pebbles_28d, active_today")
      .single().execute().value
  ```
  Await with its own `do/catch` + `logger.error("ripple fetch failed: …")` — matches the existing per-query failure-isolation pattern.
- `load()` / `refresh()` semantics unchanged.

## UI layer

### Color tokens

Three new color sets in `apps/ios/Pebbles/Resources/Assets.xcassets/`:

| Color set | Light | Dark |
|---|---|---|
| `RippleDefault.colorset` | `#F0E4E4` | `#2E2024` |
| `RippleActive.colorset` | `#C07A7A` | `#C07A7A` |
| `RippleInactive.colorset` | `#E0D0D2` | `#7A5E64` |

Exposed via the existing `Color` extension pattern:

```swift
extension Color {
    static let rippleDefault  = Color("RippleDefault")
    static let rippleActive   = Color("RippleActive")
    static let rippleInactive = Color("RippleInactive")
}
```

The center digit reuses existing tokens — no new color sets needed for the digit:

| | active | inactive |
|---|---|---|
| Light | `pebblesForeground` | `pebblesMutedForeground` |
| Dark | `pebblesSurface` | `pebblesMutedForeground` |

### `RippleBadge` — `apps/ios/Pebbles/Features/Path/Components/RippleBadge.swift`

Public API:

```swift
struct RippleBadge: View {
    let level: Int          // 0–6, clamped defensively
    let activeToday: Bool
}
```

Pure stroke-color resolver — encodes the issue's truth table verbatim, easy to unit-test:

```swift
private func strokeColor(for strokeId: Int, level: Int, activeToday: Bool) -> Color {
    if strokeId > level { return .rippleDefault }
    return activeToday ? .rippleActive : .rippleInactive
}
```

`level == 0` returns `.rippleDefault` for all six strokes (matches the spec table).

Body composition (sketch):

```swift
ZStack {
    RippleStroke6().stroke(strokeColor(for: 6, level: level, activeToday: activeToday),
                           style: .init(lineWidth: 2, lineCap: .round))
        .opacity(0.33)
    RippleStroke5().stroke(…).opacity(0.33)
    RippleStroke4().stroke(…).opacity(0.33)
    RippleStroke3().stroke(…).opacity(0.66)
    RippleStroke2().stroke(…).opacity(0.66)
    RippleStroke1().stroke(…).opacity(1.0)

    Text("\(min(max(level, 0), 6))")
        .font(.system(size: 12, weight: .bold, design: .rounded))
        .foregroundStyle(digitColor)
}
.frame(width: 44, height: 44)
.accessibilityElement(children: .ignore)
.accessibilityLabel(activeToday
    ? LocalizedStringResource("ripple_a11y_active", defaultValue: "Ripple level \(level), active today")
    : LocalizedStringResource("ripple_a11y_inactive", defaultValue: "Ripple level \(level), inactive today"))
```

`digitColor` resolved inline from `@Environment(\.colorScheme)` × `activeToday` per the table above.

Six `Shape` structs — `RippleStroke1` … `RippleStroke6` — each implementing `func path(in rect: CGRect) -> Path` by hand-porting the SVG `d` coordinates from issue #442. Coordinates are authored against the 44×44 viewBox so they apply directly when `rect.size == 44×44`; otherwise scale by `rect.width / 44` and `rect.height / 44`.

Each SVG `M x,y` becomes `path.move(to: CGPoint(x: x, y: y))`; each `C x1,y1 x2,y2 x,y` becomes `path.addCurve(to: …, control1: …, control2: …)`. ~3 cubics per stroke, ~6 lines of Swift each.

`#Preview` should include the full 14-cell `(level 0–6) × (active/inactive)` matrix in both color schemes.

### `PathBottomBar` integration

Props:

- `karma: Int?` (kept)
- ~~`bounce: Int?`~~ (removed)
- `ripple: RippleSummary?` (added)
- `onProfile: () -> Void` (kept)

Layout:

```swift
HStack(spacing: 0) {
    Button(action: onProfile) { /* avatar — unchanged */ }
        .accessibilityLabel("Profile")
    Spacer(minLength: 0)
    Button(action: onProfile) {
        stat(systemImage: "sparkle", value: karma, label: "karma")  // unchanged
    }
    .buttonStyle(.plain)
    .accessibilityLabel("Karma \(karma.map(String.init) ?? "—")")
    Button(action: onProfile) {
        RippleBadge(level: ripple?.rippleLevel ?? 0,
                    activeToday: ripple?.activeToday ?? false)
    }
    .buttonStyle(.plain)
    .padding(.leading, 16)
}
.padding(.horizontal, 16)
```

While `ripple == nil` (initial load), render `RippleBadge(level: 0, activeToday: false)` — six default-color rings with "0" — same visual as a no-pebble state, no flicker, no skeleton.

Update the file's doc comment (currently references the bounce `circle.hexagongrid` icon) to describe the new layout.

### `PathView` refresh hooks

Add `stats.refresh()` to three call sites in `apps/ios/Pebbles/Features/Path/PathView.swift`:

1. `CreatePebbleSheet`'s `onCreated` closure (currently runs `await load()`).
2. `PebbleDetailSheet`'s `onPebbleUpdated` closure (currently runs `await load()`).
3. `delete(_:)` after the successful RPC + `await load()`.

Run in parallel where natural:

```swift
async let pebbleReload: Void = load()
async let statsReload: Void  = stats.refresh()
_ = await (pebbleReload, statsReload)
```

PR description notes this incidentally fixes the dormant karma-staleness bug.

## Localization

User-facing strings added to `apps/ios/Pebbles/Resources/Localizable.xcstrings`:

- `ripple_a11y_active` — en: `"Ripple level %lld, active today"`; fr: `"Niveau d'Ondes %lld, actif aujourd'hui"` (final fr wording TBD by review — copy reviewer to confirm).
- `ripple_a11y_inactive` — en: `"Ripple level %lld, inactive today"`; fr: `"Niveau d'Ondes %lld, inactif aujourd'hui"`.

Both `en` and `fr` columns must be populated before PR open, with no `New` / `Stale` rows remaining (per `apps/ios/CLAUDE.md`).

## Testing

Per `apps/ios/CLAUDE.md`: Swift Testing only, no XCTest, no UI tests for now.

Unit tests (`apps/ios/PebblesTests/RippleBadgeTests.swift` — fall back to documenting test absence if no tests target exists yet; plan should grep first):

- `strokeColor(for:level:activeToday:)` — full 14-row truth table from the issue, 7 levels × 2 active states.
- `RippleSummary` JSON decoding — `ripple_level`, `pebbles_28d`, `active_today` keys.

Manual SQL verification (captured in PR notes):

- `select * from public.v_ripple where user_id = '<test uid>'` after inserting pebbles at known `created_at` boundaries — confirm each level threshold and the `active_today` midnight boundary.

Visual verification:

- Xcode `#Preview` 14-cell grid in both color schemes.
- Launch app, create / delete pebbles, watch the badge respond.

## Verification commands

Per task-size triage (medium task — multi-file, single feature, schema touched):

```bash
# Regen types after the new migration
npm run db:reset  --workspace=packages/supabase
npm run db:types  --workspace=packages/supabase

# Workspace-scoped lint
npm run lint --workspace=apps/ios

# iOS build
( cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles build )
```

Full `npm run build` from the repo root if any shared types in `packages/supabase` consumers fail to compile after `db:types`.

## Arkaik map

No new screen, route, data model, or endpoint visible at the product-graph level — `RippleBadge` is a UI component swap inside an existing screen. Skip the `arkaik` skill per CLAUDE.md task-size triage.

## Risks / things to watch

- **`active_today` midnight rollover.** Uses server-side `current_date`. If the user keeps the app open across midnight, the badge will only flip back to inactive after the next `stats.refresh()` — acceptable for V1 since the app is unlikely to stay open overnight without user interaction.
- **`#C07A7A` reads similarly in light and dark** for the active color — by design per the issue spec, but worth eyeballing in dark mode against `pebblesPathBackground`.
- **SVG `d` transcription accuracy.** Six strokes by hand is the obvious source of off-by-one bezier control points. The 14-cell preview grid is the verification harness — diff the preview against the issue screenshot before requesting review.

## Open items for plan-writing stage

- Confirm exact file location of the existing `Color` extension (where `pebblesAccent`, `pebblesForeground` live) before editing.
- Confirm whether `apps/ios/PebblesTests/` target exists or needs to be added.
- Final French translations for the two new accessibility strings.
