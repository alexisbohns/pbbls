# Navigation 3 on Android — typed keys, four tabs, covers as entries (M61)

**Date:** 2026-09-20
**Issue:** [#852](https://github.com/alexisbohns/pbbls/issues/852)
**Surface:** `apps/android` only. No schema, no RPC, no other client.
**Depends on:** [#848](https://github.com/alexisbohns/pbbls/issues/848) (Hilt), [#849](https://github.com/alexisbohns/pbbls/issues/849) (ViewModel per screen) — both closed. #849's folded-in remainder (`RootScreen`, `GlyphPickerSheet`, three `fireCheck()` calls) is scope here.

## 1. Why

The app runs `navigation-compose 2.10.1` with string routes, and follows M38 **D5**:
real pushes on a `NavHost`, everything modal as an `if (isPresenting…)` cover.
That shape is free on iOS, where `fullScreenCover` is a first-class presentation.
On Android it forfeits four things at once.

**Process-death restoration.** #849 moved the cover flags off `remember` and into
ViewModel `_covers` `StateFlow`s, which fixed rotation. It did not fix process
death: a `MutableStateFlow` in a `@HiltViewModel` dies with the process like any
other. Leave `RecordFlow` to pick a photo, get killed in the background, and you
return to the Path root with the flow gone.

**Predictive back.** `enableOnBackInvokedCallback="true"` is set
(`AndroidManifest.xml:13`), but all **16** handlers are the commit-only
`BackHandler`; there is no `PredictiveBackHandler` or `NavigationBackHandler`
anywhere. The gesture cannot scrub a cover or a record step. Only the app-exit
animation works. (The issue counts 15; it was written against `2e75ee15` and
`RecordFlowScreen.kt:121` uses the brace form, which a `BackHandler(` grep
misses.)

**The authed back stack itself.** Both `rememberNavController()` calls sit inside
conditionally-composed branches (`RootScreen.kt:141-195, 268`). On cold restore
the Welcome host mounts first during the splash hold, then the authed host mounts
fresh at `path`. Back-handler precedence between the covers and the `NavHost` is
an emergent property of sibling order in a `Box` — undocumented, untested, and
the kind of thing that changes when someone reorders two lines.

**Transitions and addressability.** Covers appear by instant composition, with no
`AnimatedVisibility`. A deep link cannot open one. The invite App Link therefore
routes through a mutable field on `ConnectionsService` (`MainActivity.kt:93-96`,
`ConnectionsService.kt:32`, `RootScreen.kt:164-169`): not saveable, not on the
back stack, and composed above onboarding, so a parked token covers a first-run
user.

Three smaller defects ride along. `AuthMode` round-trips through a route string
with a silent default (`RootScreen.kt:273-282`); `soulId` and `collectionId` read
back with `.orEmpty()`; and `BackHandler(enabled = !isSaving)` in six screens lets
back fall *through* to the host mid-save rather than being consumed.

The migration is mechanical precisely because the covers are already isolated
behind ViewModel flags and `onDismiss` lambdas. #849 did the hard half.

## 2. The information architecture

**Decided: an M3 `NavigationBar` with four top-level tabs and per-tab back
stacks.** This is a deliberate divergence from iOS, taken with its eyes open —
see D1.

```
Path          timeline (start route)
People        souls list
              ├ SoulDetail ─ SoulForm
              └ Connections ─ Invite
Collections   collections list
              └ CollectionDetail
You           profile hub
              ├ Glyphs ─ GlyphCarve
              ├ Achievements
              ├ Lab ─ LabAnnouncement
              │     └ LabLogList
              └ Settings
```

`SoulsListScreen` and `CollectionsListScreen` become tab roots rather than
children of Profile. `ProfileScreen` keeps its stats, achievements and lab cards
and loses its souls/collections/connections entry points; `ProfileSoulsCard`'s
`onOpenSouls`/`onOpenConnections` and `ProfileCollectionsCard`'s `onOpenList`
have no home once those surfaces are one tap away in the bar, and are removed
rather than left as duplicate routes into the same place.

### D1 — The bottom bar is a deliberate break from the 1:1 iOS mirror

The root `CLAUDE.md` names "iOS and Android mirror each other 1:1" as a hardened
cross-surface rule, and this breaks it at the navigation layer. That is the
decision, not an oversight, and it is recorded so nobody later "fixes" Android
back to a push IA.

The rule exists to protect the *database contract* and cross-surface behaviour —
a shared data shape, a timestamp precision, an RPC projection. Navigation
chrome is not that. The three things the rule actually guards (schema, RPC
payloads, cross-surface semantics) are untouched here: this PR stack adds no
column, changes no RPC, and alters no wire format.

What it costs is real: a screenshot reviewer comparing the two apps side by side
will see different chrome, and a future feature that adds a surface must now
decide *twice* where it lives. What it buys is that the app stops fighting the
platform. A pinned "New pebble" button in the bottom-quarter of the screen, fourteen
conditionally-composed covers and zero predictive back is an iOS app rendered in
Compose; Play Store reviewers, and users, read it as one.

**A follow-up issue will be filed against `apps/ios`**, in part 2, to decide
whether iOS adopts a `TabView` to converge or whether the two IAs stay
deliberately different. This spec does not decide that.

### D2 — Four tabs, with Souls and Collections promoted out of the hub

Considered and rejected: `Path / Souls / You` (three tabs), which leaves
Collections buried one level deeper than Souls for no reason other than tab
budget; and `Path / You` (two tabs), which is an unusual M3 shape and buys
per-tab stacks without buying discoverability.

Souls and Collections are the two surfaces with real browse depth and are peers
in the data model, so they are peers in the bar. `Connections` lives under
People because it is about the same entity — other humans — and is reached
rarely enough that a tab of its own would be dead weight.

### D3 — "New pebble" becomes a FAB on the Path tab

`PathScreen.kt:316` pins the "New pebble" button to the bottom of the timeline
(the iOS `PathView.safeAreaInset(.bottom)` analog). That is exactly where the
`NavigationBar` goes, so the collision has to be resolved.

It becomes a `Scaffold` FAB, **on the Path tab only**. Tap still opens the
record flow, long-press still opens the all-at-once form (M58 D1) — the
`onCreatePebble` / `onCreatePebbleLongPress` pair is preserved verbatim.

Rejected: a fifth, visually distinct centre item in the bar. A bar item that is
not a tab is a documented M3 anti-pattern, it would put a create action on every
tab including ones where it makes no sense, and long-press-for-form has no home
on a bar item.

Rejected: keeping the button pinned with the bar stacked below it. Two stacked
bars cost roughly 140dp of a phone screen, and the timeline is the one surface
that wants vertical space.

## 3. Navigation state

Modelled directly on the AndroidX `multiplestacks` recipe, which is the
supported answer to this exact question.

```kotlin
// navigation/NavigationState.kt
class NavigationState(
    val startRoute: PebblesKey,                       // Path
    topLevelRoute: MutableState<PebblesKey>,          // rememberSerializable
    val backStacks: Map<PebblesKey, NavBackStack<PebblesKey>>,
)
```

Each tab gets its own `rememberNavBackStack`, which is saveable — so process
death restoration is a property of the container rather than something each
screen re-implements. Each tab also gets its own
`rememberSaveableStateHolderNavEntryDecorator`, so a tab's scroll position and
transient UI state survive a trip to another tab.

`toDecoratedEntries()` flattens the stacks currently *in use* into the single
list one `NavDisplay` renders. `Navigator` is the only writer: `navigate`,
`goBack`, `onReselect`, plus a `replaceAll` this app needs for the auth switch
(§5) that the recipe does not have.

### D4 — Exit through home: start + current, never a growing tab history

`getTopLevelRoutesInUse()` returns `[Path]` when Path is current, and
`[Path, current]` otherwise. Back therefore unwinds the current tab's stack, then
falls back to Path, then exits.

```
Path › You › People, then back:
  ◀ Path        ← You is skipped
  ◀ exit
active stacks = {Path, People};  You's stack is retained, just not in the back path
```

The alternative — a full visited-tab history, where back walks every tab in
visit order — was rejected because the back path then grows without bound as a
user taps around the bar, and getting out of the app can take an arbitrary
number of presses. The recipe flags this as a design decision an app may
override; this app does not. The bounded version is also what Gmail and the Play
Store do, so it is what a user's thumb already expects.

**Tab reselect pops that tab to its root.** Tapping `People` while on
`People › SoulDetail › SoulForm` returns to the souls list. This is the standard
M3 affordance and gives every tab a reliable escape hatch. The recipe implements
reselect as a `Flow<NavKey>` the destination observes to reset scroll; this app
needs the stronger version (actually popping the stack), so `Navigator.onReselect`
truncates `backStacks[route]` to its first element and emits the event for any
screen that also wants to reset scroll.

## 4. Keys

`navigation/PebblesKeys.kt` — one `@Serializable sealed interface PebblesKey :
NavKey`, with two marker sub-interfaces:

```kotlin
sealed interface TopLevelKey : PebblesKey   // Path, People, Collections, You
sealed interface BarKey : PebblesKey        // renders the NavigationBar
```

| Group | Keys |
|---|---|
| `TopLevelKey` (also `BarKey`) | `Path`, `People`, `Collections`, `You` |
| `BarKey` pushes | `SoulDetail(id)`, `CollectionDetail(id)`, `Connections`, `Glyphs`, `Achievements`, `Lab`, `LabAnnouncement(logId)`, `LabLogList(mode)` |
| Modal (no bar) | `RecordFlow(resumeDraftId?)`, `CreatePebble(resumeDraftId?)`, `Drafts`, `PebbleDetail(id)`, `EditPebble(id)`, `Settings`, `SoulForm(id?)`, `CollectionForm(id?)`, `Invite`, `AcceptInvite(token)`, `GlyphCarve`, `Onboarding`, `Welcome`, `Auth(mode)` |

### D5 — The bar is a property of the key, not a parallel list

```kotlin
Scaffold(bottomBar = { if (topKey is BarKey) NavigationBar(…) })
```

One check, derived from the stack. The alternative — a `Set<PebblesKey>` of
bar-showing routes, or a `showsBar: Boolean` on each key — is a second source of
truth that can disagree with the first, and the disagreement shows up as a bar
that is visible over a full-screen composer. Marker interfaces make "I forgot to
add the new key to the set" a non-event: a new modal key simply isn't a `BarKey`,
which is the safe default.

`AuthMode` becomes a `@Serializable enum` parameter on the `Auth` key, so
`AuthMode.fromRoute`'s silent `LOGIN` default is deleted along with the route
string. `SoulDetail(id)` and `CollectionDetail(id)` carry non-null `String`s, so
both `.orEmpty()` calls go.

### D6 — Modals ride the active tab's own stack

A modal key is pushed onto `backStacks[topLevelRoute]`, not onto a separate
overlay stack rendered by a second `NavDisplay`.

Two stacks would mean two back handlers competing for precedence — which is the
exact defect this migration exists to remove, reintroduced one layer up. One
stack means back is one ordered list and `NavDisplay` owns it.

This works because of an invariant worth stating out loud: **tab switching is
only reachable from a `BarKey`, because the bar is hidden on every modal.** So a
non-current tab's stack can never have a modal sitting on top of it, and the
flattened `[Path…] + [current…]` list is always well-formed. Any future change
that makes the bar visible over a modal breaks this and must be rejected on
those grounds.

### D7 — Sheets stay sheets; no `DialogSceneStrategy`

All five `ModalBottomSheet` call sites — `EmotionPickerSheet`, `SoulPickerSheet`,
`ValencePickerSheet`, `GlyphPickerSheet` and `GlyphDetailDrawer` — return a value
to their parent and are meaningless without it. They stay `ModalBottomSheet`.

The confirm dialogs (`DeleteConfirmDialog`, `DeleteErrorDialog`) stay plain
composables too. `DialogSceneStrategy` earns its place only when a dialog must
survive process death, and "are you sure you want to delete this pebble" does
not — re-asking after a process death is the correct behaviour, not a bug.

## 5. Auth and onboarding become conditions, not branches

`RootScreen`'s `if (canShowAuthedTabs) … else …` is deleted. `RootViewModel`
(§7) watches session state and drives the stack:

| Transition | Action |
|---|---|
| session resolves to null | `navigator.replaceAll(Welcome)` |
| session resolves to non-null | `navigator.replaceAll(Path)` |
| first user id with `hasSeenOnboarding == false` | `navigator.navigate(Onboarding)` |

`replaceAll` clears every tab's stack, resets `topLevelRoute` to `Path`, and
seeds the target key. Because `Welcome`, `Auth` and `Onboarding` are ordinary
modal keys, they cover the bar with no special casing, and **the `NavDisplay` is
never unmounted** — which is the acceptance criterion. Sign-out stops being "drop
a subtree" and becomes "replace a stack".

The `hasHadSession` flag survives: it is what distinguishes "signed out" from
"auth has not resolved yet", and both still read as `userId == null`.

### D8 — Deep links push a key; the parked-token field is deleted

```
onNewIntent → parseInviteToken → signed in ? navigator.navigate(AcceptInvite(token))
                                           : RootViewModel.pendingInvite = token
```

`pendingInvite` lives in `RootViewModel`'s `SavedStateHandle` — a token the
server has not seen is exactly what `SavedStateHandle` is for (the `apps/android/CLAUDE.md`
rule), and it is what makes the token survive process death. It is consumed and
cleared *after* `replaceAll(Path)` and after Onboarding finishes, which is what
makes an invite open **after** onboarding rather than over it.

`ConnectionsService.pendingInviteToken` is deleted. It was never saveable, never
on the back stack, and its "belongs to the session it arrived in" cleanup logic
(`RootScreen.kt:120-129`) disappears with it: a key on a stack that `replaceAll`
clears needs no hand-written invalidation.

An `https://www.pbbls.app/auth/callback` App Link is added for the OAuth return,
with `pebbles://` kept as the fallback.

## 6. Back, transitions, and the six fall-through bugs

The acceptance criterion is that no `BackHandler` survives anywhere in
`features/`. All 16 go: **13 are deleted** because the entry now owns back, and
**3 are converted** to `NavigationBackHandler` (`androidx.navigationevent`) so
the gesture can be **cancelled** rather than only committed.

| Deleted by promotion (13) |
|---|
| `PebbleDetailScreen:75`, `EditPebbleScreen:80`, `DraftsScreen:66`, `CreatePebbleScreen:87`, `ConnectionsScreen:58`, `InviteScreen:78`, `AcceptInviteScreen:60`, `CollectionFormScreen:81`, `SoulFormScreen:84`, `SettingsScreen:116`, `GlyphCarveScreen:101`, `LabScreen:86`, `LogListScreen:75` |

| Converted to `NavigationBackHandler` (3) | Why it cannot be an entry |
|---|---|
| `OnboardingScreen:58` | Consumes back entirely, and must show no exit animation |
| `RecordFlowScreen:121` | Walks back through the wizard's internal steps, then raises the close-confirm |
| `AchievementMomentOverlay:97` | An overlay drawn above the `NavDisplay`, never on the back stack |

The record flow keeps its internal `AnimatedContent` step machine inside its
single `RecordFlow` entry. Steps are not keys: they are a wizard's internal
state, they have no meaningful deep link, and promoting them would put ten
entries on the back stack for one logical task.

### D9a — Lab's content swaps become keys

`LabScreen:84-101` swaps its own content for `AnnouncementDetailScreen` or
`LogListScreen` based on `covers.openAnnouncement` / `covers.seeAllMode`, with a
`BackHandler` to unwind them — the cover pattern under a different name, inside
a route that is itself a push. It has the same three defects: no process-death
restoration, no predictive back, no addressability.

So `LabAnnouncement(logId)` and `LabLogList(mode)` become `BarKey` pushes on the
`You` stack, and both `BackHandler`s go. This is the one place the migration
promotes something the issue's task list does not name, and it is in scope
because the acceptance criterion ("no `BackHandler` in `features/`") cannot be
met without it.

### D9 — The `enabled = !isSaving` bug is fixed by the promotion, not patched

`EditPebbleScreen:80`, `CreatePebbleScreen:87`, `CollectionFormScreen:81`,
`SoulFormScreen:84`, `SettingsScreen:116` and `GlyphCarveScreen:101` all read
`BackHandler(enabled = !isSaving)`. A *disabled* `BackHandler` does not block
back — it declines to handle it, so the event falls through to the next handler
and ultimately pops the host. The intent was "ignore back while saving"; the
behaviour is "leave the screen while saving".

Promotion fixes this structurally. The entry owns back, and a screen that wants
to refuse it does so with an *enabled* `NavigationBackHandler` that consumes and
ignores — never a disabled one.

**Transitions:** slide-up `transitionSpec` / `popTransitionSpec` /
`predictivePopTransitionSpec` for modal keys, shared-axis for `BarKey` pushes.
These are set per-entry via `NavDisplay.entryProvider` metadata, so a key's
animation travels with the key rather than living in a `when` at the display.

### D10 — Parents refresh on resume; no result bus

Path currently learns "a pebble was published" through `PathViewModel`'s cover
callbacks (`onFlowPublished`, `onFormCreated`), which the promotion removes.

Path's `NavEntry` outlives the trip to `RecordFlow`, so this uses the
**resume-refresh** pattern already established on this codebase — the #849 lesson
that a back-stack entry outliving a trip to a child needs a refresh when it comes
back — rather than introducing a result bus. The nav3 `lifecycle-owner` recipe
supplies the entry's `Lifecycle`; a `repeatOnLifecycle(RESUMED)` collector in the
screen triggers the reload the callback used to.

`ViewModel`s are scoped per entry with `rememberViewModelStoreNavEntryDecorator()`,
which is what makes `RecordFlowModel`, `SnapUploadCoordinator` and
`ComposerDraftCoordinator` die with their entry instead of with the screen that
happened to host them. `hiltViewModel()` works inside a decorated entry using the
`androidx.hilt.lifecycle.viewmodel.compose` import the repo already mandates;
keys carrying arguments use `hiltViewModel<VM, VM.Factory>(creationCallback = …)`
with `@AssistedInject`.

## 7. `di/ServiceGraph` dies

Folded in from #849 (decision log, 2026-09-20), which halved the bridge to ten
entries and named each survivor's blocker. Four of those blockers are this issue.

**`RootScreen` gets a `RootViewModel`.** It is the one stateful surface #849 left,
reading `supabase`, `connectionsService`, `karma` and `achievementNotify` off
CompositionLocals. §5 and D8 rewrite exactly that code, so the ViewModel is the
natural shape for what replaces it. `KarmaOverlayHost` and
`AchievementMomentOverlay` take **state** instead of the service object, the way
every other surface has worked since #849.

**`GlyphPickerSheet` gets the #849 treatment.** 432 lines holding its own
`itemsByTab`, `isLoading`, `loadFailed`, `reloadToken` and carve/buy content
swaps — a duplicate of the glyph store inside a picker, reading `glyphMarket` and
`pathStats` off locals. Promoting `GlyphCarve` to an entry removes the `isCarving`
swap; a `GlyphPickerViewModel` takes the rest. **PR #898's deliberate hook goes
with it**: `GlyphPickerContent` releases `GlyphCarveViewModel` on `unwind()`
because the picker closes the carve surface without a terminal effect, and an
entry with its own `ViewModelStore` makes that unnecessary.

**Three `fireCheck()` calls become lambdas.** `RecordSoulsStep`, `SoulPickerSheet`
and `ProfileAchievementsCard` read `LocalAchievementsService` only to fire an
achievement check. It is passed in.

After this, `di/ServiceGraph.kt` is deleted and `MainActivity` provides exactly
three CompositionLocals: `LocalEmotionPaletteService`, `LocalReferenceDataService`
and `LocalSnapURLCache`. Those three are **not** in scope and are meant to outlive
it — they carry ambient reference data read by leaf components, which is what a
`CompositionLocal` is for (decision log, 2026-09-20, and the carve-out in
`apps/android/CLAUDE.md`).

## 8. Testing

The parts of this with judgement in them are pure Kotlin, so they get JVM tests:

- **`Navigator`** — tab switch, reselect-pops-to-root, exit-through-home ordering,
  `getTopLevelRoutesInUse` returning start-plus-current, `replaceAll` clearing
  every stack.
- **Key serialization** — every `PebblesKey` round-trips through
  `NavKeySerializer`, including the nullable-argument keys (`SoulForm(null)`,
  `RecordFlow(null)`). A key that fails to serialize is a process-death
  restoration bug that no other test would catch.
- **`RootViewModel`** — the session→stack transition table in §5, and the pending
  invite surviving a `SavedStateHandle` round-trip and being consumed after
  onboarding rather than before.
- **`GlyphPickerViewModel`** — load, error and select against a fake, which is the
  #849 bar for extracting a `…Servicing` interface.

Screenshot coverage follows the existing convention: each promoted screen keeps
its stateless overload taking the `XUiState`, so the chrome, spinner and error
branches stay renderable without services. The bar itself gets a gallery preview
across the four tabs.

Instrumented coverage is deliberately **not** added for the predictive-back
gesture. Scrubbing a back gesture is not expressible in the JVM screenshot
harness, and standing up an instrumented suite for it is a larger change than
this stack; it is verified by hand on the Pixel 7 AVD against the acceptance
criteria, and noted here so the gap is a known one rather than an assumed pass.

## 9. Parts

One part is one branch is one PR, chained with `gh stack`. Ordered by dependency:
keys lowest, then the container, then the screens that consume it.

| # | Scope | Ships |
|---|---|---|
| 1 | Catalog + `PebblesKeys` + one `NavDisplay` over a flat `rememberNavBackStack(Path)` | Nav 3 in, string routes out. **IA unchanged, covers still covers.** |
| 2 | `NavigationState` + `Navigator` + `NavigationBar` + FAB | The four-tab IA (D1–D4) |
| 3 | Covers → entries: `SoulForm`, `CollectionForm`, `Settings`, `GlyphCarve`, `Invite`, `LabAnnouncement`, `LabLogList` | Predictive back + process death, profile side (D9a) |
| 4 | Covers → entries: `PebbleDetail`, `EditPebble`, `RecordFlow`, `CreatePebble`, `Drafts`; VM scoping | The write path (D9, D10) |
| 5 | Auth/Onboarding as keys, deep links through the stack, drop `navigation-compose` | Sign-in/out stops unmounting; invite survives process death (§5, D8) |
| 6 | `RootViewModel`, `GlyphPickerSheet`, `fireCheck` lambdas | `di/ServiceGraph` dies (§7) |

### D11 — Part 1 is a pure move, and does not touch the IA

Part 1 replaces both `NavHost`s with a `NavDisplay` over a single flat back stack
and keeps the Path → Profile → children IA exactly as it is. Part 2 then changes
the IA. Doing both at once would be one commit, and slightly less total work.

The root `CLAUDE.md` rule is explicit about why not: *"When work both relocates
existing code and changes it, make the relocation its own part. A pure-move diff
is read in seconds; the same change tangled with a redesign hides the redesign."*
A reviewer of part 1 checks one thing — that every route became the same
destination — and a reviewer of part 2 checks one thing — that the new IA is
right. Tangled, both reviews become one hard one.

Each part passes `ktlint` and `./gradlew lint` on its own, without the parts
above it.

## 10. Library versions

Navigation 3 is **stable**; nothing here is on an alpha.

| Artifact | Version | Note |
|---|---|---|
| `androidx.navigation3:navigation3-runtime` | `1.1.7` | |
| `androidx.navigation3:navigation3-ui` | `1.1.7` | |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | `2.11.0` | Same coordinated lifecycle release as the `2.11.0` already in the catalog — mixing versions across the lifecycle group is the classic `NoSuchMethodError` |
| `androidx.navigationevent:navigationevent-compose` | `1.1.2` | `NavigationBackHandler` |

`androidx.compose.material3.adaptive:material3-adaptive-navigation3` is named in
the issue's task list and is **deliberately not added**. It exists for adaptive
list-detail and supporting-pane Scenes on large screens; this app is phone-only
and portrait-locked, so it would be a dependency with no call site.

`navigation-compose 2.10.1` is removed in part 5, once nothing imports it.

## 11. Decision-log entries

Two appended entries, both in part 2 (the part that makes the IA real):

1. **The IA choice** — the four-tab bar, and the deliberate break from the 1:1
   iOS mirror rule (D1, D2), with the `apps/ios` follow-up issue referenced.
2. **The supersession of D5** — M38's "modal surfaces stay conditionally-composed
   covers" no longer holds; every full-screen cover is a nav entry. Supersede,
   don't edit.
