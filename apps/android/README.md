# Pebbles for Android

Native Android app for Pebbles — Kotlin + Jetpack Compose, minSdk 33 (Android 13),
phone-only. It mirrors the iOS app (`apps/ios`) and talks to the same Supabase
backend. See `CLAUDE.md` for conventions and
`docs/superpowers/specs/2026-07-10-android-bootstrap-design.md` for the milestone
design.

At this stage the app builds, lints, unit-tests, and launches to a debug preview
of the design system (theme tokens, typography, components, the Rive logo). The
auth funnel and Path timeline arrive in later sub-projects.

## Prerequisites

- **Android Studio** (latest stable) with the Android SDK. The app targets
  compileSdk/targetSdk 37.
- **JDK 21** (the Gradle toolchain and CI both use Temurin 21). Android Studio's
  bundled JDK works.
- The committed Gradle wrapper pins Gradle 9.4.1 / AGP 9.2.0 — no manual install.

## First-time setup

1. **Secrets.** Copy the example and fill in the two Supabase values (from the
   Supabase dashboard → Project Settings → API):

   ```sh
   cp secrets.example.properties secrets.properties
   # edit secrets.properties:
   #   SUPABASE_URL=https://<project>.supabase.co
   #   SUPABASE_ANON_KEY=<anon key>
   ```

   `secrets.properties` is git-ignored. The build still succeeds without it
   (values default to empty), but the app crashes at launch with setup
   instructions the moment it reads a missing value — by design.

2. **Open `apps/android/` in Android Studio** (or use the CLI below) and let it
   sync.

## Build, run, test

```sh
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew ktlintCheck          # lint
./gradlew ktlintFormat         # auto-fix lint
```

Run on a device/emulator from Android Studio (Run ▶), or:

```sh
./gradlew installDebug         # install on a connected device
```

## Installing without a local SDK (CI artifact)

The shared dev container has no Android SDK, so the debug APK is produced by CI.
On every pull request touching `apps/android/**`, the **Android** workflow
(`.github/workflows/android.yml`) runs ktlint + unit tests + `assembleDebug` and
uploads the result as the **`app-debug`** artifact. Download it from the PR's
Actions run and side-load it:

```sh
adb install -r app-debug.apk
```

## Seeing the UI without a device

Every CI run also **renders the app's screens to images** (Compose Preview
Screenshot Testing) and uploads them as the **`ui-screenshots`** artifact — so you
can review the UI as PNGs without Android Studio, an emulator, or a device.
Download it from the PR's Actions run, alongside `app-debug`. Today it renders the
design-system token preview (colors, type ramp, Rive logo) in light and dark;
each new screen adds a preview under `app/src/screenshotTest/`.

This renders-to-view — nothing fails on a visual diff. Committed baselines for
visual-regression testing are a future opt-in (see `CLAUDE.md`).

## npm / Turbo

`@pbbls/android` exposes `build` / `lint` / `test` scripts (via
`scripts/gradle-if-sdk.sh`) so the app participates in the monorepo's Turbo
pipeline. In an environment with no Android SDK, each script prints a warning and
exits 0 rather than failing — Gradle is the real build, and CI is the real gate.
There is no `dev` script (Gradle is the source of truth, matching `@pbbls/ios`).
