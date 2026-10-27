# Android month wrap: the animated intro — design addendum

- **Issue:** [#1242 — Animated intro for the month wrap (Android)](https://github.com/alexisbohns/pbbls/issues/1242) (feat, ui, android; milestone M64 · New Path)
- **Date:** 2026-10-26
- **Mirror of:** #1241, shipped on iOS as stack #1247 (#1243→#1251, merged 2026-10-26).
- **Scope:** `apps/android` only: the month page of the Path (`features/path/wrap/`), the figures it draws, the Spine Lab's Month tab, Settings, and the app's existing haptic call sites. No schema change, no RPC change.
- **Size:** Large. One stack of eight parts (§Parts).

## How to read this

This is an addendum. **The iOS spec is the source of the choreography:** `docs/superpowers/specs/2026-10-25-ios-month-intro-design.md`.
- **What ports:** its final shape: D1–D6 (the engine), then the revision block D8–D17, wherever they supersede the original.
- **What doesn't:** the iteration history:
  - D7's chapter paging (built and dropped on iOS);
  - D8's 85 % viewport trigger and arrive line;
  - the sections playing as they scroll in.
- **Also read:** the iOS plan's "As built" (`docs/superpowers/plans/2026-10-25-ios-month-intro.md`) and the four #1241 entries in `docs/decisions/log.md` (2026-10-25 and 2026-10-26).
- **#1242:** its body and two comments are the brief.

This addendum holds only what Android does differently, or how Android builds the same thing. Its decisions continue the iOS numbering from D18. Every time, tick strength, tempo, motion and word is iOS's unless a decision below says otherwise.

## What ports unchanged

- **The score:** the beats, keys, ticks, swells, curves, tempo (cover ×1.45, sections ×2.5) and every constant in `IntroScore`, `IntroMotion`, `IntroTick` and `IntroTempo`, as they stand on main at `b4a5d078`.
- **The lifecycle:**
  - once per month per launch, per sequence;
  - one voice at a time;
  - tap to skip;
  - focus loss and backgrounding settle;
  - a frozen clock for references.
- **The deck (D11):**
  - its cards in the server's order, an empty section dealing none;
  - tone and hours as separate cards, the insights head on the first;
  - the galley chrome, the stepper, a title before a card's turn, the arrive tick;
  - revisits settled;
  - every throw landing on a tick;
  - scale-to-fit, and *more* at the card's foot (D13).
- **The signatures (D10, D13, D16, D17), the end card and Replay (D14), and the swell (D15).**
- **What the month says (D12, D16):**
  - every week listed, an empty one muted;
  - milestones date first, in their lit stones, with their polaroid or their souls.

  This reaches the plain page too.
- **The haptics switch:** the second comment on #1242, and the decision log entry of 2026-10-26.

## Decisions (Android)

**D18. The intro's haptics are a composition, with two fallbacks.** Settled on the maintainer's Pixel 8 (Android 17) with a debug probe, which played the iOS presets and sequences two ways:
- **C:** one `VibrationEffect.Composition` per sequence;
- **V:** `View.performHapticFeedback` fired from the frame clock.

C felt more natural and closer to the iPhone, above all on the domains' swell. `dumpsys vibrator_manager` confirmed that the pauses play as compiled.

**The device:**
- primitives TICK 5 ms, LOW_TICK 12 ms and CLICK 26 ms;
- compositions of up to 254 primitives, with pauses of up to 10 s;
- amplitude control, and no envelope effects.

**The trade-off:** a composition that holds a primitive the device lacks plays nothing at all, and many phones lack primitives. So the backend picks a tier from the vibrator's capabilities:

| Tier | When | How |
|---|---|---|
| **C · Composition** | `areAllPrimitivesSupported(TICK)` | One composition per sequence, compiled from the beats. **Primitive by sharpness:** below 0.4 LOW_TICK, below 0.65 TICK, otherwise CLICK. **Scale:** the tick's intensity. **Substitutes:** a missing LOW_TICK becomes TICK ×0.7, a missing CLICK becomes TICK ×1.2 (clamped to 1). **Pauses:** each pause is the beat's time less the end of the previous primitive, by `getPrimitiveDurations`, never negative. **The swell (D15):** back-to-back LOW_TICKs (TICKs where LOW_TICK is missing), their scale ramping from `from` to `to` over the swell's sounding time, which stops a floor before the beat's end. This is the texture System UI's own slider uses on the Pixel. |
| **W · Waveform** | No TICK primitive, but `hasAmplitudeControl()` | **One `createWaveform(timings, amplitudes, -1)` per sequence:** each tick an 8 ms pulse at `intensity × 255`, silence between. **The swell:** 10 ms steps ramping `from → to`. Timing stays exact. |
| **V · View** | Neither | **`performHapticFeedback` on the page's view,** from the clock's frame callback: the last beat each frame crossed. **The constant, by intensity:** below 0.35 `CLOCK_TICK`, below 0.7 `CONTEXT_CLICK`, otherwise `VIRTUAL_KEY`. **No swell.** **Shared rules:** the floor still applies; nothing plays where `hasVibrator()` is false. |

- **Attributes:** tiers C and W vibrate with `VibrationAttributes.USAGE_TOUCH`, so Android's own Touch feedback setting governs them as it governs tier V. The device pass confirms this.
- **Stopping:** `Vibrator.cancel()` for C and W. Tier V stops by not firing.
- **The pure parts, tested on the JVM:**
  - the tier choice (`IntroHapticTier.of(primitives, amplitude, vibrator)`);
  - the 25 ms floor (`IntroHaptics.events`, transients only, as iOS);
  - the composition's primitives and pauses (`IntroComposition.of(beats, supported, durations)`);
  - the waveform's arrays (`IntroWaveform.of(beats)`).
- **The permission:** the main manifest gains `android.permission.VIBRATE`, a normal permission: no prompt and no Data Safety declaration. The app's existing haptics, all `performHapticFeedback`, never needed it.
- **The Lab:** the probe becomes the Spine Lab Month tab's controls:
  - *Replay* (forgets the month and replays from the cover);
  - a tier override (*Auto · C · W · V*), so each tier can be felt on the Pixel 8.
- *Considered and rejected:*
  - **V alone:** every phone ticks, but only three fixed textures, frame-quantised times, and no swell.
  - **Composition alone:** silent on phones without primitives.
  - **The waveform swell inside tier C:** felt on the probe, with a separate vibration handed over at each gauge. The LOW_TICK train felt closest to the iPhone and stays one voice.
  - **Android 16's envelope effects and `DELAY_TYPE_RELATIVE_START_OFFSET`:** they need API 36 (minSdk is 33), and the Pixel 8 reports no envelope support.

**D19. The engine runs on Compose's frame clock and draws in the draw phase.**
- **The port:** `IntroScore`, `IntroCurve`, `IntroMotion`, `IntroTempo` and `IntroLedger` port as pure Kotlin under `features/path/wrap/intro/`.
- **The types:**
  - `IntroKey` is a sealed interface of data objects and data classes;
  - `IntroReveal` is a `fun interface` from a key to 0…1, with `IntroReveal.Settled` (every key 1) and `IntroReveal.Hidden`.
- **`IntroClock`:** a plain `@Stable` class.
  - Phases are snapshot state.
  - One `withFrameNanos` loop runs only while a sequence plays, writing the frame time to a `mutableLongStateOf`. It's the twin of iOS's paused `TimelineView`, and it lands a sequence at its end, so there is no separate timer.
  - It takes `now: () -> Long` and has `advance(to)`, so its state machine is tested on a fake clock.
  - `IntroClock.frozen(month, sequence, beats, elapsed)` builds the references' clocks.
- **Reading reveals:**
  - Figures read their reveal inside `Canvas`/`drawBehind` lambdas.
  - Compose parts read theirs in a `Modifier.graphicsLayer {}` block (`Modifier.introReveal(key, landing)`: rise, fade or pop).
  - So a playing sequence invalidates drawing once per frame and never recomposes.
- **`LocalIntroStage` (new):** carries the clock and the current sequence to the rows. It is UI plumbing, not a service: the same footing as `LocalSharedTransitionScope` (decision log 2026-10-20), and outside the three-locals rule for services. It is null by default, so every figure draws settled (D2).
- **The `core/` boundary:** `core/` components never see an intro type. `MarkRow`, `DaySetMarks`, `CairnMark` and `SoulMark` take plain progress providers (`() -> Float`, or `(Int) -> Float`, defaulting to 1). The feature maps its keys onto them. `LitStone` changes only by the `rings` strength (D22). The stamp is a `graphicsLayer` at the call site.

**D20. The ledger, the policy, the lifecycle.**
- **The ledger:** `IntroLedger` is held by `PathViewModel`.
  - It lives as long as the Path tab's root entry, in practice the launch. That mirrors iOS's `PathModel`.
  - A rotation keeps it. Process death clears it, like a relaunch.
  - The Lab keeps its own.
- **`IntroPolicy.plays(reduceMotion, touchExploration, largeText)`:** no clock, and the plain page settled, under any of:
  - **Reduce Motion:** `rememberReduceMotion()`, animator duration scale 0.
  - **TalkBack:** `AccessibilityManager.isTouchExplorationEnabled`, through a new `rememberTouchExploration()` beside `rememberReduceMotion()` in `core/designsystem`, live on its listener.
  - **Large text:** `fontScale >= 1.5`. The Pixel's 1.5, 1.8 and 2.0 steps are Android's equivalent of iOS's accessibility sizes, the first past iOS's xxxLarge (1.35×).
- **The page is active** while it is the pager's settled page, the activity is resumed (`LocalLifecycleOwner`, `RESUMED`) and the policy allows.
  - Losing any of the three settles: the playing sequence lands, its haptics stop, and queued ones go back to waiting, unrecorded.
  - Leaving the composition does the same (`DisposableEffect`).
  - The active flag lives on `MonthPage`, outside the deck's replay key (D21's visibility trap).
- **Skipping:** a tap anywhere on the deck skips. It is observed in the initial pass of a `pointerInput` without being consumed, so the tap still opens what it hits (a milestone's held card, *more*'s sheet).

**D21. The deck is a `VerticalPager`.**
- **One card per throw:** Compose's pager snaps one page per fling by default (`PagerSnapDistance.atMost(1)`). That is iOS's "one card per throw" fix, natively. The device pass checks it with hard flings.
- **Nesting:** it nests in the Path's `HorizontalPager`. The axes are orthogonal, so horizontal swipes still move the Path.
- **`MonthDeck.cards(wrap, lookups)`:** pure. The cover if shown, each section that shows, then `END` whenever anything is dealt; a month with nothing to deal deals no end card either (as iOS built it, `MonthDeckTests`). Each section exposes `isShown(wrap, lookups)`, and its composable draws under the same predicate, so the two can't drift.
- **The motion:** from `pagerState.getOffsetDistanceInPages(page)`, in a `graphicsLayer`, with iOS's `MonthDeckMotion` numbers (pure, tested):
  - **A card to come:** held in place, its foot 10 dp below the top card, scaled 1 − 0.05 × d about its bottom.
  - **A leaving card:** scales 1 + 0.02 × |d| and turns 3° × d.
  - **Draw order:** an earlier card draws above a later one (`zIndex`).
  - **Pile edges:** up to two static edges behind the pager (`min(2, remaining − 1)`), fading as a throw lands.
  - `beyondViewportPageCount = 1`, so the next card is composed beneath.
- **The galley card (`MonthDeckCard`):** `SpineCardShell` with no palette (the stock, the card shadow, the Night ring), inset 12/8/22 dp (sides/top/bottom). It has:
  - a 28 dp margin ruled off in `cardRule`, the month and year set upright at its top (reading upward) and the stepper at its foot;
  - a title band: the head's label, its hint in italics at the far end, a rule under them;
  - the content top-aligned in the room. Content that fits is laid out in the whole room, so *more* stands at the foot. Content past it keeps its height and scales down, anchored at the top.
- **Heads:** each section and figure exposes its head (`MonthHead(label, hint)`) and takes `onCard: Boolean` to leave it out under the band. That's a parameter, not a local.
- **Arrival:** `snapshotFlow { pagerState.settledPage }`.
  - The clock shows that card: the previous one lands, and this one plays its arrive tick then its content.
  - When the settled card changes, `IntroClock.landed(card)` ticks the arrive tick for a card that plays nothing.
  - The top card is read from `settledPage`, never driven into the pager. That's iOS's "never a `scrollPosition` binding", which mattered there because a restored position scrolled the outer pager.
- **The end card (D14):** the cover's whole garland, settled, outside any clock, and "Replay the month" (`path_wrap_replay_month`).
  - Replay settles the old clock, resets the ledger's month, makes a fresh clock and remounts the deck from the cover, under a key that includes the month.
  - Replay runs on the next frame, so the skip from the same tap can't record the fresh cover.
- **The traps from #1242:**
  - **Unique keys:** the pager's item keys and the replay key are unique per month page.
  - **Ring dots:** drawn in sorted day order.
  - **Visibility:** the page's active flag is outside the replayed subtree.
  - **One card per fling:** the pager's default.
- **Large screens:** the deck keeps `readableWidth()`.

**D22. What the month says, on Android (D12, D16).**
- **Weeks:** every week of the month is listed, and the head counts all of them. An empty week is muted:
  - `CairnMark(days = emptyList(), state = LIVE)`, the strip's dotted next stone;
  - "No pebble." / "Aucun galet." (`path_wrap_line_no_pebbles`) in `ink3`;
  - no souls, and no click.
- **Milestones:**
  - `MarkRow` gains `subtitleFirst`, so the date sits above the name.
  - The mark is the Spine's lit stone. `LitStone` gains `rings: Float = 1f` (the halo, the rings and the ripple's strength), and milestones pass 0.45, so the Spine's renders are unchanged.
  - The stone sits in a column wide enough for its rings, on 56 dp rows.
- **`MilestoneSnap` (D16's polaroid):**
  - the stock under a `cardRule` border;
  - the photo in a window of 1:1, 3:4 or 4:3, whichever is nearest its ratio by the geometric midpoints (`MilestoneSnap.shape`, pure, tested), cropped;
  - a strip with two faint caption lines (`outlineVariant`);
  - tilted +4°, keeping within a box 1.15 × as wide as it is tall.
- **Loading the photo:** through Coil, from the signed URL `LocalSnapURLCache` gives.
  - The ratio comes from the loaded image, else from `SnapAspectCache` (passed down from `PathViewModel.snapAspect`), else square.
  - Until the photo loads, the window is a bed in the pebble's tint.
  - With no photo, the souls' glyphs stand there instead.
- **References:** the month page's references change, and CI re-records them.

**D23. The haptics switch and its plumbing.**
- **`HapticsPreference`:** a `@Singleton` on `SettingsStore`, key `haptics.enabled` (iOS's name), on until turned off, saved as it is switched.
- **Settings:** a Haptics section with one Vibrations switch and the footer, in the device-local group beside *Use wallpaper colours*. It bypasses the form's Save, as that switch does.

  | key | EN | FR |
  |---|---|---|
  | `settings_haptics_section` | Haptics | Retours haptiques |
  | `settings_haptics_vibrations` | Vibrations | Vibrations |
  | `settings_haptics_footer` | Every vibration in Pebbles, on this phone. | Toutes les vibrations de Pebbles, sur ce téléphone. |

- **Every haptic asks it first,** with the flag reaching each site through its ViewModel, never a new local:
  - `rememberTapHaptics(enabled)` (Capture, Complete, `SpinePebble`, the valence picker);
  - `RootViewModel` → the karma and achievement overlays;
  - the glyph store's ViewModel → `SlideToConfirm`;
  - `PathViewModel` → the intro's haptics, the deck's landing ticks and the pager's swipe tick.
- *Considered and rejected:*
  - **`View.isHapticFeedbackEnabled` on the activity's view:** it would not reach sheets, which have their own windows (the valence picker, the glyph drawer).
  - **A global mutable flag:** it bypasses Hilt.

**D24. Throws and swipes tick.**
- **A deck throw:** lands on a tick (D21).
- **A swipe of the Path's pager** that settles on another page plays `TapHaptic.SELECTION`, as iOS's `SpinePager` does.
- **A strip tap** plays it too. Android's strip had no haptic, which was drift against iOS's `PathStrip`.

## Score API (Kotlin)

```kotlin
sealed interface IntroSequence { data object Cover; data class Section(val section: MonthSection); data object End }
sealed interface IntroKey { /* one data object or data class per iOS case, same names */ }
data class IntroTick(val intensity: Float, val sharpness: Float) { companion object { /* faint … arrive, seal(i), cairn(i) */ } }
data class IntroSwell(val from: Float, val to: Float, val sharpness: Float)
data class IntroBeat(val key: IntroKey, val at: Double, val duration: Double, val tick: IntroTick? = null, val swell: IntroSwell? = null)
fun interface IntroReveal { operator fun invoke(key: IntroKey): Float }
object IntroScore { /* cover, persons, gem, gemRuns, sweep, tone, hours, ranks, weekColumns, milestones, domains, end, head, duration */ }
object IntroCurve { fun times(count: Int, duration: Double): List<Double> }
```

Times are seconds (`Double`), as iOS's `TimeInterval`, so the Swift tests port with their values unchanged.

## Testing

- **JVM (JUnit, `testDebugUnitTest`):**
  - The Swift tests under `apps/ios/PebblesTests/Wrap/Intro*Tests.swift`, `MonthDeckTests.swift` and `MonthCardsTests.swift` port as oracles, with the same inputs and the same expected values: curve, cover, persons, gem and its runs, insights, signatures, tempo, clock, landing, rules and motion.
  - The Android-only pure parts: the tier choice, the composition, the waveform, `MilestoneSnap.shape`, the week columns, the deck's cards, motion and step.
- **Screenshot references (`src/screenshotTest/`), recorded by CI only:**
  - Part 1 re-baselines `MonthPage*`.
  - Every later part keeps every existing reference unchanged and adds frozen-clock frames:
    - `IntroGarlandHalf` (Part 2);
    - `MonthDeckCover` and `MonthDeckSection` (Part 3);
    - `IntroPersonsRing` (Part 4);
    - `IntroGemSecondFamily` (Part 5);
    - `IntroToneSweep` (Part 6);
    - `MonthDeckWeeks` and `MonthDeckEnd` (Part 7).
  - Part 8 re-baselines `Settings*`.
- **Device pass on the Pixel 8,** after each part: the maintainer tries what the part adds. Across the stack:
  - the feel and the tiers;
  - the Touch feedback setting;
  - the composition's drift (Risks);
  - hard flings;
  - the pager's horizontal swipes from every card;
  - Replay;
  - TalkBack, Reduce Motion and large text;
  - interruptions: swipe away, background, tap.

## Parts (`gh stack`; labels `feat`, `ui`, `android`, `no-lab-note`; milestone M64 · New Path)

| Part | Branch | Contents |
|---|---|---|
| 1 | `feat/1242-android-month-content` | D22: every week, the empty week muted; milestones date first, lit stones at 45 %, polaroids or souls. This addendum and the plan. Re-baselines `MonthPage*`. |
| 2 | `feat/1242-android-intro-engine` | D18–D20: the score (cover), curve, motion, tempo, ledger, clock, policy, `LocalIntroStage`, `introReveal`, the three haptic tiers, the VIBRATE permission. The cover plays in the Spine Lab's Month tab (Replay, tier override); the Path is untouched. The decision-log entry. `IntroGarlandHalf`. |
| 3 | `feat/1242-android-intro-deck` | D21, D24: the deck, the galley card, the stepper, the arrival and landing ticks, the pager's and the strip's selection tick, the Path wiring (ledger on `PathViewModel`), the plain page under the policy. Sections deal settled cards. |
| 4 | `feat/1242-android-intro-persons` | The persons' choreography (glyph, name, ring dot by dot) and their rows filling the card (D13). `IntroPersonsRing`. |
| 5 | `feat/1242-android-intro-gem` | The gem: the count-up, then the families' facets. `IntroGemSecondFamily`. |
| 6 | `feat/1242-android-intro-insights` | Tone (the sweep, the pulses) and hours (the dial, the stones by time of day), the shared insights head. `IntroToneSweep`. |
| 7 | `feat/1242-android-intro-signatures` | D17's week columns, D16's stamps and ripple, D10's domain gauges and swell, D13's rows sharing the room, D14's end card and Replay. `MonthDeckWeeks`, `MonthDeckEnd`. |
| 8 | `feat/1242-android-haptics-switch` | D23: `HapticsPreference`, the Settings section, every haptic site gated. Resolves #1242. |

Each part builds, lints (`ktlintCheck` and `lint`) and passes `testDebugUnitTest` alone.

## Arkaik

The acceptance "the month's intro plays once per launch, ticking each slot" moved to `development` for Android on 2026-10-26. Its gherkin still describes the scrolled-in sections. Part 3 rewrites it to the deck's shape, on both platforms, sending the whole metadata back.

## Risks

- **Composition drift.** On the probe, 20 TICKs 32 ms apart played in 660 ms against 613 ms scored: about 1–2 ms per primitive past their reported durations. A 31-day garland may end about 50 ms behind its frames.
  - Part 2's device pass measures the cover with `dumpsys vibrator_manager`.
  - Past 30 ms, `IntroComposition` subtracts a measured per-primitive overhead from each pause.
- **Vendor HALs.** Primitive support and amplitude control vary widely. The tier choice reads the vibrator itself, never the model, and only the Pixel 8 is felt. Tiers W and V are felt on the Pixel through the Lab's override.
- **The system's Touch feedback setting.** It is expected to silence `USAGE_TOUCH` compositions and waveforms. The device pass confirms it. Pebbles' own switch (D23) ships either way.
- **Per-frame cost.** The garland redraws up to 31 seals per frame for about 2.2 s, and the gem 28 facets.
  - Draw-phase reads keep recomposition out of it.
  - If frames drop on the Pixel (the profiler's frame timeline), the landed marks draw into a cached layer (`graphicsLayer` with `CompositingStrategy.Offscreen` around the settled ones), and only the moving ones redraw.
- **Nested pagers.** A diagonal drag on a card could start the wrong pager. Compose resolves the axis on the first slop, and the device pass checks swipes from every card.
- **Robolectric and previews feel nothing.** Haptics are proven by the JVM tests of their compile steps and by the device pass alone.
