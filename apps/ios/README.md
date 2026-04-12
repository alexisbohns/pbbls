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

You should see a two-tab app: Path and Profile.

## Project structure

```
apps/ios/
  project.yml              XcodeGen source of truth — edit this, not .xcodeproj
  Pebbles/
    PebblesApp.swift       @main entry
    RootView.swift         Top-level TabView
    Features/              Feature folders (placeholder views for now)
    Services/              SupabaseService, AppEnvironment
    Resources/             Info.plist, Assets.xcassets
    Pebbles.entitlements   Sign in with Apple
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

V1 shell. Two empty tabs, Supabase SDK wired but not used. Features will be added in subsequent PRs.
