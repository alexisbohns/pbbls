# iOS Handcrafted Logo Loader — Design

**Issue:** #598 (iOS part) · **Date:** 2026-07-17 · **Surface:** `apps/ios`

## Problem

The app opens on a **fake** loader: a Rive logo (`pbbls-logo-appear_idle.riv`)
held centered inside `WelcomeView` by a **hardcoded 2.5s timer**
(`RootView.minSplashSeconds`) that has nothing to do with real readiness. The
animation plays, then a real loader would appear behind it — the loader doesn't
actually load anything.

We now know how to render a glyph with the handcraft **wobble** effect and to
animate it with the **boil** effect (#555). Replace the fake Rive loader with a
native, handcrafted glyph loader that **is** the real loader: it plays the logo
draw-on, then boils while the app finishes loading, and only reveals the app
once it is genuinely ready.

## Decisions (locked with product)

- **Wait behavior:** after the draw-on completes, **boil** the logo (#555) until
  the app is ready. Draw-on and boil never run at the same time — draw fully,
  *then* boil. Once ready, the logo **settles to static** (boil = "loading is
  happening"; static = "done").
- **Rive removal:** remove Rive from the launch flow **entirely**. The
  handcrafted logo is both the loader and the `WelcomeView` header logo.
- **Ready gate:** auth resolved **and** reference data (palettes + refs) load
  attempts settled — see "Ready gate" for the failure-safety requirement.
- **Frame production:** **runtime wobble** (approach A) — reuse the existing
  wobble engine; no offline bake pipeline. Cross-platform baked-frame parity is
  a separate #555 concern and is out of scope here.

## Source artwork — must be the issue's SVG

The loader consumes the **exact SVG in issue #598**, bundled as a loose
resource (`Resources/pbbls-logo-loader.svg`). It is *not* the same as the
existing `Assets.xcassets/WelcomeLogo.imageset/welcome-logo.svg`: in the issue
version the **fossil is strokes** (not a fill) and previously-closed strokes
have been opened. The wobble effect displaces the per-vertex geometry of each
path's `d` string, so we need the raw vector paths — a PDF (even vector) does
not expose individual path `d` data to the displacer and cannot be used.

Structure (viewBox `0 0 251 251`), by render mode:

| Group | Paths | Render |
|---|---|---|
| `pebble-outline` | 1 (closed) | stroke, width 6 |
| `creature` | `creature-01`…`12` | stroke width 6, **except** `creature-04-left_eye` / `creature-05-right_eye` which are `fill="black"` |
| veins | `pebble-vein-1`, `pebble-vein-2` | stroke, width 6 |
| `fossil` | `fossil-01-shell` + `fossil-02`…`10-line` | stroke, width 6 |

## Architecture

Reuse the existing wobble primitives directly — the `WobbleFlags` DEBUG gate
only guards the *pebble* render experiment, so the loader uses these in Release
regardless of that flag:

- `SVGPathParser.parse(_:) -> CGPath?` — parse each `d`.
- `WobblePathFlattener.flatten(_:step:) -> [WobblePolyline]`.
- `WobbleOutlineBuilder.art(for:halfWidth:params:noise:) -> WobbleArt` (ink +
  centerline) for **stroke** paths.
- Backdrop-style contour displacement (as in `WobbleRenderer.backdropArt`) for
  **fill** paths (the two eyes) — fills can't be trim-revealed.
- `SVGTurbulence(seed:)`, `WobbleParams.canonical`.

### New components (under `Features/Welcome/`)

**`LogoLoaderArt.swift`** — parse + variant builder (computed once, cached).
- Parses the bundled logo SVG, preserving **group membership** and per-path
  **render mode** (stroke vs fill).
- Normalizes geometry into the canonical **200-unit box** (#555 §2.1: uniform
  scale so the longest side = 180, centered with a 10-unit margin) so
  `WobbleParams.canonical` (amplitude 18, width 6) applies as authored.
- Builds **3 boil variants** (seeds 3 / 4 / 5, #555 §2.3 `seed = 3 + k`). Each
  variant holds, per group: the stroke groups' `WobbleArt` (ink + centerline)
  and the eyes' displaced fill paths.
- Exposes the four reveal groups in draw order.

**`HandcraftedLogoView.swift`** — the rendered loader. Phase state machine:
- **`.drawing`** — render **variant 0** only. Per stroke group: fill the ink
  masked by a fat trimmed stroke along the displaced centerline
  (`.trim(from: 0, to: progress)`), exactly as
  `PebbleAnimatedRenderView.animatedBody`. Reveal groups **in sequence**:
  1. `pebble-outline`
  2. `creature` (the two **eyes** fade/scale in at the tail of this phase — no
     trim, since they're fills)
  3. `veins` + `fossil` (together)

  Total ≈ 1.3–1.6s with light overlap. Fires `onDrawComplete` when finished.
- **`.boiling`** — `TimelineView(.periodic(by: 1.0/4.0))` cycling the 3 variant
  inks in ping-pong `order = [0, 1, 2, 1]` (#555 §3). Full logo, no trim. Runs
  **only** while the parent is still waiting on readiness.
- **`.settled`** — static variant 0, no timers.
- **Reduce Motion** (`@Environment(\.accessibilityReduceMotion)`): skip drawing
  and boil, render static variant 0 immediately, fire `onDrawComplete` at once.

The view takes `shouldSettle: Bool` (parent → "app is ready, stop boiling"). It
advances `.drawing → .boiling` on its own; when `shouldSettle` becomes true it
goes to `.settled`. If `shouldSettle` is already true when drawing completes, it
skips boil and settles directly.

### Wiring

**`WelcomeView`**
- Remove `import RiveRuntime` and `logoViewModel`. Both the centered slot and
  the post-reveal header slot render `HandcraftedLogoView`.
- Continuity (center → header) is unchanged: the existing `move(.bottom)`
  layout animation still translates the logo up when content reveals.

**`RootView`** — the real gate
- Delete `minSplashSeconds` and its sleep `.task`.
- `dataReady = !supabase.isInitializing && palettes.didFinishLoading && refs.didFinishLoading`
  (session-agnostic — auth resolution plus both reference-load attempts settled).
- `logoDrawComplete: Bool` — `@State`, flipped by `HandcraftedLogoView`'s
  `onDrawComplete`.
- `canProceed = dataReady && logoDrawComplete`.
- `canShowAuthedTabs = supabase.session != nil && canProceed`.
- `welcomeContentRevealed = supabase.session == nil && canProceed`.
- Pass `shouldSettle: canProceed` into the logo. While `!canProceed`, the logo
  draws then boils — this is the loader, persisting until genuinely ready. No
  spinner anywhere.

**`EmotionPaletteService` / `ReferenceDataService`** — failure-safe gate
- These `catch`-and-log on failure with no retry; a plain `hasLoaded` gate would
  trap the user on an **infinite boil** if the network fails. Add
  `didFinishLoading: Bool`, set in **both** the success and failure paths
  (i.e. after the attempt settles, regardless of outcome), preserving the
  services' existing "render empty on failure, recover next launch" philosophy.
- Bound each load's network call with a timeout so the attempt — and therefore
  the gate — cannot hang indefinitely. (Confirm current `load()` timeout
  behavior during implementation; add `withTimeout` if absent.)
- Update the now-stale doc comments in both services that reference
  `RootView.minSplashSeconds` / the 2.5s splash.

### Removed / untouched
- **Removed:** the Rive splash logo from the launch flow (`WelcomeView`'s
  `pbbls-logo-appear_idle` usage).
- **Untouched:** the `.riv` asset files; `WeekRollCairnCell`'s separate Rive
  usage; `Color+Rive.swift` (still used by the cairn); the `WobbleFlags` pebble
  experiment.

## Failure & edge cases
- **Slow/failed network:** draw-on completes, boil holds, gate proceeds once
  `didFinishLoading` flips (success or bounded-timeout failure) → app opens with
  a degraded (empty) reference cache, same as today's background-load failure.
- **Fast returning authed user:** `dataReady` true early; draw-on (~1.5s) is the
  long pole; on completion → swap to `PathView`. Boil never appears.
- **Reduce Motion:** no draw-on, no boil; static logo; gate is purely
  `dataReady`.
- **Backgrounding:** `TimelineView` naturally pauses when not rendering; no extra
  work needed for #555 §3's "pause when not visible."

## Testing
- Swift Testing unit test for `LogoLoaderArt`: the bundled SVG parses into the
  expected group/render-mode split; 3 variants build; variant paths are
  non-empty and differ (seeds 3/4/5 produce distinct geometry).
- Manual simulator verification (the real acceptance surface): cold launch draw
  order, boil cadence on a throttled network, settle-to-static on ready,
  Reduce-Motion static path, center→header continuity, authed vs unauth exits.
  Choreography, boil cadence, and stroke weight are tuned live here.

## Out of scope
- Android and web (separate issues / surfaces).
- Cross-platform baked-frame pixel parity (#555 bake pipeline).
- Changes to the `WobbleFlags`-gated pebble render experiment.
- Variable stroke width / paper texture / grit (#555 §7 non-goals).
