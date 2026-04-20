# @pbbls/ios — agent context

Native iOS app for Pebbles. SwiftUI, iOS 17+, iPhone-only.

## Source of truth

- **`project.yml` is the Xcode project source of truth.** `.xcodeproj` is a git-ignored build artifact. Never hand-edit the pbxproj. After editing `project.yml`, run `xcodegen generate` (or `npm run generate --workspace=@pbbls/ios`).
- **Supabase config flows xcconfig → Info.plist → `AppEnvironment`.** Never hardcode keys in Swift. Never read `Bundle.main.infoDictionary` from arbitrary code — go through `AppEnvironment`.

## Swift conventions

- **iOS 17 APIs only.** No backports, no `if #available` guards. The deployment target is 17.0.
- **Use `@Observable` not `ObservableObject`.** It's the iOS 17 replacement and works with `@Environment(Type.self)` injection.
- **Use Swift Testing not XCTest.** `@Suite`, `@Test`, `#expect`. New code goes in `PebblesTests/` using the `Testing` module.
- **No UI tests for now.** Add a `PebblesUITests` target in a dedicated PR when smoke tests are actually needed.
- **Use `os.Logger` not `print`.** When async operations land, every error path logs — mirror the web-side discipline that silent failures are bugs.
- **Use `.task { ... }` for view-scoped async work.** It cancels automatically when the view disappears. Don't kick off `Task { ... }` from `.onAppear` without a reason.

## Data layer

- Views never construct `SupabaseClient` themselves. They pull `SupabaseService` from the environment: `@Environment(SupabaseService.self) private var supabase`.
- When a test needs to fake Supabase, extract a `SupabaseServicing` protocol at that moment — not before. YAGNI.

## Error handling

- `AppEnvironment` crashes with `fatalError` if config is missing. That's a setup bug, not a runtime condition.
- Runtime async failures must be surfaced: either logged with `os.Logger` or reflected in view state. No empty catch blocks.

## Localization

- **User-facing strings live in `Pebbles/Resources/Localizable.xcstrings`.** SwiftUI `Text`, `Button`, `Label`, `.navigationTitle`, and similar APIs that accept a `LocalizedStringKey` or `LocalizedStringResource` auto-extract their literal on every build via `SWIFT_EMIT_LOC_STRINGS=YES`. Struct/enum fields that carry user-facing copy declare their type as `LocalizedStringResource`.
- **Reference-data names (`Emotion`, `Domain`, `EmotionRef`, `DomainRef`) resolve via `localizedName`**, which keys the catalog by slug (`emotion.<slug>.name`) and falls back to the DB `name` column. Never render `.name` directly to the user on a read path.
- **Before every PR that touches user-facing strings**: open `Localizable.xcstrings` in Xcode and confirm no entry is in the `New` or `Stale` state. Confirm every row has a value in both the `en` and `fr` columns. Add new `ReferenceSlugs` entries whenever a new emotion/domain is seeded in the DB.
- **Brand names** (the word "Pebbles") and any literal that must render in English regardless of the active locale: wrap in `Text(verbatim: "…")` so they are not extracted.
- **Dates and numbers** are localized automatically by SwiftUI via `Locale.current`. Never construct a `DateFormatter` / `NumberFormatter` pinned to `Locale(identifier: "en_US")` — that overrides the user's locale.

## Folder layout

```
Pebbles/
  PebblesApp.swift          @main entry
  RootView.swift            Top-level TabView
  Features/<Feature>/       One folder per feature; matches web apps/web/components/<feature>/
  Services/                 Non-view code (Supabase, environment, future repositories)
  Resources/                Info.plist, Assets.xcassets, entitlements-adjacent files
```

Features map roughly to the web app's navigation structure: Path (home/timeline), Profile (access to Collections, Glyphs, Souls).

## What's scaffolded but not used yet

- `SupabaseService` is created and injected; no view calls it yet.
- `Pebbles.entitlements` declares Sign in with Apple; no sign-in UI yet.
- Asset catalog has an empty `AppIcon` slot — Xcode warns at build time. Expected.
