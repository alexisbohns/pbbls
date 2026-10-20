# iOS converges on the four-tab IA — `TabView`, per-tab stacks (Stack C)

**Date:** 2026-10-20
**Issue:** [#903](https://github.com/alexisbohns/pbbls/issues/903), which the maintainer decided as **converge**. It unblocks #446 (Ripples explainer) and #444 (Ripple dissolve), since both wire into `PathBottomBar`.
**Surface:** `apps/ios` only. No schema, no RPC, no wire format.
**Reference:** `docs/superpowers/specs/2026-09-20-android-nav3-design.md` (§2, D1–D4, §3) and the decision-log entry "2026-09-21 — Android gets a four-tab bottom bar".
**Depends on:** stack **A1 only**, because the `Color.pebbles.*` M3 roles must exist. C does **not** depend on stack B. It uses B1's string rider if that has landed (§10), and does not wait for it.

## Goal

The authed iOS root becomes a system `TabView` with four tabs: **Path, People, Collections, You**. These are the names and the shape Android shipped in #852. Each tab owns its own `NavigationStack` and its own path state. Both survive tab switches and scene restoration. Souls and Collections are promoted out of the Profile hub into tabs of their own, and `PathBottomBar` is deleted. After this stack, a new surface has one answer to "where does it live" on both platforms.

## Non-goals

- **The card stack.** `PebbleDetailSheet` stays a `.sheet(item:)` here. The overlay that replaces it later is only *located* by this spec (§7).
- **Ripples explainer (#446) and dissolve (#444).** This stack gives them a home and stops there.
- **Settings as a push.** Android pushes Settings. iOS keeps `SettingsSheet` as a sheet from the You tab's gear button. That is a presentation difference, not an IA difference.
- **Deep links into tabs.** The only URL entry point stays the invite link (`RootView.onOpenURL`).
- **iPad / sidebar adaptivity.** iPhone-only. **iOS 18 `Tab` builder, `.sidebarAdaptable`, `#available`**: excluded by the iOS 17 floor.
- **Swift 6 strict concurrency, schema work, Android changes.**

## 1. Today

`RootView` gates on `canShowAuthedTabs` and renders `PathView` directly. `PathView` owns the app's only authed `NavigationStack(path: $navPath)` with one route, `PathRoute.profile`. Everything else is reached from `ProfileView`. That happens through **closure-based** `NavigationLink { Destination() }` in `ProfileShortcutsRow` (Souls, Glyphs, Circle), `ProfileCollectionsCard` (header → `CollectionsListView`, tiles → `CollectionDetailView`), `ProfileAchievementsCard` and `ProfileLabCard`. The Path chrome is a `safeAreaInset(.bottom)` holding `NewPebbleButton` (full width, 52pt, tap → `CaptureFlowView` cover) and `PathBottomBar` below it (profile glyph, karma stat and `RippleBadge`, **all three routed to `onProfile`**).

**On the long-press:** the brief for this stack asked to keep "tap = record flow, long-press = all-at-once form". **No long-press exists on iOS today**: `NewPebbleButton` has a single `Button(action:)`, and the only `onLongPressGesture` in the app is in `StoneLabView`. Android's FAB lost its long-press too when the moments split retired the all-at-once form (`PathScreen.kt`, "A tap opens Capture (#1048)"). So "exactly as today" means: **tap opens Capture, nothing on long-press.** This spec does not add a gesture back.

## 2. Tab and route table

| Tab | Root view (today's file) | Pushed destinations (today's file) | Presented from here |
|---|---|---|---|
| **Path** | `PathView` (`Features/Path/PathView.swift`), minus its `NavigationStack` | none in this stack (`PathRoute.profile` deleted) | Capture cover, `PebbleDetailSheet`, delete dialog |
| **People** | `SoulsListView` (`Features/Profile/Lists/SoulsListView.swift`) | `.soul(id)` → `SoulDetailView` (`Features/Profile/Views/SoulDetailView.swift`); `.connections` → `ConnectionsListView` (`Features/Connections/ConnectionsListView.swift`) | `CreateSoulSheet`; `EditSoulSheet`, `PebbleDetailSheet` from detail; `InviteSheet` from Connections |
| **Collections** | `CollectionsListView` (`Features/Profile/Lists/CollectionsListView.swift`) | `.collection(id)` → `CollectionDetailView` (`Features/Profile/Views/CollectionDetailView.swift`) | `CreateCollectionSheet`; `EditCollectionSheet`, `PebbleDetailSheet` from detail |
| **You** | `ProfileView` (`Features/Profile/ProfileView.swift`) | `.glyphs` → `GlyphsListView` (`Features/Glyph/Views/GlyphsListView.swift`); `.achievements` → `AchievementsView` (`Features/Profile/AchievementsView.swift`); `.lab` → `LabView` (`Features/Lab/LabView.swift`, whose own links to `AnnouncementDetailView` / `LogListView` stay closure-based) | `SettingsSheet` (gear), Glyph carve cover and detail drawer |

People reaches Connections from a toolbar button on the souls list (`person.2.badge.plus`, label "Circle"/"Cercle", the hub tile's icon and copy). Invite stays a sheet on `ConnectionsListView`.

**Tab items.** SF Symbols, filled variants chosen by the system when selected:

| Tab | Symbol | EN | FR |
|---|---|---|---|
| Path | `square.stack` | Path | Chemin |
| People | `person.2` | People | Proches |
| Collections | `circle.hexagongrid` | Collections | Collections |
| You | `person.crop.circle` | You | Toi |

FR matches Android's `tab_*` strings. "Path" and "Collections" already exist in `Localizable.xcstrings` and are reused. "People" and "You" are new keys, added with both `en` and `fr`. `square.stack` echoes Android's `ic_stack`, and `circle.hexagongrid` is the symbol the app already uses for pebbles.

### D1 — Tab roots are the existing list views, not new screens

`SoulsListView` and `CollectionsListView` become roots unchanged apart from their entry links (§3). They already carry `.pebblesToolbarTitle` and a `+` primary action, which is exactly the root chrome a tab wants.

Considered and rejected:
- **New "People hub" screens** that combine souls and connections. That redesigns a surface under cover of a navigation change.
- **Keeping the hub cards as duplicate entry points.** Android removed them (nav3 §2). Two routes into the same place on one platform and one on the other is the drift #903 exists to end.

## 3. Navigation state model

New folder `Pebbles/Features/Tabs/`:

```swift
enum AppTab: String, Codable, CaseIterable, Hashable { case path, people, collections, you }

enum PathRoute: Codable, Hashable {}                       // reserved; stack exists for reselect + future pushes
enum PeopleRoute: Codable, Hashable { case soul(UUID), connections }
enum CollectionsRoute: Codable, Hashable { case collection(UUID) }
enum YouRoute: Codable, Hashable { case glyphs, achievements, lab }

@Observable @MainActor
final class TabNavigation {
    var selected: AppTab = .path
    var path: [PathRoute] = []
    var people: [PeopleRoute] = []
    var collections: [CollectionsRoute] = []
    var you: [YouRoute] = []
    /// Bumped when a tab is reselected at its root; roots observe it to scroll to top.
    private(set) var scrollToTop: [AppTab: Int] = [:]

    func select(_ tab: AppTab)          // reselect semantics, §4
    func popToRoot(_ tab: AppTab)
    var snapshot: Snapshot { get }      // Codable mirror of the five fields above
    func restore(_ snapshot: Snapshot)
    func reset()                        // sign-out
}
```

- **Typed arrays, not `NavigationPath`.** Each stack has one route type, so `NavigationStack(path: $nav.people)` with `.navigationDestination(for: PeopleRoute.self)` is enough. A plain `[Route]` is `Codable` without `NavigationPath.CodableRepresentation`'s opaque blob, and it can be asserted on directly in tests.
- **Routes carry ids, not models.** `SoulWithGlyph` and `Collection` are `Decodable`-only and change under edits. Two thin resolver views do the lookup. `SoulRoute(id:)` reads `ReferenceDataService.souls` and passes the match to `SoulDetailView(initial:onChanged:)`. `CollectionRoute(id:)` fetches the one `collections` row by id, using the column list `CollectionDetailView.load()` already uses, then shows `CollectionDetailView(collection:onChanged:)`. That fetch is a single-table, single-row select, so it needs no RPC (AGENTS.md). If the id no longer resolves (deleted on another device), the resolver logs and pops itself.
- **Where it lives.** `TabNavigation` is created in **`RootView`'s authed branch** as `@State` and injected into `PebblesTabView` with `.environment(nav)`. Any descendant can then call `nav.select(.you)` (the karma cluster does, §5), and `RootView` itself can read `nav.selected` for `holdsLoader` and call `nav.reset()` on sign-out (§6). It is not created in `PebblesApp`, because it is scoped to the authed tree.
- **Restoration.** `RootView` holds `@SceneStorage("tabs.navigation") private var storedTabs: Data?`, next to the navigator it restores. It restores once in `.task` (decode → `nav.restore`, and a decode failure logs and keeps defaults). It writes on `.onChange(of: nav.snapshot)`. `@SceneStorage` survives a system kill and is discarded on a user swipe-kill. That is the iOS-native expectation and needs no extra policy.
- **Conversion.** The closure links that cross a tab root become value links: `NavigationLink(value: PeopleRoute.soul(soul.id))` in `SoulsListView`, `NavigationLink(value: CollectionsRoute.collection(c.id))` in `CollectionsListView`, and `YouRoute` links in the You hub cards. Links below that depth (Lab internals, achievement rows) stay closure-based, so they are not restored. Restoration lands on their parent.

### D2 — One `@Observable` navigator, not four `@State` paths in four views

Considered and rejected:
- **Per-view `@State` paths.** Reselect and cross-tab jumps (karma → You) would each need a binding threaded through `PebblesTabView`.
- **One shared `NavigationPath`.** Destinations are tab-specific, so one heterogeneous path is the wrong model.

This mirrors Android's `NavigationState` + `Navigator` (nav3 §3) in one iOS object.

## 4. Back and reselect semantics

- **Back** is per stack: the nav-bar back button and the edge swipe, as provided by `NavigationStack`. Nothing crosses tabs. Android's D4 "exit through home" solves the system back button, which iOS does not have. Switching tabs never alters another tab's stack.
- **Reselect.** `TabView(selection:)` is bound through `Binding(get: { nav.selected }, set: { nav.select($0) })`. `select(_:)` behaves like this:
  - If the tab is not current, it becomes current.
  - If it is current and its stack is non-empty, the stack is popped to root (`removeAll()`), which animates as a standard pop.
  - If it is current and already at root, `scrollToTop[tab] += 1`.

  This is UIKit's long-standing `UITabBarController` behaviour: the first tap pops, the second scrolls.
- **Scroll to top.**
  - The list roots wrap their `ScrollView` in `ScrollViewReader` and scroll to a top anchor on change.
  - The You hub does the same.
  - **Path reselect at root** focuses the current week (the same `WeekRollBuilder.weekStart(for: today)` lookup `load()` uses) and scrolls that week's list to its top. "Top of Path" means *now*.

### D3 — Reselect pops, then scrolls (two taps), rather than both at once

This matches Android's reselect-pops (nav3 D4) and adds the iOS second-tap scroll users already expect from system apps.

Considered and rejected: **pop and scroll in one tap.** That fights the pop animation and leaves no way to scroll a root without leaving it.

## 5. Where New pebble, karma and Ripples live

Everything Path-specific stays inside `PathView`. The tab bar is the only shared chrome.

- **New pebble** becomes a floating button, `NewPebbleFab` (`Features/Path/Components/`, replacing `NewPebbleButton`): a 56pt rounded-square button, trailing.
  - Fill: `Color.pebbles.primaryContainer`.
  - Glyph: `plus`, coloured `onPrimaryContainer`.
  - VoiceOver label "New pebble".
  - **Tap → `isPresentingCapture = true`**, unchanged. No long-press (§1).
- **Karma and Ripples** move to a compact `PathStatusCluster`, leading, on the **same row** as the FAB. It shows the sparkle + number + "karma" caption, then `RippleBadge`, and keeps today's two independent `Button`s (#442's split).
  - Karma tap → `nav.select(.you)`, which replaces today's push to Profile.
  - Ripple tap → also `nav.select(.you)` until #446 lands. That issue then swaps this one action to present `RippleExplainerSheet`, and #444 restyles the badge it already renders here.
- **Layout.** The row is a `safeAreaInset(edge: .bottom)` on the Path content with a clear background, 16pt gutters and 8pt above the tab bar. The week list therefore scrolls *under* the row while its last pebble can still scroll clear of it.

### D4 — Karma and Ripples stay on Path, beside the FAB

The cluster costs no height of its own: the row exists for the FAB anyway. Today's two stacked rows cost about 112pt. The new row plus the system tab bar cost about the same, so the timeline loses no space.

Considered and rejected:
- **Above the week roll, or as nav-bar toolbar items.** Android tried a full-width row above the roll and reverted it on device (decision log 2026-09-21). A visible nav bar is the same cost by another name.
- **Dropping them, as Android did.** That is a declared temporary gap on Android. On iOS it would orphan #446's and #444's Path entry point for no space gain.
- **A centre "+" tab.** A non-tab tab item, rejected for the same reasons as nav3 D3.

## 6. What `RootView` composes

The gate is unchanged: `canShowAuthedTabs` still decides authed vs `WelcomeView`/`AuthView`, so the `TabView` renders **only** with a session. The authed branch becomes:

```
ZStack
├ PebblesTabView(onPathFirstLoad: { pathFeedLoaded = true })   // TabView, four NavigationStacks
│   .fullScreenCover(isPresented: $isPresentingOnboarding) { OnboardingView … }   // moved off PathView, same trigger
├ HandcraftedLogoView hold-over   if holdsLoader
└ [future] card-stack overlay host      // §7
.sheet(item: $acceptingInvite) { InviteAcceptSheet }   // unchanged, above the tabs
```

- **The loader hold-over** becomes `holdsLoader = !pathFeedLoaded && nav.selected == .path`. A scene restored onto People would otherwise boil forever. Path's `.task` (and so `onFirstLoad`) only runs once Path is shown, and `TabView` defers a tab's `.task` until first selection. `RootView` owns both `nav` and its `@SceneStorage`, so it restores before the authed branch first renders and reads `nav.selected` directly.
- `PathStatsService.load()` keeps running from Path's `.task`. `ProfileView` already calls `stats.load()` itself, so a launch onto You still has stats.
- **Sign-out reset contract.** `RootView`'s signed-out transition (the existing `pathFeedLoaded = false` re-arm) calls `nav.reset()` and clears **every authed `@SceneStorage` key**:
  - `tabs.navigation` (this stack);
  - stack E's `heldCard` (`momentId` + `face` + `printingFor`).

  The next account must inherit nothing from the previous one: no soul ids, no held card. **Any later authed `@SceneStorage` key registers here**, in this one place, in the PR that adds it.
- **Bar styling**, applied once on the `TabView`:
  - `.toolbarBackground(Color.pebbles.surfaceContainer, for: .tabBar)` and `.toolbarBackground(.visible, for: .tabBar)`.
  - `.tint(Color.pebbles.primary)` for the selected item.
  - The unselected item colour (`onSurfaceVariant`) and the hairline (`outlineVariant`) go through `UITabBarAppearance`, next to the existing appearance-proxy setup in `PebblesApp`. iOS 17 SwiftUI exposes no unselected-tint modifier.

## 7. Sheet and cover inventory

| Presenter (file) | Presentation | Becomes |
|---|---|---|
| `RootView`: `OnboardingView` | `fullScreenCover`, was on `PathView` | Same cover, attached to `PebblesTabView`; same `hasSeenOnboarding` trigger |
| `RootView`: `InviteAcceptSheet` | `.sheet(item:)` | Unchanged, above the `TabView`, so it presents whatever tab is showing |
| `PathView`: `CaptureFlowView` | `fullScreenCover` | Unchanged; opened by the FAB |
| `PathView` / `SoulDetailView` / `CollectionDetailView`: `PebbleDetailSheet` | `.sheet(item: UUID)` | Unchanged presentation; refresh fixed (D5) |
| `PathView`, `SoulDetailView`, `CollectionDetailView`: `.momentDeleteDialog` | `confirmationDialog` | Unchanged; also signals D5 |
| `ProfileView`: `SettingsSheet` (+ nested glyph picker, legal doc, Stone Lab cover) | sheet | Unchanged, You tab |
| `SoulsListView`: `CreateSoulSheet`; `SoulDetailView`: `EditSoulSheet` | sheet | Unchanged, People tab |
| `CollectionsListView`: `CreateCollectionSheet`; `CollectionDetailView`: `EditCollectionSheet` | sheet | Unchanged, Collections tab |
| `ProfileCollectionsCard`: `CreateCollectionSheet` | sheet | **Deleted with the card** (C2); creation lives on the Collections tab |
| `ConnectionsListView`: `InviteSheet` | sheet | Unchanged, under People |
| `GlyphsListView`: `GlyphCarveSheet` cover, `GlyphDetailDrawer` sheet | cover / sheet | Unchanged, under You |
| `KarmaOverlayWindowController` | pass-through `UIWindow` | Unaffected; already floats above everything |

**Every presentation is triggered from the tab that is showing.** No state is set from another tab, so no `.sheet` is ever asked to present from a non-selected tab (§10).

**The card-stack overlay (later stack).** It will be hosted in `RootView`, in the `ZStack` *above* `PebblesTabView` (the `[future]` slot in §6), and not inside any tab. That way one overlay serves Path, SoulDetail and CollectionDetail. While it is up, the tab bar sits under its scrim (dimmed). `PebblesTabView` gets `.accessibilityHidden(true)` so VoiceOver cannot reach the bar beneath. Callers will set an `@Observable` presented-moment value instead of their local `selectedMomentId`. This stack deliberately leaves the three `.sheet(item:)` call sites shaped alike to make that swap mechanical.

### D5 — Path refreshes when moments change on another tab

Path sat underneath Profile in one stack before, and it was already stale after an edit made from a soul's detail. Side-by-side tabs make that visible. It ships in **C1**, because C1 is the part that puts the tabs side by side. A small `@Observable MomentsRevision` (one `Int`, injected from `PebblesApp`) has these owners:
- **Bumps:**
  - `PebbleDetailSheet`, on save, delete and completion;
  - the `.momentDeleteDialog` presenters;
  - `CaptureFlowView.onMomentChanged`.
- **Reader:** `PathView` reloads on `.onChange(of: revision.value)` when it did not cause the bump. Once stack D lands, `PathModel` is the reader and reloads on the same signal.
- **Handover:** when E5 deletes `PebbleDetailSheet`, stack E's `HeldCardModel` takes over the sheet's bumps.

People and Collections detail views already reload through their own callbacks.

Considered and rejected:
- **Reload Path on every `.onAppear`.** That refetches on every tab switch.
- **NotificationCenter.** It is untyped and outside the app's `@Observable` pattern.

### New patterns, raised in the PR

Both of these land in C1 and are named in its PR body for review:
- **`MomentsRevision`** (D5): a cross-tab change signal.
- **The `UITabBarAppearance` shim** (§6): it sets the unselected item colour and the hairline, which iOS 17 SwiftUI cannot express.

## 8. Accessibility

- Tab items use `Label(_:systemImage:)` with the localized key, so VoiceOver reads "Path, tab, 1 of 4". No custom `accessibilityLabel` is needed.
- The FAB reads "New pebble". The karma button keeps "Karma N" and gains the hint "Opens You" / "Ouvre Toi". The Ripple badge keeps its own label.
- Reselect scroll-to-top posts no announcement. The pop is already announced as a screen change.
- Dynamic Type: the system tab bar handles large content (long-press shows the large-content viewer). The status cluster wraps its caption under the number at accessibility sizes, as `PathBottomBar` does now.
- The bar contrast pairs (`onSurfaceVariant` on `surfaceContainer`, `primary` on `surfaceContainer`) are Stack A pairs and AA by construction there. A missing pair is added to `verify-color-contrast.mjs`.
- Reduce Motion: the reselect pop and scroll use the system animations, which already honour it.

## 9. Testing

- **Swift Testing, `PebblesTests/Features/Tabs/TabNavigationTests.swift`:**
  - `select` covers switch, pop-on-reselect, and scroll-on-reselect-at-root (asserts `scrollToTop` increments only at root).
  - `popToRoot` leaves other tabs' stacks intact.
  - `snapshot`/`restore` round-trips all four stacks plus `selected`.
  - Decoding is tolerant: a snapshot written with an unknown tab raw value or a removed route case decodes to defaults, not a crash.
  - `reset()` empties everything.
- **Resolver tests:** `SoulRoute` with an id missing from a loaded `ReferenceDataService` pops itself.
- **`LocalizationTests`:** the new keys ("People", "You", "Circle" if new, the karma hint) have `en` and `fr` values.
- **No B2 reference images in stack C.** The tab bar is drawn by the system, not by a view of ours, and every tab root scrolls, which B2's harness forbids. Stack C is covered by the Swift Testing state-model tests above and the device checklist below.
- **Device checklist** (iPhone 15, both appearances):
  1. Cold launch lands on Path, and the loader hands off without a spinner flash.
  2. Push People › Soul › and switch to You and back: the soul is still pushed.
  3. Reselect People pops to root, and a second tap scrolls to top.
  4. Path reselect at root returns to the current week.
  5. Background the app, kill it from Xcode (not the switcher), relaunch: the tab and stacks are restored.
  6. Do the same with the restored tab = People: no stuck loader.
  7. Delete a moment from Collections › detail, then switch to Path: it is gone.
  8. Karma tap selects You.
  9. An invite universal link while on any tab presents the accept sheet.
  10. First-run onboarding still covers the tab bar.
  11. Sign out and back in as another account: the tabs reset.
  12. VoiceOver walk of the bar.

## 10. Parts

**PR metadata (both parts):**
- Labels: `feat`, `ui`, `core`, `ios`, plus `no-lab-note`, because Arkaik coverage replaces the Lab Note.
- Milestone: **M64 · New Path**. The maintainer moves #903 there.
- Stacked with `gh stack`, C1 below C2.

**C1 — `feat(ios): four tabs with their own stacks`.** Branch `feat/903-ios-four-tabs`. Covers §2–§6, D5 and §8. It adds `Features/Tabs/` (`AppTab`, the routes, `TabNavigation`, `PebblesTabView`, `SoulRoute`, `CollectionRoute`). It strips `PathView`'s `NavigationStack` and `PathRoute.profile`, replaces the bottom inset with the FAB row, and converts the root-crossing links to value links. It adds the Connections toolbar button on the souls list, updates `RootView` (composition, onboarding cover move, `holdsLoader`, reset on sign-out), sets the bar appearance, adds the strings and the state-model tests, and adds `MomentsRevision` with its bumps and Path's reload (D5).
- **FAB string.** The FAB's FR label comes from the existing key "New pebble", which gets "Nouveau galet". Stack B1's #1101 rider carries that value. If B1 has not landed when C1 is cut, C1 sets it. Whichever lands first wins, and the other becomes a no-op.
- **Arkaik at pickup.** C1 moves the touched views (listed under C2) to `development` through `arkaik-mcp`.
*Done when:* the four tabs render authed-only, per-tab stacks survive switches and a system kill, reselect behaves per §4, `PathBottomBar` is no longer rendered, the hub still works (its cards now push into the You stack, or are untouched where they point elsewhere), lint, `TabNavigationTests` and the full test suite are green, and device checks 1–10 and 12 pass.

**C2 — `feat(ios): the hub gives souls and collections to their tabs`.** Branch `feat/903-ios-hub-handoff`.
- Deletes `PathBottomBar.swift` and `NewPebbleButton.swift`.
- Removes the Souls and Circle tiles from `ProfileShortcutsRow`, keeping Glyphs, and deletes `ProfileCollectionsCard`, then any component left unreferenced (`ProfileCollectionCard`, `CollectionModeBadge` if unused).
- Appends the decision-log entry **"iOS converges on the four-tab IA (#903)"**, which records D1–D5 and the absent long-press, and supersedes nothing (the 2026-09-21 entry's "iOS and Android now differ in IA" consequence is answered by it).
- Replaces the `apps/ios/CLAUDE.md` line "The authed root is `PathView` with a custom bottom bar (not a system `TabView`)" with: the authed root is a system `TabView` (Path / People / Collections / You), each tab owning a `NavigationStack` driven by `TabNavigation`; sheets and covers still do secondary navigation.
- Completes the hosted Arkaik edge changes through `arkaik-mcp` only, never `docs/arkaik/bundle.json`. C1 already set these views to `development`:
  - `V-timeline` (Path: FAB + status cluster, profile entry removed);
  - `V-profile` (now the You tab; souls/collections/connections entries removed);
  - `V-souls-list` (People tab root; reaches `V-connections-list`);
  - `V-collections-list` (Collections tab root);
  - `V-connections-list` (parent is now People);
  - `V-soul-detail` and `V-collection-detail` (parents change).

  Edges: remove Profile→Souls, Profile→Collections and Profile→Connections, and add the tab-root edges. No status change happens in C2. The GitHub App handles `releasing`, and nothing here sets `live`.

*Done when:* the hub has no duplicate routes, `grep PathBottomBar` returns nothing, device check 11 passes, the decision-log, CLAUDE.md and Arkaik changes are in the same PR, and #903 closes with it. #446 and #444 are unblocked: their hook is now `PathStatusCluster`.

## 11. Risks

- **Memory and offscreen work under `TabView`.** All four stacks stay alive once visited. Path holds a Rive cairn strip, Metal stone layers and possibly `StoneMotionSource` tilt. While another tab is showing, `TabView` keeps them mounted.
  - *Mitigation:* the tilt and Rive playback pause on `.onDisappear` and resume on `.onAppear`. C1 checks that `StoneMotionSource` stops CoreMotion updates, and adds that if it does not.
  - Profile Instruments on device before and after C1. A growth of more than one Path-sized heap on a full four-tab tour is a blocker.
- **`.sheet` on a non-selected tab.** Presenting from a view that is not in the window hierarchy logs "not in the window hierarchy" and drops the sheet. Today every trigger is local to the visible tab, and `InviteAcceptSheet` is above the `TabView` by design. The rule for future work: **cross-tab presentation goes through `RootView` or the card-stack host, never by setting another tab's state.**
- **Reselect detection.** The `Binding` setter firing for the already-selected tab is long-standing behaviour, not a documented contract. It is the first device check in C1. If an iOS 17.x point release does not call it, the fallback is a `UITabBarControllerDelegate` shim found through the hosting controller. That is rejected unless needed, because it is UIKit introspection.
- **Status bar and safe areas.** Path hides its navigation bar and draws under the status bar via `pebblesScreen()`. Inside a `TabView` the bottom safe area now includes the bar. The FAB row's `safeAreaInset` must sit above it, not under the home indicator twice. The tab bar background must be `.visible` so the scrolling week list does not show through it in light mode.
- **Keyboard avoidance.** Tab roots have no text input, but pushed screens and sheets do. A `safeAreaInset(.bottom)` row rides up with the keyboard. If a future Path search field appears, the FAB row takes `.ignoresSafeArea(.keyboard, edges: .bottom)`. Sheets are unaffected because they own their own keyboard layout.
- **Restoration of stale ids.** Handled by the resolvers (§3). The second risk is a snapshot from an older build with a removed route case. Tolerant decoding falls back to defaults rather than crashing.
