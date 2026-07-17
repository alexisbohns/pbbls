# Handcrafted Logo Loader — Android Porting Guide

**Source:** iOS implementation of issue #598 (branch `feat/598-ios-handcrafted-logo-loader`).
**Audience:** the Android developer porting this to `apps/android`.
**Goal:** port the loader without re-brainstorming. Every product decision and
every non-obvious implementation choice is captured here, with the SwiftUI
gotchas translated to Compose. Pair this with the iOS source and issue #555
(the wobble/boil spec, whose §5.3 already covers the Compose primitives).

> This doc is the *reconciled final state* after several rounds of simulator
> feedback — it supersedes the original iOS design spec
> (`2026-07-17-ios-handcrafted-logo-loader-design.md`) where they differ. The
> refinements below (per-path masking, sequential creature draw, count-based
> boil, settle-gated transition, feed cover) were all discovered during
> testing; don't rediscover them.

---

## 1. What this is

Replace the fake Rive splash (a hardcoded ~2.5s timer that played a Rive logo,
then a *separate* real loader appeared) with a **native handcrafted glyph
loader that IS the real loader**. It:

1. **Draws the logo on** — hand-sketched, stroke by stroke.
2. **Boils** it (the #555 hand-drawn wobble animation) while the app finishes
   loading.
3. **Settles** to a static logo and reveals the app — only once the app is
   genuinely ready, gated by an **event, never a computed duration**.

No spinner is ever shown. Rive is removed from the launch flow entirely.

## 2. Product decisions (locked)

| Decision | Choice | Why |
|---|---|---|
| Wait behaviour after draw-on | **Boil** (#555), then settle | Feels alive; boil = "still working", static = "done" |
| Rive removal | **Entirely** from launch flow | One consistent rendering; handcrafted logo is also the Welcome header |
| Ready gate | **Auth + reference data** (palettes + domains/souls/collections) settled | First real screen has no pop-in |
| Frame production | **Runtime wobble** (reuse the wobble engine), not baked frames | One logo; baking/cross-platform pixel-parity is a separate #555 concern |
| Draw order | **outline → creature → veins+fossil** | Reads like drawing a pebble: shell, then the creature, then decoration |
| Creature strokes | **One line at a time** (sequential) | A hand draws one stroke at a time; drawing them all at once looked wrong |
| Eyes | Displaced **fills**, popped in during the creature phase | Fills can't be trim-revealed |
| Boil duration | **A count of boil ticks**, not a duration | Agnostic/scalable; "boil N times then settle", never a brutal time-based cut |
| Transition trigger | The loader's **settle event**, not a timer or the draw-on end | The full boil always plays; no premature cut |
| Feed spinner | **Removed**; loader cover holds until the home feed mounts | "No more spinner" per the issue |

## 3. Source artwork

Bundle the **exact SVG from issue #598** (iOS: `Pebbles/Resources/pbbls-logo-loader.svg`).
It is NOT the older `welcome-logo.svg`: in this version the **fossil is
strokes** (not a fill) and previously-closed strokes were opened. viewBox
`0 0 251 251`. You need the raw per-`<path>` `d` strings (a PDF/vector image
won't do — the wobble displaces per-vertex geometry).

Groups, by `id` prefix and render mode:

| Reveal group | Paths | Render |
|---|---|---|
| `outline` | `pebble-outline` (1, closed) | stroke, width 6 |
| `creature` | `creature-01..12` **except** `-04-left_eye` / `-05-right_eye` (→ 10 strokes) | stroke, width 6 |
| `fossilAndVeins` | `fossil-01..10` + `pebble-vein-1/2` (12) | stroke, width 6 |
| `eyeFills` | `creature-04/05` (`fill="black"`) | **fill** |

**Keep every path separate** — do NOT merge a group's paths into one path.
(See §5, the bleed fix.)

## 4. The wobble & boil algorithm (platform-agnostic, from #555)

Reuse the Android wobble engine you already built for glyphs (mirror of iOS
`Features/Path/Render/Wobble/`). The loader needs:

- **Wobble** = displace a path's flattened vertices with SVG fractal noise
  (`feTurbulence`, seed S). Produces, per stroke: a filled **ink** path (the
  leaky hand-drawn shape) and a **centerline** path (the displaced skeleton).
- **Boil** = cycle **3 wobble variants** built with seeds **3, 4, 5** in
  ping-pong order **`[0, 1, 2, 1]`** at **4 fps** (discrete swaps, no
  interpolation). Reduce Motion → variant 0 only, no boil.

### Logo-specific tuning (do NOT reuse the canonical glyph params)

The loader has finer features than a glyph, so the canonical amplitude (18)
reads as jagged. Use a **gentler, logo-only** parameter set, authored in the
normalized 200-box and scaled into the 251 viewBox (same `scaled(for:)` §2.1
mapping the wobble engine already has). **Do not touch the shared glyph/pebble
wobble params.**

| Param | Logo value | (Canonical glyph value) |
|---|---|---|
| amplitude | **9** | 18 |
| frequency | **0.02** | 0.024 |
| octaves | **5** | 5 |
| flattenStep | **2** | 2 |
| stroke half-width | **3** (= SVG stroke-width 6 / 2) | — |
| boil seeds | **3, 4, 5** | — |

Build the 3 variants once and cache (iOS caches in a `static let`).

## 5. The draw-on reveal (the two non-obvious bits)

The draw-on fills each stroke's **ink**, masked by a fat stroke running along
its **centerline**, trimmed 0→progress. (iOS mask width = 15 viewBox units,
round cap/join; it over-covers the ink so trim=1 fully reveals it.)

**5a. Per-path masking — the bleed fix.** Each stroke is filled and masked
**independently** (its own ink, its own centerline). If you merge a group's
strokes into one ink + one centerline, the fat reveal mask sweeping one
stroke's centerline exposes a *neighbour* stroke's ink that happens to be
spatially adjacent (the creature body's mask flashed bits of the ears/spine).
Keep every stroke a separate masked unit.

**5b. Sequential creature — needs per-stroke animations, not one clock.** The
creature draws one line at a time. The trap: you cannot drive this from a
single animated `clock` scalar and compute each stroke's trim from it — the UI
framework only evaluates the view at the animation's *endpoint* (where every
derived trim = 1) and interpolates them all together, so they draw
simultaneously regardless of your stagger math. (This bit us twice.)
**You must animate one value per stroke**, each with its own start delay:

- iOS: `@State var creatureProgress: [Double]`; a `withAnimation(...).delay(base + i*stagger)` per stroke `i`.
- Compose equivalent: one `Animatable` per stroke (or a list), each launched in
  its own coroutine with `delay(base + i*stagger)` then `animateTo(1f)`. Do NOT
  derive per-stroke progress from a single `animateFloatAsState`.

Reveal order & timing (iOS final; tunable): outline draws (`easeInOut`), then
each creature stroke on a staggered delay, then veins+fossil together, eyes
fade/scale in at the tail of the creature phase.

## 6. The state machine & transition (event-based, not time-based)

This is the part to get exactly right — it's where the "brutal cut" and
"ignored boil count" bugs lived.

**The loader owns a phase machine:** `drawing → boiling → settled`.

```
run():
  reveal model (build + cache the 3 variants)
  if ReduceMotion:      revealAll(); phase = settled; await untilReady(); emit onSettled; return
  if cover mode:        revealAll(); phase = boiling; boil forever (parent removes it); return
  # main loader:
  startDrawOn()                         # per-stroke animations (§5b)
  sleep(totalDrawDuration)              # the ONLY duration wait — for the draw-on animation
  phase = boiling
  boil(until: ticks >= minBoilTicks AND appReady AND currentFrame == variant 0)
  phase = settled
  emit onSettled                        # <-- the app-transition trigger
```

Key rules:

- **`boil()` counts discrete ticks** (one ping-pong swap per tick). It settles
  only when it has boiled **at least `minBoilTicks`** (a count — iOS ships **8**),
  **and** the app is ready, **and** the current frame is **variant 0** (the same
  artwork `settled` shows) — so the switch to static is invisible (no jump). If
  the app isn't ready after `minBoilTicks`, it keeps boiling and re-checks each
  tick.
- **The transition is gated on `onSettled`, NOT on the draw-on finishing.** The
  original bug: the parent showed the app as soon as the draw-on ended and data
  was ready, so it tore the loader down after ~2 boils. `onSettled` fires only
  after draw + full boil, so the boil always plays.
- **`appReady` = raw data readiness** (auth + reference data), fed *into* the
  loader as its settle condition. The parent gates the app transition on the
  loader's `onSettled` — do not make the loader's settle depend on the parent's
  "can proceed" (that's circular).

**Compose note (important):** a long-running loop that reads the readiness flag
must read it *live*. In SwiftUI the running `.task` captures the view by value,
so the `shouldSettle` input is stale inside the loop — iOS mirrors it into
`@State` (`wantsSettle`) via `onChange` and the loop polls that. In Compose,
drive the loop from a `LaunchedEffect` and read the readiness `State`/`Flow`
directly (Compose reads are live), or collect it — just make sure the loop sees
updates.

### Readiness sources (what "ready" means)

- **Reference services** must expose a "load attempt **settled**" flag that
  flips on **success OR failure** (iOS: `didFinishLoading`, set in a `defer`).
  Gating on a success-only flag would hang the loader forever on a bad network.
- **Safety ceiling:** if data never settles, proceed anyway after a max wait
  (iOS: `loaderCeilingSeconds = 8`, folded into `dataReady`). This is a
  *maximum*, not the old *minimum* timer — it only fires on failure.

## 7. Unified render — no phase-swap flash

Render **all phases through one code path** whose view tree never changes —
only the data (per-stroke progress + which variant index) changes. iOS bug: a
`switch phase` swapped structurally different subtrees (masked draw vs plain
fill), so the framework tore one down and built the other → a one-frame **white
flash** between the last draw frame and the first boil frame. Instead:

- One `logoBody` renders every stroke masked-by-its-own-centerline, always.
- `variant index = boiling ? boilOrder[boilFrame % 4] : 0`.
- Progress values are simply all-1 after the draw-on. At the draw→boil boundary
  progress is already all-1 and variant is still 0, so nothing rebuilds.

## 8. Composition / gating (the parent — iOS `RootView` + `WelcomeView`)

- **Welcome (pre-login) hosts the loader** centered; on settle for an
  unauthenticated user, content reveals and the logo translates to the header
  (same handcrafted view, now static). Rive is gone from this screen.
- **Authed launch:** on the loader's `onSettled`, swap to the home feed — but
  **hold a second instance of the loader as an opaque cover over the feed**
  (iOS: `startSettled = true` → skips the draw-on, just boils) until the feed's
  first load fires an `onFirstLoad` callback, then **crossfade the cover out**
  (iOS: 0.35s opacity). This is what makes "no spinner" true end-to-end.
- **The home feed must not render its own spinner** on first load — render the
  plain app background instead; the cover owns the loading visual. (iOS:
  `PathView` initial-load branch renders `Color.system.background`, not
  `ProgressView`.)
- **Colour:** tint the ink with the **brand accent** (`Color.accent.primary`),
  not the foreground.

## 9. Final tuned values (starting point — all live-tunable)

```
draw-on:   outline (delay 0.0s, dur 1.2s)
           creature (delay 1.0s, window 2.2s), per-line dur 0.55s, staggered
           fossil+veins (delay 3.0s, dur 1.3s)
           eyes pop near the end of the creature phase
           easing: easeInOut
boil:      4 fps, order [0,1,2,1], minBoilTicks = 8
wobble:    amplitude 9, frequency 0.02, octaves 5, flattenStep 2, seeds 3/4/5,
           stroke half-width 3, mask width 15 (viewBox units)
ceiling:   8s max data wait
cover fade: 0.35s
```

## 10. iOS file map (for reference while porting)

| iOS file | Responsibility |
|---|---|
| `Resources/pbbls-logo-loader.svg` | the source artwork |
| `Features/Welcome/LogoLoaderArt.swift` | parse SVG → per-path groups; build + cache the 3 wobble variants (logo-tuned params); `parseGroups()`, `build()` |
| `Features/Welcome/HandcraftedLogoView.swift` | the loader view — phase machine, per-stroke draw-on, count-based boil, unified render, `onSettled` |
| `Features/Welcome/WelcomeView.swift` | hosts the loader (splash + header); Rive removed |
| `RootView.swift` | gating: `dataReady` → loader → `onLoaderSettled` → show app; authed feed cover + fade; safety ceiling |
| `Features/Path/PathView.swift` | home feed; `onFirstLoad` callback; no spinner on first load |
| `Services/EmotionPaletteService.swift`, `Services/ReferenceDataService.swift` | `didFinishLoading` (success-or-failure) |
| `Features/Path/Render/Wobble/*` | the reusable wobble engine (already mirrored on Android) |

## 11. Testing

Unit-test the pure logic (iOS: `PebblesTests/Features/Welcome/LogoLoaderArtTests`):
the SVG parses into the expected group counts (outline 1, creature 10,
fossil+veins 12, eyes present) and 3 distinct variants build (seeds 3/4/5
produce different geometry). Everything else (choreography, boil, transitions)
is verified on-device — this is an animation, so budget for simulator/emulator
tuning.

## 12. Out of scope

Web; cross-platform baked-frame pixel parity (#555 bake pipeline); the
`WobbleFlags`-gated pebble render experiment.
