---
name: android-screenshot-gate
description: Use when an Android PR turns the screenshot check red, when re-baselining Compose Preview Screenshot Testing references, approving the held CI runs after a re-baseline, adjusting the diff threshold, or adding preview variants (large font, French, wide) to apps/android. Loaded on demand; the never-bend rules stay in apps/android/CLAUDE.md.
---

# Android screenshot validation gate (#847)

Moved here from `apps/android/CLAUDE.md` on 2026-10-06. The rules that never bend are still in that file; this is the full mechanics.

**Compose Preview Screenshot Testing** (`com.android.compose.screenshot`) renders
the `@PreviewTest` composables in `src/screenshotTest/` to PNGs on the JVM (no
device, no Robolectric). It is a **regression gate**: the reference PNGs are
committed under `app/src/screenshotTestDebug/reference/`, and `android.yml` runs
`validateDebugScreenshotTest` on every PR. A render that moves past the threshold
fails the PR and uploads the reference/actual/diff triptych as
`ui-screenshot-diffs`.

- **Previews render a screen's stateless content layer**, fed a UiState and the
  harness's fakes, so they cover the screen's own chrome and error branch. Add
  a preview per screen and state as UI lands.
- **Changing UI turns the check red. That is the gate working, not a bug.** The
  fix is to re-baseline, never to weaken the threshold or re-ignore the
  references.
- **Re-baseline is one step: add the `rebaseline-screenshots` label to the PR.**
  `android-screenshots.yml` re-renders and commits the new PNGs to the branch. The
  same workflow runs from the Actions tab on any ref; dispatched on `main` it opens
  a branch instead of writing to it.
- **After a re-baseline the Android check is still red, and it needs a hand.** The
  push is attributed to `github-actions[bot]`, so the runs queued against the new
  commit are held at `action_required`: approve them from the PR's Checks tab (or
  `gh api --method POST repos/<owner>/<repo>/actions/runs/<id>/approve`), or push
  any further commit yourself. Do **not** re-run the failed run: a re-run replays
  the commit it was created for, which is the tree from before the re-baseline.
  The workflow's own step summary says all of this at the moment you need it.
- **Never commit references rendered on your own machine.** `./gradlew
  updateDebugScreenshotTest` locally is the right way to *look* at a change, but
  CI is the authority. macOS and the CI runner disagree on every render that
  draws vector art (pebble silhouettes come out one colour level apart, which
  byte-exact comparison counts as every filled pixel), and dozens of renders
  fail locally on `main` too. Render locally, review, then let the
  `rebaseline-screenshots` label regenerate the committed set on the runner.
  `./gradlew validateDebugScreenshotTest` locally still tells you *which*
  previews moved; judge a local run by diffing against `main`'s local run.
- **The threshold lives in `app/build.gradle.kts`**, set on the validation task
  because alpha16 exposes no DSL for it. It is calibrated (0.05% of pixels)
  against a measured real change, and the comment there carries the numbers. Do
  not widen it to paper over a host-platform difference.
  `libs.versions.toml` pins the plugin with `strictly` for the same reason: any
  layoutlib movement re-baselines every reference at once, so that has to be a
  deliberate commit that re-runs the re-baseline job, not a Dependabot drive-by.
- **Variants are multipreview annotations in `PreviewVariants.kt`.** Stack
  `@PreviewLargeFont` / `@PreviewLargeFontTall` (font scale 2.0, the
  accessibility ceiling), `@PreviewFrench` (`values-fr`) or `@PreviewWide` /
  `@PreviewWideTall` (840 dp and 1024 dp) on an existing `@PreviewTest` function
  to add a render without touching its body. The original reference's file
  name is unaffected, so adding a variant never re-baselines what was already
  there.

## The Arkaik mapping

`apps/android/arkaik-shots.json` maps Arkaik node ids to reference stems, so the
hosted map can show each Android view's render. It has 18 entries today.

- **A mapped preview keeps its name, or the mapping moves with it.** Renaming a
  preview, adding a variant to it, or deleting it means editing the mapping in
  the same PR. Only Light/Paper, default-size, English renders fill a view's
  slot; a variant goes in only when mapped to an acceptance id.
- **Check it locally** with `node scripts/arkaik/android-shots-manifest.mjs
  --check`. `android.yml` runs the same check right after checkout, before
  Gradle, and fails when a stem no longer resolves to exactly one PNG.
- **A re-baseline on `main` records itself** through
  `.github/workflows/android-screenshots-record.yml`; nothing to do by hand.
- **Keep `name =` off a mapped preview.** The plugin appends a preview's
  `name` to the reference file name, spaces and `·` included, which a stem
  cannot match. `ConnectionsListPreview` dropped its name for exactly this
  when it was mapped; the Collections list gained a phone-height
  `CollectionsListLight` because only its wide two-pane render existed.
