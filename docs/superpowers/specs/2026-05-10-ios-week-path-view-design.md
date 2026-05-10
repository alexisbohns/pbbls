# iOS Week Path View — Design Spec

**Issue:** [#388 — split the path in week views](https://github.com/alexisbohns/pbbls/issues/388)
**Date:** 2026-05-10
**Scope:** iOS only (`apps/ios`) plus one Supabase migration for the new RPC
**Size:** Large (cross-app: schema migration + new RPC + major iOS feature surface)

## Summary

Replace the iOS Path screen's continuous, multi-week vertical list with a week-paginated experience: a horizontal "weeks roll" of cairns at the top, a date-range header with chevrons, a per-week pebble list in the body, a full-width "New pebble" button, and a custom bottom navigation bar (glyph + bounce + karma) that replaces the system tab bar. `MainTabView` is removed; `PathView` becomes the post-auth root. `LabView` moves into Profile as a NavigationLink.

## Background and motivation

The current Path is a single scrollable list grouping pebbles by ISO week, with a per-week cairn animation cascading rows. As the corpus grows, navigation by scroll becomes coarse. The redesign treats the week as the primary unit of navigation, gives each cairn first-class presence in a horizontal strip, and creates room for richer per-row treatment (large/hero pebbles, attached photos).

The redesign also rationalises navigation: dropping the tab bar removes a navigation tier and lets the bottom of every screen show user identity (glyph) and stat affordances (bounce, karma).

## Out-of-scope deferrals

The following are intentionally out of this PR (call out in the implementation plan but do not ship):

- **Pebble shape-conforming background.** The Figma uses a thick outline trick to make the pebble background hug each glyph's silhouette. We keep the current rounded-square thumbnail in this PR. A future PR will tackle the proper shape-conforming render.
- **Real user glyph in bottom-left.** The user-glyph feature ("the user's chosen glyph") is unbuilt. We placeholder it with an SF Symbol (`person.crop.circle`) tinted in `pebblesAccent`.
- **Per-week lazy loading + functional index** on `date_trunc('week', happened_at)`. The V1 single-query RPC is fast enough for typical pebble counts. Migration path documented below.
- **Auto-jump focus to a freshly-created retro pebble's week.** Shipping value is borderline and the "where did I go?" risk is real. Ship as a follow-up if requested.
- **Snap URL signing failure placeholder.** Design has no failure mock. Failures log silently; the row renders without the photo.
- **Page-indicator dots under the cairn strip.** Not in design.
- **UI / snapshot tests.** Project has no UI test target. Pure logic gets Swift Testing tests in `PebblesTests/`.

## Locked decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | "Large" pebble = `intensity == 3` | Matches `Valence.*Large` in the existing model (the "Month event" tier). Per-row data, not per-position. |
| 2 | Week swap = tap-cairn + swipe-path + chevrons | All three converge on a single `focusedWeekStart` binding. |
| 3 | Cascade replays on every week swap | Time-based per-pebble stagger; **not** gated on cairn finish. |
| 4 | Photo size 64 × 64 | From Figma. Fits inside calculated row heights. |
| 5 | Photo source = first `snap` row by `(sort_order asc, created_at asc)` | Same snap that PebbleDetailSheet renders as the primary image. |
| 6 | Photo rotation parity | Index 0 (even) → −7°. Index 1 (odd) → +4°. Index is the row's position in the focused-week list. |
| 7 | Row heights | No photo → 60pt. Photo + 4° → 68pt. Photo − 7° → 71pt. Sized to fit the rotated 64pt bounding box. |
| 8 | Tap on photo opens detail | Same target as the rest of the row (single button). |
| 9 | Roll model | Union of: weeks-with-pebbles ∪ {current week} ∪ {next week}, sorted ascending by `weekStart`. Adjacent in roll = adjacent in this set, **not** in calendar time. |
| 10 | Year hint in date header | Show year suffix only when focused-week year ≠ today's year. |
| 11 | Sort within week | Past = oldest-first. Current and future = newest-first. Pivot: "week ends before today's ISO week start". |
| 12 | Date format in pebble row | `WEEKDAY · HH:MM` only (no day/month — week is already known from header). |
| 13 | Bottom of list "fade behind" New button | `.mask` with vertical gradient, opaque 0% → opaque 85% → transparent 100%. |
| 14 | Cairn animation in roll | Focused cairn plays one-shot on focus change. Non-focused cairns are static (paused at frame 1). |
| 15 | Drop `MainTabView`, `PathView` is post-auth root | Custom bottom bar inside PathView. Profile pushed via NavigationLink. |
| 16 | `LabView` becomes a NavigationLink at the top of `ProfileView` | New "Discover" section above "Stats". |
| 17 | Glyph stub | SF Symbol `person.crop.circle` tinted `pebblesAccent`. |
| 18 | Implementation strategy | TabView(`.page` style) for the path body; custom `ScrollView` + `LazyHStack` for the weeks roll. Single `@State focusedWeekStart` keeps both in sync. |
| 19 | Data layer | New `path_pebbles` RPC returning pebbles + intensity + first-snap path + emotion. Single round trip; client-side roll construction. |

## Architecture

### Component graph (runtime)

```
RootView
└─ PathView (post-auth root, owns NavigationStack and focusedWeekStart)
   ├─ WeekRollView         (entries, focused: Binding<Date>)
   ├─ WeekHeaderView       (entries, focused: Binding<Date>, today)
   ├─ TabView(.page) bound to focused
   │   └─ WeekPathView per entry
   │       ├─ List (gradient-masked at bottom)
   │       │   └─ PathPebbleRow per pebble
   │       └─ .task(id: cascadeKey) → reveal cascade
   └─ safeAreaInset(.bottom)
       ├─ NewPebbleButton
       └─ PathBottomBar (glyph + bounce + karma)
```

### File layout

```
apps/ios/Pebbles/Features/Path/
  PathView.swift                            # CHANGED — root container, owns weeks-roll state
  Components/
    WeekRollView.swift                      # NEW — horizontal cairn strip
    WeekRollCairnCell.swift                 # NEW — one cairn cell, plays-on-focus
    WeekHeaderView.swift                    # NEW — date range pill + chevrons
    WeekPathView.swift                      # NEW — per-week pebble list (one TabView page)
    NewPebbleButton.swift                   # NEW — full-width primary button
    PathBottomBar.swift                     # NEW — glyph + bounce + karma footer
    PathPebbleRow.swift                     # CHANGED — large variant + photo
    PathPebbleSnapThumb.swift               # NEW — tiny lazy-signed thumb image for the row
    # Existing siblings stay untouched: CairnAnimationViewModel, etc.
    # WeekSectionHeader.swift will be DELETED (only used by the old PathView; no other refs).
  Models/
    WeekRollEntry.swift                     # NEW — value type: weekStart + pebbles slice
    Pebble.swift                            # CHANGED — adds intensity + firstSnapPath
  Services/
    WeekRollBuilder.swift                   # NEW — pure func building entries from pebbles
    PathStatsService.swift                  # NEW — @Observable wrapper around v_karma_summary + v_bounce

apps/ios/Pebbles/Features/Main/
  MainTabView.swift                         # DELETED

apps/ios/Pebbles/Features/Profile/
  ProfileView.swift                         # CHANGED — adds Lab link + onboarding replay row

apps/ios/Pebbles/Features/Lab/
  LabView.swift                             # CHANGED — strip outer NavigationStack
                                            # (it's now a destination of Path's stack via Profile)

apps/ios/Pebbles/RootView.swift             # CHANGED — replace MainTabView() with PathView()

packages/supabase/supabase/migrations/
  <ts>_path_pebbles_rpc.sql                 # NEW — adds public.path_pebbles()
packages/supabase/types/database.ts         # REGENERATED via npm run db:types
```

## Data layer

### `Pebble` model changes

```swift
struct Pebble: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let happenedAt: Date
    let renderSvg: String?
    let emotion: EmotionRef?
    let intensity: Int                 // NEW — 1/2/3
    let firstSnapPath: String?         // NEW — storage_path of first snap, nil if none

    private enum CodingKeys: String, CodingKey {
        case id, name, intensity
        case happenedAt = "happened_at"
        case renderSvg = "render_svg"
        case emotion
        case firstSnapPath = "first_snap_path"
    }
}
```

### `path_pebbles` RPC (new)

```sql
-- packages/supabase/supabase/migrations/<ts>_path_pebbles_rpc.sql
create or replace function public.path_pebbles()
returns table (
  id uuid,
  name text,
  happened_at timestamptz,
  intensity smallint,
  render_svg text,
  emotion jsonb,            -- { id, slug, name } or null
  first_snap_path text
)
language sql
security invoker
stable
as $$
  select
    p.id,
    p.name,
    p.happened_at,
    p.intensity,
    p.render_svg,
    case
      when e.id is null then null
      else jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name)
    end as emotion,
    (
      select s.storage_path
      from public.snaps s
      where s.pebble_id = p.id
      order by s.sort_order asc nulls last, s.created_at asc
      limit 1
    ) as first_snap_path
  from public.pebbles p
  left join public.emotions e on e.id = p.emotion_id
  where p.user_id = auth.uid()
  order by p.happened_at desc;
$$;

grant execute on function public.path_pebbles() to authenticated;
```

The RPC runs `security invoker` and filters by `auth.uid()` — RLS-equivalent isolation without bypassing it. Single round trip; the client constructs the weeks roll from the result.

After migration: run `npm run db:types --workspace=packages/supabase` and commit the regenerated `packages/supabase/types/database.ts`.

### iOS query call (replaces existing `PathView.load`)

```swift
let result: [Pebble] = try await supabase.client
    .rpc("path_pebbles")
    .execute()
    .value
self.pebbles = result
```

### `WeekRollEntry` (new value type)

```swift
struct WeekRollEntry: Identifiable, Hashable {
    let weekStart: Date         // ISO Monday 00:00 in the calendar passed in
    let pebbles: [Pebble]       // already sorted per the past/current rule (can be empty)
    var id: Date { weekStart }
}
```

### `WeekRollBuilder.build(...)` — pure function

```swift
enum WeekRollBuilder {
    static func build(
        pebbles: [Pebble],
        calendar: Calendar,                  // Calendar(identifier: .iso8601)
        today: Date                          // Date()
    ) -> [WeekRollEntry]
}
```

Algorithm:
1. Compute `currentWeekStart` and `nextWeekStart` from `today` using `calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], ...)` then setting `.weekday = 2`.
2. Group `pebbles` by ISO week (reuse the existing `groupPebblesByISOWeek`).
3. Build the union: every key in the grouping ∪ `{currentWeekStart, nextWeekStart}`.
4. For each entry's pebbles, sort:
   - If `weekStart < currentWeekStart` (past): ascending by `happenedAt`.
   - Else (current or future): descending by `happenedAt`.
5. Return entries sorted ascending by `weekStart`.

Lookup helpers exposed from the builder:
- `index(of weekStart: Date, in entries: [WeekRollEntry]) -> Int?`
- `previous(of weekStart: Date, in entries: [WeekRollEntry]) -> WeekRollEntry?`
- `next(of weekStart: Date, in entries: [WeekRollEntry]) -> WeekRollEntry?`

### Photo URL signing — reuse `PebbleSnapRepository`

`PebbleSnapRepository.signedURLs(storagePrefix:)` already exists and returns both the original and the thumb signed URLs. We do **not** introduce a new resolver service. Instead, a small view component handles row-level lazy loading:

```swift
struct PathPebbleSnapThumb: View {
    let storagePath: String                       // value of pebble.firstSnapPath
    @Environment(SupabaseService.self) private var supabase
    @State private var url: URL?

    var body: some View {
        AsyncImage(url: url) { image in
            image.resizable().aspectRatio(contentMode: .fill)
        } placeholder: {
            Color.clear                            // no failure placeholder per spec
        }
        .task(id: storagePath) {
            do {
                let urls = try await PebbleSnapRepository(client: supabase.client)
                    .signedURLs(storagePrefix: storagePath)
                url = urls.thumb                   // 64pt row → use thumb, not original
            } catch {
                Logger(subsystem: "app.pbbls.ios", category: "path-row").error(
                    "snap sign failed: \(error.localizedDescription, privacy: .private)"
                )
            }
        }
    }
}
```

This pattern mirrors the existing `SnapImageView` (which signs the `original`); we use the `thumb` URL because the row renders at 64pt and the thumb is bandwidth-cheaper. No environment-injected service, no shared cache (the same snap won't appear in multiple visible rows). Bucket name (`pebbles-media`) and TTL (1 h) are inherited from `PebbleSnapRepository`.

### Bounce / karma fetch in PathView

PathView fires three independent tasks on appear:
1. `path_pebbles` → drives the body and roll.
2. `v_karma_summary` (single row) → drives the karma stat.
3. `v_bounce` (single row) → drives the bounce stat.

Reuse the same query shape that `ProfileView.loadStats()` already runs. Extract a small `@Observable PathStatsService` so PathView, the bottom bar, and ProfileView all read from one source. The existing `KarmaSummary` and `BounceSummary` types stay unchanged.

```swift
@Observable
final class PathStatsService {
    var karma: Int?
    var bounce: Int?
    private let supabase: SupabaseService
    init(supabase: SupabaseService) { self.supabase = supabase }

    func load() async {
        async let karmaResult: KarmaSummary = supabase.client
            .from("v_karma_summary").select("total_karma, pebbles_count")
            .single().execute().value
        async let bounceResult: BounceSummary = supabase.client
            .from("v_bounce").select("bounce_level, active_days")
            .single().execute().value
        self.karma = (try? await karmaResult)?.totalKarma
        self.bounce = (try? await bounceResult)?.bounceLevel
    }
}
```

Constructed in `RootView` (alongside `EmotionPaletteService`) and injected via `.environment(stats)`. Both PathView and ProfileView read it via `@Environment(PathStatsService.self) private var stats` and trigger `.task { await stats.load() }` on appear. ProfileView's existing `loadStats()` is replaced by the service call.

## Components

### `WeekRollView`

Horizontal `ScrollView(.horizontal, showsIndicators: false)` with a `LazyHStack(spacing: 0)` of `WeekRollCairnCell`s. Width per cell ~72pt; content padding leaves ~36pt on each side so the focused cell rests centered.

```swift
struct WeekRollView: View {
    let entries: [WeekRollEntry]
    @Binding var focusedWeekStart: Date
    let calendar: Calendar
}
```

Behavior:
- `.scrollTargetBehavior(.viewAligned)` and `.scrollPosition(id: $focusedWeekStart)`. Programmatic writes to `focusedWeekStart` animate the strip to center the focused cairn. User horizontal drags also update `focusedWeekStart` via the binding.
- Each cell receives `isFocused: entry.weekStart == focusedWeekStart` and the absolute distance to focus (computed from index position) for opacity.

Opacity by distance to focus:
| Distance | Opacity |
|---|---|
| 0 | 1.00 |
| ±1 | 0.50 |
| ±2 | 0.25 |
| ≥ ±3 | 0.0 |

### `WeekRollCairnCell`

```swift
struct WeekRollCairnCell: View {
    let entry: WeekRollEntry
    let isFocused: Bool
    let opacity: Double                 // computed by parent
    let calendar: Calendar
    let onTap: () -> Void
}
```

- Cairn rendered via `CairnAnimationViewModel(fileName: "pbbls-cairn")` at 56 × 56.
- Below: ISO week number, `Ysabeau-SemiBold 13pt`. Foreground: `pebblesAccent` when focused, `pebblesMutedForeground` otherwise.
- `.onChange(of: isFocused) { _, newValue in newValue ? cairn.play() : cairn.reset() }`. The Rive runtime is safe to re-trigger; the existing `CairnAnimationViewModel.onFinished` callback is unused here (we don't gate anything on it).
- Tap → `onTap()`.
- Apply `.opacity(opacity)` to the whole cell.

### `WeekHeaderView`

```swift
struct WeekHeaderView: View {
    let entries: [WeekRollEntry]
    @Binding var focusedWeekStart: Date
    let calendar: Calendar
    let today: Date
}
```

- Pill: `Capsule().stroke(headerStroke, lineWidth: 1)`, height 40pt. Stroke color: light = `pebblesMutedForeground`, dark = `pebblesForeground`.
- Inside: `HStack { chevronButton(.left) ; Spacer() ; rangeText ; Spacer() ; chevronButton(.right) }`.
- `rangeText` uses `Date.FormatStyle.dateTime.month(.wide).day()` × 2, locale-aware. Uppercased + tracked via `.textCase(.uppercase)` and `.tracking(2.0%)`. Year suffix appended only when `year(focusedWeekStart) != year(today)`.
- Chevrons: `Image(systemName: "chevron.compact.left" / "chevron.compact.right")`, tinted `pebblesAccent`. Disabled (no action, alpha 0.3) when there's no further entry in that direction.
- Tap chevron → write `focusedWeekStart` to `WeekRollBuilder.previous(...)` / `.next(...)`.

Pure helper for testing:
```swift
extension WeekHeaderView {
    static func formatRange(weekStart: Date, today: Date, calendar: Calendar, locale: Locale) -> String
}
```

### `WeekPathView`

```swift
struct WeekPathView: View {
    let entry: WeekRollEntry
    let onTap: (Pebble) -> Void
    let onDelete: (Pebble) -> Void
    @State private var revealedCount = 0
}
```

- `List` with a single section, no header.
- Each row: `PathPebbleRow(pebble:, positionIndex: index, onTap:, onDelete:)`. Row visibility gated on `index < revealedCount`. Transition: `.opacity.combined(with: .move(edge: .top))`.
- Bottom mask:
  ```swift
  .mask(
      LinearGradient(
          stops: [
              .init(color: .black, location: 0.0),
              .init(color: .black, location: 0.85),
              .init(color: .clear, location: 1.0),
          ],
          startPoint: .top, endPoint: .bottom
      )
  )
  ```
- Empty state (when `entry.pebbles.isEmpty`): centered `Text("No pebbles this week")` foreground `pebblesMutedForeground`. The "New pebble" button below remains the obvious next step.
- Cascade trigger:
  ```swift
  .task(id: cascadeKey) {
      revealedCount = 0
      for index in 0..<entry.pebbles.count {
          try? await Task.sleep(for: .milliseconds(80))
          withAnimation(.easeOut(duration: 0.25)) {
              revealedCount = index + 1
          }
      }
  }
  ```
  where `cascadeKey = "\(entry.weekStart.timeIntervalSince1970)-\(entry.pebbles.count)"`. Week swap or pebble-count change triggers a fresh cascade. View teardown (TabView swap) cancels the in-flight Task automatically.

### `PathPebbleRow` (changed)

```swift
struct PathPebbleRow: View {
    let pebble: Pebble
    let positionIndex: Int                  // for photo rotation parity
    let onTap: () -> Void
    let onDelete: () -> Void
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.colorScheme) private var colorScheme
}
```

Sizing (driven by data, not position):
| State | Thumbnail | Row height |
|---|---|---|
| Small/medium (intensity 1–2), no photo | 56pt | 60pt |
| Small/medium, photo (odd index, +4°) | 56pt | 68pt |
| Small/medium, photo (even index, −7°) | 56pt | 71pt |
| Large (intensity = 3), any photo state | 96pt | 100pt |

(Large rows match the design's "hero" treatment: bigger thumbnail, primary background, light glyph stroke. The 96pt thumbnail dominates the row vertically, so a 64pt rotated photo always fits — no per-photo height adjustment needed for large rows.)

Pure helpers exposed for testing:
```swift
extension PathPebbleRow {
    static func rotationAngle(forPositionIndex i: Int) -> Double {
        i.isMultiple(of: 2) ? -7 : 4
    }
    static func rowHeight(intensity: Int, hasPhoto: Bool, positionIndex: Int) -> CGFloat
}
```

Color rules:
- **Small/medium**: `thumbnailFill = palette.surface`; `glyphStrokeHex = palette.secondaryHex`; `nameColor = (colorScheme == .dark ? palette.light : palette.primary)`. (Identical to today.)
- **Large**: `thumbnailFill = palette.primary`; `glyphStrokeHex = palette.lightHex`; `nameColor = palette.light`. (Same in both schemes — the primary background carries the contrast.)

Date format (replaces today's `weekday + day + month + time`):
```swift
let weekday = pebble.happenedAt.formatted(.dateTime.weekday(.wide))
let time    = pebble.happenedAt.formatted(.dateTime.hour().minute())
return "\(weekday) · \(time)"
```

Photo rendering:
- If `pebble.firstSnapPath` is `nil`: no photo view.
- Else: wrap `PathPebbleSnapThumb(storagePath: pebble.firstSnapPath!)` in a 64 × 64 frame, with `.clipped()`, `.cornerRadius(8)`, white border 4pt, shadow `0 2 6 rgba(0,0,0,.18)`, then `.rotationEffect(.degrees(Self.rotationAngle(forPositionIndex: positionIndex)))`.
- Lazy URL signing happens inside `PathPebbleSnapThumb`. Failure → image stays empty; row already reserves the photo's space, so no layout jitter.

Long-press delete: keep the existing `.contextMenu { Button(role: .destructive, action: onDelete) }`. PathView still owns the confirmation dialog.

### `NewPebbleButton`

```swift
struct NewPebbleButton: View {
    let onTap: () -> Void
}
```
- Full-width `Capsule`, height ~52pt, horizontal padding 16pt.
- Background: light = `pebblesBackground`, dark = `pebblesForeground`.
- Label: `Text("New pebble")` (Localized), foreground `pebblesAccent`, `Ysabeau-SemiBold 17pt`.
- Tap → presents `CreatePebbleSheet` via PathView's existing sheet binding.

### `PathBottomBar`

```swift
struct PathBottomBar: View {
    let karma: Int?
    let bounce: Int?
    let onProfile: () -> Void
}
```
- `HStack(spacing: 0)` with three children: glyph button, `Spacer()`, bounce + karma cluster.
- Glyph: `Image(systemName: "person.crop.circle").font(.title2).foregroundStyle(pebblesAccent)`. Wrapped in `Button(action: onProfile)`. ~40pt tap target.
- Stat cluster: `HStack(spacing: 16) { stat("circle.hexagongrid", bounce, "bounce") ; stat("sparkle", karma, "karma") }`. SF Symbol names match the issue spec; implementer should verify against Figma (e.g. `.fill` variant) before merge. Each stat is also a `Button(action: onProfile)`.
- Stat layout per item: `HStack(spacing: 6) { Image(systemName:).foregroundStyle(pebblesAccent) ; VStack(alignment: .leading, spacing: 0) { Text("\(value)").foregroundStyle(numberColor) ; Text("bounce").font(.caption).foregroundStyle(pebblesMutedForeground) } }`.
- `numberColor`: light = `pebblesForeground`, dark = `pebblesAccent`.
- Loading state (value `nil`): show a placeholder dash `"—"`. No spinner — the path is the focus.

### `PathView` (revised)

State and dependencies:
```swift
@Environment(SupabaseService.self) private var supabase
@Environment(EmotionPaletteService.self) private var palettes
@Environment(PathStatsService.self) private var stats     // injected from RootView
@State private var pebbles: [Pebble] = []
@State private var entries: [WeekRollEntry] = []          // derived from pebbles + today
@State private var focusedWeekStart: Date                 // initialized to today's weekStart
@State private var navPath = NavigationPath()
@State private var isPresentingCreate = false
@State private var selectedPebbleId: UUID?
@State private var pendingDeletion: Pebble?
@State private var deleteError: String?
@State private var isLoading = true
@State private var loadError: String?

private var isoCalendar: Calendar { Calendar(identifier: .iso8601) }
private var today: Date { Date() }                        // recomputed each render; fine for our resolution
```

Layout:
```swift
NavigationStack(path: $navPath) {
    VStack(spacing: 16) {
        WeekRollView(entries: entries, focusedWeekStart: $focusedWeekStart, calendar: isoCalendar)
        WeekHeaderView(entries: entries, focusedWeekStart: $focusedWeekStart, calendar: isoCalendar, today: today)
        TabView(selection: $focusedWeekStart) {
            ForEach(entries) { entry in
                WeekPathView(entry: entry, onTap: openDetail, onDelete: queueDelete)
                    .tag(entry.weekStart)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
    }
    .safeAreaInset(edge: .bottom) {
        VStack(spacing: 12) {
            NewPebbleButton(onTap: { isPresentingCreate = true })
            PathBottomBar(karma: stats.karma, bounce: stats.bounce, onProfile: { navPath.append(PathRoute.profile) })
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
    .navigationDestination(for: PathRoute.self) { route in
        switch route { case .profile: ProfileView() }
    }
    .toolbar(.hidden, for: .navigationBar)
    .pebblesScreen()
}
.task { await load() }
.task { await stats.load() }
.sheet(isPresented: $isPresentingCreate) { CreatePebbleSheet(onCreated: handleCreated) }
.sheet(item: $selectedPebbleId) { id in PebbleDetailSheet(pebbleId: id, onPebbleUpdated: { Task { await load() } }) }
.confirmationDialog(...)        // unchanged delete confirmation
.alert("Couldn't delete", ...)  // unchanged
```

Onboarding replay no longer lives in PathView's toolbar; it moves to ProfileView.

## Data flow

### Launch
1. `RootView` resolves auth → mounts `PathView`.
2. `PathView.task { await load() }` calls `path_pebbles`. On success, `pebbles = result`; `entries = WeekRollBuilder.build(...)`; `focusedWeekStart = currentWeekStart`.
3. Parallel `.task { await stats.load() }` populates karma + bounce.
4. `WeekRollView` snaps to focused via `.scrollPosition(id:)`. The focused cairn plays its one-shot.
5. The active TabView page mounts `WeekPathView`, which fires its cascade `.task(id: cascadeKey)`.

### Week swap
| Trigger | Writes `focusedWeekStart` |
|---|---|
| Tap a cairn | `WeekRollCairnCell.onTap` |
| Swipe path body | `TabView` selection binding |
| Tap a chevron | Computed `previous` / `next` in `WeekRollBuilder` |

Both `WeekRollView` and `TabView` bind to the same `@State`. Roll snap, body slide, header label, and chevron-enabled state all derive from this single source.

### Mutation (create / edit / delete)
1. Sheet's callback fires after a successful mutation.
2. PathView reruns `await load()` → new `pebbles` → recomputed `entries`.
3. If `focusedWeekStart` is no longer in `entries` (a deleted-last-pebble case for a past, non-current/next week), fall back: pick the entry whose `weekStart` is closest to the prior focus; on tie, prefer the earlier one.
4. `cascadeKey` changes for the focused entry → cascade replays.

## Edge cases

- **Empty current week** — `entries` = `[currentWeek (empty), nextWeek (empty)]`. Roll shows 2 cairns at full opacity. Header range covers current week. Left chevron disabled. WeekPathView renders empty state.
- **Focused week somehow missing from `entries`** — fall back as described in the Mutation flow. Should not arise outside the post-deletion path.
- **Year jump** — `WeekHeaderView.formatRange(...)` appends ` · 1990` when years differ. Locale-aware month/day rendering handles French ordering automatically.
- **Retro pebble create** — `CreatePebbleSheet`'s `onCreated` callback already triggers `selectedPebbleId = newPebbleId` and reload. After reload, the new week appears in `entries`. The user remains on the original focus; they navigate to the new week manually. Auto-jump is deferred.
- **Rapid swipe across many weeks** — each new `WeekPathView` mount cancels the prior `.task`. New cairn `play()` resets the timeline; previously-focused cairn naturally winds down or is `reset()` on `isFocused` flip.
- **Long-press delete** — bubbles up to PathView's `confirmationDialog`. Unchanged from today.
- **Snap URL sign failure** — `PathPebbleSnapThumb` keeps its `url` `nil`, `AsyncImage` renders the empty `Color.clear` placeholder. Row height still reflects the photo-included value (`firstSnapPath` is authoritative — failure is transient and reload resolves it).
- **Loading state** — full-screen `ProgressView` until `path_pebbles` resolves the first time. Subsequent loads (after mutations) keep the existing UI on screen.
- **Load error** — `Text("Couldn't load your pebbles.").foregroundStyle(.secondary)` centered. Same as today.

## Navigation & MainTabView removal

### `RootView`
```swift
if canShowAuthedTabs {
    PathView()                           // was MainTabView()
        .fullScreenCover(...)            // existing onboarding gate stays
}
```

`PathStatsService` is also constructed and injected at the same level as `EmotionPaletteService`:
```swift
let stats = PathStatsService(supabase: supabase)
RootView()
    .environment(supabase)
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(stats)
```
(In `PebblesApp.swift` — wherever the existing services are injected.)

### `MainTabView`
Deleted. The tab-bar UIKit appearance code in its `init` goes with it. No replacement — there is no tab bar.

### `PathView` is the post-auth root
Owns the `NavigationStack` (already does today). `.toolbar(.hidden, for: .navigationBar)` removes the title bar — the redesign has no top nav. The "info.circle" replay-onboarding toolbar item is removed; replay moves to ProfileView.

### `ProfileView` changes
- New top section before "Stats":
  ```swift
  Section {
      NavigationLink {
          LabView()
      } label: {
          Label("Lab", systemImage: "lightbulb.max")
      }
      .listRowBackground(Color.pebblesListRow)
  } header: { Text("Discover") }
  ```
- "Replay onboarding" row added inside the existing "Legal" section, above the "Terms" row. Renders as `ProfileNavRow(title: "Replay onboarding", systemImage: "play.circle")`. Same pattern as the existing Terms / Privacy rows.
- Profile gains `@State private var isPresentingOnboarding = false` + `.fullScreenCover(isPresented: $isPresentingOnboarding) { OnboardingView(...) }`.

### `LabView` changes
Strip the outer `NavigationStack`:
```swift
// Before
var body: some View {
    NavigationStack { content.navigationTitle("Lab").pebblesScreen() }
}
// After
var body: some View {
    content
        .navigationTitle("Lab")
        .pebblesScreen()
}
```
LabView's internal `NavigationLink`s now route through Path's NavigationStack (via Profile), which is correct.

## Animations

- **Cascade**: 80ms stagger between rows. Per row: `withAnimation(.easeOut(duration: 0.25)) { revealedCount = index + 1 }`. Transition `.opacity.combined(with: .move(.top))`.
- **Cairn focus play**: handled by `RiveViewModel.play()` on `isFocused` flip. No SwiftUI animation needed.
- **Roll snap**: `.scrollPosition(id:)` animates by default when the bound id changes.
- **TabView page swap**: built-in horizontal slide.
- **Bottom mask gradient**: static (no animation).

## Accessibility

- Cairn cells: `accessibilityLabel("Week \(isoWeek), \(pebbleCount) pebbles")`. The Rive cairn itself is `accessibilityHidden(true)`.
- Chevron buttons: `accessibilityLabel("Previous week" / "Next week")`. Disabled state surfaces correctly (no announcement when unreachable).
- New pebble button: `accessibilityLabel("New pebble")` (default from the `Text`).
- Bottom bar: each tappable element gets a label ("Profile", "Bounce \(N)", "Karma \(N)").
- Dynamic Type: row text uses `.font(.custom(...))` today — keep current behavior. Photo size and row heights stay fixed (the design is layout-driven, not type-driven).

## Localization

New strings in `Localizable.xcstrings`, with FR translations:

| Source | French |
|---|---|
| `New pebble` | `Nouveau pebble` |
| `No pebbles this week` | `Aucun pebble cette semaine` |
| `Discover` | `Découvrir` |
| `Replay onboarding` | `Revoir l'introduction` |
| `Previous week` | `Semaine précédente` |
| `Next week` | `Semaine suivante` |

Brand terms `Pebbles`, `bounce`, `karma`, `Lab` stay English in both locales (`Text(verbatim:)` where literals are used outside Strings catalog auto-extraction).

Date formatting uses `Date.FormatStyle` with `Locale.current` — never `DateFormatter(locale:)`.

## Test plan

### Unit tests (Swift Testing in `PebblesTests/`)

1. **`WeekRollBuilderTests`** — covers entry composition and per-week sort:
   - Empty pebbles → `[currentWeek, nextWeek]`, both with empty `pebbles` arrays.
   - Single pebble in current week → `[currentWeek (1), nextWeek (0)]`.
   - Single pebble in 1990 → `[1990-week, currentWeek, nextWeek]` ascending by `weekStart`.
   - Pebbles in two non-adjacent weeks plus current → roll has both pebble-weeks plus current and next, no gap-filling.
   - Past-week pebbles sorted ascending; current and future descending; verify the pivot is "weekStart < currentWeekStart" (strict).
   - Boundary: pebble at this week's Monday 00:00 UTC bucketed correctly (current, not previous).
   - Boundary: pebble in late December landing in ISO week 1 of next year buckets to the next-year key.

2. **`PathPebbleRowGeometryTests`** — pure helper tests:
   - `rotationAngle(forPositionIndex:)`: 0 → −7, 1 → +4, 2 → −7, 3 → +4.
   - `rowHeight(intensity:hasPhoto:positionIndex:)`: full table per the spec.

3. **`WeekHeaderFormatTests`** — pure helper tests:
   - Same-year focus → label has no year suffix.
   - Cross-year focus → label appends ` · YYYY`.
   - FR locale → month names render in French; structure unchanged.

### Manual smoke (run before requesting review)

- Cold launch → splash → Path with current week focused, cascade visible.
- Swipe left/right several weeks; chevrons reflect bounds; date header label updates including year-jump suffix.
- Tap a cairn 3 weeks back; roll snaps and body slides.
- Create a pebble in the current week; appears at top of list; cascade re-runs.
- Create a retro pebble dated 2 years ago; new entry appears in roll left of current; navigate to it.
- Long-press a pebble → confirm delete → row removes.
- Tap glyph in bottom bar → Profile pushes; back chevron returns.
- Tap Lab in Profile → Lab pushes; back returns to Profile.
- Tap karma stat in bottom bar → Profile pushes (same target as glyph).
- Light + dark mode parity, including the New button and stat number colors.
- Empty current week (fresh signup): empty state visible, New button reachable.
- Pebble row with photo: rotation correct (even = −7°, odd = +4°); row height matches the spec.
- Pebble row marked `intensity = 3`: large thumbnail with primary bg + light stroke.

## Lint and build scope

Per CLAUDE.md task triage (Large task):
- `npm run lint` (full)
- `npm run build` (full — touches schema migration, generated types, iOS project)
- `npm run db:types --workspace=packages/supabase` after the migration; commit `packages/supabase/types/database.ts`.
- `npm run generate --workspace=@pbbls/ios` (xcodegen) after deleting `MainTabView.swift` and adding new files.
- iOS Xcode build run manually for the smoke checklist.

## Arkaik map updates

Per the `arkaik` skill — apply to `docs/arkaik/bundle.json`:
- **Delete node** `MainTabView`.
- **Update edge** `LabView`: parent `MainTabView (tab)` → `ProfileView (push)`.
- **Add nodes** under `PathView`: `WeekRollView`, `WeekHeaderView`, `WeekPathView`, `PathBottomBar`, `NewPebbleButton`.
- **Add edge** `PathBottomBar (PathView) → ProfileView (push)`.

The implementation plan should include a dedicated step for this so it ships in the same PR.

## Performance notes (for future)

When the path query exceeds ~100ms or ~200KB on a typical user:
1. Add a functional index: `create index pebbles_user_iso_week_idx on public.pebbles (user_id, date_trunc('week', happened_at at time zone 'UTC') desc);`
2. Add a view: `v_path_weeks (week_start, pebble_count)` for the roll.
3. Add an RPC: `path_pebbles_for_week(p_week_start)` for the body.
4. Switch iOS to two parallel queries: one for the roll (cheap), one per `WeekPathView` (`.task` lazy-fetch). Adjacent TabView pages will warm-fetch ±1.

This is **not** in this PR.

## Risks

- **TabView page-style binding sync**: rapid programmatic writes (chevron taps + roll snaps) may interleave oddly with the user's drag gestures. Mitigation: trust SwiftUI's TabView selection binding semantics; verify in smoke run.
- **`.scrollPosition(id:)` on horizontal LazyHStack**: iOS 17 surface, generally stable but has known quirks with cell appearance ordering at edges. Mitigation: cap the `.contentMargins` and watch for pre-render issues during smoke.
- **Rive cairn replays during rapid focus changes**: should be safe per the existing `CairnAnimationViewModel` tests, but mass replays haven't been exercised. Mitigation: smoke run with rapid swiping.
- **Deleting `MainTabView` mid-PR**: if any forgotten reference exists, build breaks. Mitigation: grep the entire `apps/ios` tree for `MainTabView` before deletion.

## Branch / PR

- Branch: `feat/388-week-path-view`
- PR title: `feat(ios): split path into week views (#388)`
- PR body starts with `Resolves #388`
- Labels (inherit from issue 388): `core`, `feat`, `ios`, `ui`
- Milestone: confirm with the user when opening the PR.
