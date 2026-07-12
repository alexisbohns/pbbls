# @pbbls/ios

Native iOS app for Pebbles. SwiftUI, iOS 17+, iPhone-only.

## Prerequisites

- macOS with Xcode 15 or later
- iOS 17 simulator runtime (Xcode → Settings → Platforms)
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) and [SwiftLint](https://github.com/realm/SwiftLint):
  ```bash
  brew install xcodegen swiftlint
  ```

## First-time setup

1. Copy the config template and fill in real Supabase values:
   ```bash
   cp Config/Secrets.example.xcconfig Config/Secrets.xcconfig
   ```
   Edit `Config/Secrets.xcconfig` and set `SUPABASE_URL` and `SUPABASE_ANON_KEY`. Get them from the Pebbles Supabase dashboard or from another developer via a secure channel. **Never commit this file.**

2. Generate the Xcode project:
   ```bash
   npm run generate --workspace=@pbbls/ios
   ```
   This produces `Pebbles.xcodeproj` — a build artifact, git-ignored.

3. Open `Pebbles.xcodeproj` in Xcode.

4. Select an iOS 17 iPhone simulator and hit ⌘R.

You should see the Welcome screen (Rive logo + carousel); after signing in you land on the Path timeline.

## Project structure

```
apps/ios/
  project.yml              XcodeGen source of truth — edit this, not .xcodeproj
  Pebbles/
    PebblesApp.swift       @main entry — constructs and injects the service graph
    RootView.swift         Auth gate + root composition
    Features/              One folder per feature (Path, Profile, Auth, Welcome, Onboarding, Glyph, …)
    Services/              SupabaseService, AppEnvironment, palette/reference-data services
    Resources/             Info.plist, Assets.xcassets, fonts, Rive/SVG assets
    Pebbles.entitlements   Sign in with Apple
  PebblesWidget/           Widget extension (Live Activity, retained for future use)
  PebblesTests/            Unit tests (Swift Testing)
  Config/
    Secrets.example.xcconfig  Committed template
    Secrets.xcconfig          Git-ignored, local only
```

## Workflow

- Edit `project.yml`, then run `npm run generate` (or `xcodegen generate`) before opening Xcode. Never hand-edit `.xcodeproj`.
- `npm run build --workspace=@pbbls/ios` — regenerate + build from the command line.
- `npm run test --workspace=@pbbls/ios` — run unit tests in the simulator.
- `npm run lint --workspace=@pbbls/ios` — SwiftLint.

## Troubleshooting

**App crashes on launch with "SupabaseURL missing or invalid":** you haven't copied `Secrets.example.xcconfig` → `Secrets.xcconfig` or the values are blank. Fix the file and hit ⌘R again. Xcode does not need to be restarted.

**Xcode says "Failed to resolve dependency supabase-swift":** `File → Packages → Reset Package Caches`, then `npm run generate`.

**xcodebuild errors with "Unable to find a destination matching iOS Simulator":** install the iOS 17 simulator runtime via `Xcode → Settings → Platforms`.

**First device build fails with a provisioning error about Sign in with Apple:** the capability must be enabled on the `app.pbbls.ios` App ID in your Apple Developer account. Simulator builds are not affected.

## Status

Full production app: auth (email, Apple, Google), Path timeline with pebble create/edit/detail, Profile (collections, souls, glyphs), glyph carving + marketplace, karma, Lab feed — localized en/fr. Builds on Xcode Cloud (`ci_scripts/ci_post_clone.sh`).
