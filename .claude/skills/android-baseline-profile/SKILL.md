---
name: android-baseline-profile
description: "Use when regenerating or measuring the Android release baseline profile and startup profile (apps/android, :baselineprofile module) after a startup or Path change: the gradle commands, ANDROID_SERIAL, the BENCHMARK_EMAIL/BENCHMARK_PASSWORD throwaway account, and the StartupBenchmark. Loaded on demand."
---

# Android baseline profile (#856)

Moved here from `apps/android/CLAUDE.md` on 2026-10-06.

- **The release build ships a baseline profile and a startup profile**,
  committed under `app/src/release/generated/baselineProfiles/`
  (`baseline-prof.txt`, `startup-prof.txt`). `profileinstaller` hands the
  baseline profile to ART on install, so the first launches after an install or
  update run pre-compiled code; R8 uses the startup profile to put what a cold
  start touches in the primary dex. Every `bundleRelease` packs the committed
  files; nothing re-generates them in CI (`automaticGenerationDuringBuild =
  false`), because generation needs a device and a signed-in test account.
- **Regenerate by hand when startup or Path changes shape** (a new launch-time
  dependency, a new screen on the cold path, a big Path rework):
  `ANDROID_SERIAL=<device> ./gradlew :app:generateBaselineProfile`, then commit
  both files. Set `ANDROID_SERIAL` whenever more than one device is attached,
  or the task runs on all of them. The journey (`:baselineprofile`,
  `PebblesJourney.kt`) signs in with `BENCHMARK_EMAIL` / `BENCHMARK_PASSWORD`
  from `secrets.properties`: a throwaway account with pebbles on its Path,
  never a real one. Without them it profiles Welcome and Auth only; check the
  diff's size before committing a regeneration.
- **The journey finds the UI by `testTag`** (`core/common/JourneyTags.kt`,
  exposed as resource ids by `MainActivity`). `:baselineprofile` is a separate
  APK and repeats the strings, so renaming a tag fails nothing; it silently
  shortens the profiled journey.
- **Measure with** `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
  -P android.testInstrumentationRunnerArguments.class=app.pbbls.android.baselineprofile.StartupBenchmark`
  (cold start, no profile vs the committed one; results in the task's
  `benchmarkData.json`). A phone is the authority. An emulator on a loaded host
  swings by seconds; only its paired `None` vs `BaselineProfile` rows compare.
- **Anything added to `PebblesApp.onCreate` runs before the first frame of
  every launch.** It is empty on purpose.

