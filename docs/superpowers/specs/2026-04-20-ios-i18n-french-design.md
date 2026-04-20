# iOS Internationalization Layer (French)

**Issue:** #288
**Platform:** iOS (`apps/ios`)
**Date:** 2026-04-20
**Author:** brainstormed with Claude

## Context

The iOS app was built English-only for the Luni App Contest deadline. Copy is
hardcoded across ~102 occurrences in 26 view files, plus a handful of
Swift-level static config trees (`WelcomeSteps`, `OnboardingSteps`,
`Valence.label`, `ValenceSizeGroup.name`/`.description`). Reference data names
(emotions, domains, card types, pebble shapes) are seeded in English in
Postgres and surface to the client via deterministic UUIDs
(`md5('emotions:' || slug)`).

## Goal

Build a complete iOS i18n layer so that:

- An English user sees the app in English.
- A French user sees the app in French.
- A user with any other system language sees the app in English (fallback).
- No user-facing string is hardcoded in a way that would require a code change
  to translate.

French is the first non-English language. Adding future languages must be a
single-file, additive change.

## Non-goals

- Web app i18n (different stack, separate issue if/when pursued).
- Backend-side localization of reference data via Postgres columns or
  translation tables — the client-side slug-keyed catalog strategy removes the
  need.
- Right-to-left language support.
- Over-the-air translation updates.
- An in-app language picker. Language is strictly system-driven per the issue
  acceptance criteria.

## Scope — three buckets of strings

All three ship in this PR. They use the same catalog; only the call-site
treatment differs.

| Bucket | What | Where | Call-site treatment |
|---|---|---|---|
| **(a)** UI strings in views | `Text("Path")`, `Button("Continue")`, `.navigationTitle("Souls")` | ~102 occurrences across 26 view files | Zero code change. SwiftUI's `Text`/`Button`/`Label` already accept `LocalizedStringKey` — auto-extracted into the catalog on build. |
| **(b)** Swift-level static copy | `WelcomeSteps`, `OnboardingSteps`, `Valence.label`, `ValenceSizeGroup.name`/`.description`, `AuthView.Mode` rawValue | 5-6 Swift files carry user-facing literals in `String` properties | Convert `String` → `LocalizedStringResource` on the struct/enum fields. Consumers pass the resource to `Text(…)`, which auto-localizes. |
| **(c)** DB-originated names | emotion and domain names | `Emotion`, `Domain` (and their nested `EmotionRef` / `DomainRef` forms carried inside `PebbleDetail`) | Add `localizedName` computed property keyed by slug (`emotion.joy.name`, `domain.work.name`). Falls back to the DB `name` column if the catalog has no entry. |

## Technique — Apple String Catalog (`.xcstrings`)

Introduced in Xcode 15. Single JSON file rendered in Xcode as a grid editor.
Rows are keys, columns are locales. Entries carry a **state** (`New`,
`Translated`, `Stale`, `Needs Review`).

**Why this over legacy `.strings` + `.stringsdict` or SwiftGen:**

- Auto-extraction from Swift source on every build (`SWIFT_EMIT_LOC_STRINGS=YES`)
  removes the hand-maintained key list that causes drift.
- Native pluralization per-entry (`one`/`other`/`few`/`many`).
- Single file scales cleanly to N locales — no `.lproj/` folder per language.
- Apple's 2026 default for new projects, aligned with the issue's "follow
  standard iOS guidelines" guideline.
- No third-party build dependency.

SwiftGen-style compile-time key safety is left as a future enhancement if and
only if string drift becomes a real pain point.

## File layout & build config

**New file:**

```
apps/ios/Pebbles/Resources/Localizable.xcstrings
```

**Changes to `apps/ios/project.yml`**, added under the `Pebbles` target's
`settings.base`:

```yaml
SWIFT_EMIT_LOC_STRINGS: YES
LOCALIZATION_PREFERS_STRING_CATALOGS: YES
```

**Changes to `apps/ios/Pebbles/Resources/Info.plist`**, add:

```xml
<key>CFBundleLocalizations</key>
<array>
    <string>en</string>
    <string>fr</string>
</array>
```

`CFBundleDevelopmentRegion` stays as `$(DEVELOPMENT_LANGUAGE)` (= `en`). This
is what makes unsupported system locales (Spanish, German, …) fall back to
English automatically.

After editing `project.yml`, run `xcodegen generate` (or `npm run generate
--workspace=@pbbls/ios`). No new Swift package or third-party dependency.

## Call-site patterns in detail

### Pattern A — UI strings in views (bucket a)

SwiftUI's `Text`, `Button`, `Label`, `.navigationTitle`, `.alert`, and
`TextField(placeholder:…)` all accept `LocalizedStringKey`. Zero code change:

```swift
Text("Path")                 // extracted on build → catalog key "Path"
Button("Continue") { … }
.navigationTitle("Souls")
```

**Opt-out cases** — when a literal must stay untranslated (e.g. the app name
`"Pebbles"` in `AuthView`), wrap in `Text(verbatim:)`:

```swift
Text(verbatim: "Pebbles")    // not extracted, renders literal
```

### Pattern B — Swift-level static copy (bucket b)

Change field types on the carrier struct/enum from `String` to
`LocalizedStringResource`. The literals stay in the Swift file — they still
auto-extract.

Before:

```swift
struct WelcomeStep {
    let id: String
    let title: String
    let description: String
}
```

After:

```swift
struct WelcomeStep {
    let id: String
    let title: LocalizedStringResource
    let description: LocalizedStringResource
}
```

Usage in the view requires no change — `Text(step.title)` accepts
`LocalizedStringResource` directly.

Files affected:

- `apps/ios/Pebbles/Features/Welcome/WelcomeStep.swift` +
  `WelcomeSteps.swift`
- `apps/ios/Pebbles/Features/Onboarding/OnboardingStep.swift` +
  `OnboardingSteps.swift`
- `apps/ios/Pebbles/Features/Path/Models/Valence.swift` (`Valence.label`,
  `Valence.shortLabel`, `ValenceSizeGroup.name`, `ValenceSizeGroup.description`)
- `apps/ios/Pebbles/Features/Auth/AuthView.swift` (`AuthView.Mode` — replace
  `rawValue`-as-label with an explicit `label: LocalizedStringResource`
  property)

### Pattern C — DB-originated names (bucket c)

One four-line extension per reference model, keyed off `slug`, with the DB
`name` as the fallback `defaultValue`:

```swift
extension Emotion {
    /// Localized display name, keyed by slug. Falls back to `name` (the DB
    /// value, English) if no catalog entry exists — safe for new emotions
    /// added server-side before iOS catches up.
    var localizedName: String {
        let key = "emotion.\(slug).name"
        return String(
            localized: String.LocalizationValue(key),
            defaultValue: name
        )
    }
}
```

Catalog entries added manually in Xcode, one per known slug:

| Key | English | French |
|---|---|---|
| `emotion.joy.name` | Joy | Joie |
| `emotion.sadness.name` | Sadness | Tristesse |
| `domain.work.name` | Work | Travail |
| … | … | … |

Call sites update from `emotion.name` to `emotion.localizedName` **only on
read paths**. Write paths to Supabase continue to use `slug` / `id`.

Two extensions on `Emotion` and `Domain` (the full reference models, which
carry `slug`). For the nested `EmotionRef` / `DomainRef` forms embedded in
`PebbleDetail` — which carry `id` + `name` but **no `slug`** — the plan
resolves slug via a lookup on the in-memory reference list that the app
already loads for the pickers. The lookup helper lives on the reference model
(e.g. `Emotion.slug(forId:)`) so the ref extension stays a one-liner:

```swift
extension EmotionRef {
    var localizedName: String {
        guard let slug = Emotion.slug(forId: id) else { return name }
        let key = "emotion.\(slug).name"
        return String(
            localized: String.LocalizationValue(key),
            defaultValue: name
        )
    }
}
```

`CardType` and `PebbleShape` are DB reference tables but are **not currently
modeled as iOS types** (no user-facing surface yet). If they grow user-facing
names in the future, the same `localizedName` pattern applies — out of scope
here.

## Language resolution & fallback chain

iOS does the resolution; we write no code for it. iOS inspects the user's
Preferred Languages list (Settings → Language & Region) and the app's
`CFBundleLocalizations`, picks the first match top-to-bottom, and falls back
to `CFBundleDevelopmentRegion` (= `en`) if none match.

Per-string fallback order:

```
1. Catalog entry for user's active locale (e.g. fr)
   ↓ if missing
2. Catalog entry for the source language (en)
   ↓ if missing
3. The raw key (or the DB `name` column, for Pattern C slugs)
```

| Scenario | Result |
|---|---|
| French user, FR catalog entry present | French value |
| French user, FR empty but EN filled | English value (visible in Xcode as `New` state before ship) |
| Spanish user (any non-EN, non-FR) | English throughout — per acceptance criterion |
| French user, new emotion slug without catalog entry | DB `name` column (English) via `defaultValue:` |
| New `Text("…")` added without a French translation | English shown to FR users; Xcode marks entry as `New` |

**iOS localizes for free** via `Locale.current`: relative dates
("il y a 2 heures"), month/day names, number formatting (comma decimal in
French), system error messages, system keyboards.

**iOS does not localize**:

- Hand-rolled `String(format: "…")` interpolations that bake in English
  grammar. None found in the scan but the plan includes a final sweep before
  migration.
- Right-to-left languages — not in scope.

## Testing & verification

Three layers, each with a specific job.

### Layer 1 — Swift Testing unit tests (`PebblesTests/LocalizationTests.swift`)

- `allReferenceSlugsHaveCatalogEntries` — iterate every emotion and domain
  slug the app knows, force-resolve `localizedName` in both `en` and `fr`
  locales, assert neither falls through to the DB fallback.
- `staticCopyTreesHaveNonEmptyFields` — iterate `WelcomeSteps.all` and
  `OnboardingSteps.all`; assert each `title` and `description` resolves to a
  non-empty string in both EN and FR.
- `frenchCoverageMatchesEnglish` — count catalog entries per locale; assert
  `fr.count == en.count`. Catches coverage gaps.

A one-time helper (`Bundle.forLocale(_:)`) forces a specific locale bundle in
test context. Tests use Swift Testing (`@Suite`, `@Test`, `#expect`).

### Layer 2 — Xcode's built-in state tracking

Xcode flags entries with `New` / `Stale` / `Needs Review` states in the
catalog editor. The discipline is: before every PR touching user-facing
strings, open `Localizable.xcstrings` in Xcode, confirm no `New` or `Stale`
entries for this branch's changes. This discipline is added as a line in
`apps/ios/CLAUDE.md`. Enforcement (CI) is deferred to FU-1.

### Layer 3 — Manual QA on Simulator

Checklist in the PR body:

- [ ] English simulator: all screens English; dates/numbers English-formatted.
- [ ] French simulator: all screens French; dates/numbers French-formatted
  ("il y a 2 heures", "12,5" with comma decimal).
- [ ] Spanish simulator: all screens English (fallback verified).
- [ ] Background, toggle system language FR↔EN, foreground → app re-renders.
- [ ] Walk through: Welcome → Onboarding → Auth → Path → create pebble →
  valence picker → emotion picker → detail → edit → Profile → Collections →
  Glyphs → Souls. Confirm no English strings leak on the French run.

### Not tested (and why)

- Snapshot tests per locale — overkill for V1; unit tests catch coverage
  gaps, and we have no visual regression infra yet.
- Translation correctness — human review before PR merge.
- Catalog lookup performance — iOS caches; no hot path concerns.

## Migration order (to be detailed in the implementation plan)

1. Build config — `project.yml`, `Info.plist`, regenerate Xcode project.
2. Create empty `Localizable.xcstrings`, verify build extracts keys.
3. Pattern C — add `localizedName` extensions on `Emotion` / `Domain` and the
   `EmotionRef` / `DomainRef` ref forms; add `slug(forId:)` lookup helpers;
   wire read sites; seed catalog entries for all known emotion and domain
   slugs.
4. Pattern B — convert static copy trees to `LocalizedStringResource`.
5. Pattern A — sweep all 26 view files; add `verbatim:` where needed
   (e.g. "Pebbles" brand name).
6. Fill the French column in Xcode for the full catalog.
7. Write Layer 1 unit tests.
8. Run Layer 3 manual QA on simulator.
9. Document the maintenance discipline in `apps/ios/CLAUDE.md`.

## Follow-up issues to create after this PR merges

### FU-1 — `[Feat] i18n maintenance enforcement (instructions vs CI)`

Decide between (or combine) a documented rule in `apps/ios/CLAUDE.md` and a
CI script (`npm run i18n:check --workspace=@pbbls/ios`) that parses
`Localizable.xcstrings` and fails if any entry is in `New`/`Stale` state or
any locale column is empty. Labels: `quality`, `ios`.

### FU-2 — `[Feat] Localize date/number formatters audit`

Sweep the codebase for hand-rolled `DateFormatter(…)`, `NumberFormatter(…)`,
`Locale(identifier:…)` that would pin an English locale and override the
user's locale. Fix and document the convention. Labels: `quality`, `ios`.

## Acceptance criteria (from issue #288)

- [x] As an English user, when I open Pebbles, my app is in English.
- [x] As a French user, when I open Pebbles, my app is in French.
- [x] As a Spanish user, when I open Pebbles, my app is in English (default).
- [x] No more hardcoded strings (covered by buckets a, b, c).
- [x] Follows standard iOS guidelines (Apple String Catalog is the default).
- [x] Easy to maintain (auto-extraction + Xcode state tracking; enforcement
  layer tracked as FU-1).
- [x] Base user language on system language (iOS handles natively via
  `CFBundleLocalizations`).
- [x] French first (covered in this PR).
