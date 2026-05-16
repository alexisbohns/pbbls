# iOS Colors System Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Spec:** `docs/superpowers/specs/2026-05-17-ios-colors-system-refacto-design.md`
**Issue:** [#456](https://github.com/Bohns/pbbls/issues/456)
**Branch:** `quality/456-ios-colors-refacto` (already created)

**Goal:** Replace the legacy iOS color tokens with two clearly-scoped palettes — `Color.system` (4 primitives) and `Color.accent` (6 tiers) — and remove all legacy colorsets / `Color.pebbles*` constants from the codebase.

**Architecture:** Asset catalog gets 10 new colorsets (`System*`, `Accent*`) plus a retuned `AccentColor`. A new `Pebbles/Theme/Palettes.swift` file exposes two structs (`SystemPalette`, `AccentPalette`) as static instances on `Color`. A `Pebbles/Theme/ColorTokensPreview.swift` file serves as visual ground truth in Xcode. Component migration happens one feature folder per commit, interactively with the user, using existing `#Preview` blocks for verification. Final commit deletes `Pebbles/Theme/Color+Pebbles.swift` and the 11 legacy colorsets.

**Tech Stack:** SwiftUI, iOS 17+, Asset Catalog (`.xcassets`), Xcode Previews, `xcodegen`.

**Note on testing:** the iOS app has no automated tests yet (V1). Verification per commit is build (`⌘B` in Xcode or `xcodebuild -scheme Pebbles`) plus visual inspection in Xcode previews. There is no TDD step in this plan because there is no behavior to test — only visual rendering.

**Note on interactivity:** the user has explicitly asked to review each component commit together. Tasks 6–14 each contain a "walk the per-call-site decisions WITH USER" step that must pause for user input. Do not batch-commit these tasks autonomously.

---

## Refinement vs. spec

Discovery during planning revealed:

1. **Top-level `Pebbles/Components/*` shared widgets** (`PebbleRow.swift`, `Buttons/*`, `Inputs/*`, `Checkboxes/*`, `Auth/*`) carry legacy color refs and are consumed by multiple features (notably Auth). They need their own commit BEFORE the Auth commit so feature commits can verify against already-correct primitives.
2. **`Features/Auth/`** itself contains no direct color refs — its views compose the shared widgets above.
3. **`Features/PebbleMedia/`** has zero color refs. No commit needed.
4. **`Services/EmotionPaletteService.swift`** has a doc-comment reference to `Color.pebblesAccent` / `Color.pebblesAccentHex` that needs updating. Folded into the foundation commit.

Refined commit sequence:

```
1  feat(ios): add system + accent palettes
2  quality(ios): migrate shared components to new palette
3  quality(ios): migrate Auth feature to new palette
4  quality(ios): migrate Glyph feature to new palette
5  quality(ios): migrate Lab feature to new palette
6  quality(ios): migrate Onboarding feature to new palette
7  quality(ios): migrate Path feature to new palette
8  quality(ios): migrate Profile feature to new palette
9  quality(ios): migrate Shared ripples to new palette
10 quality(ios): migrate Welcome feature to new palette
11 quality(ios): remove legacy color tokens
```

---

## File structure

### Created files

| Path | Responsibility |
|------|----------------|
| `apps/ios/Pebbles/Resources/Assets.xcassets/SystemForeground.colorset/Contents.json` | System foreground primitive |
| `apps/ios/Pebbles/Resources/Assets.xcassets/SystemSecondary.colorset/Contents.json` | System secondary text primitive |
| `apps/ios/Pebbles/Resources/Assets.xcassets/SystemMuted.colorset/Contents.json` | System muted surface primitive |
| `apps/ios/Pebbles/Resources/Assets.xcassets/SystemBackground.colorset/Contents.json` | System background primitive |
| `apps/ios/Pebbles/Resources/Assets.xcassets/AccentDark.colorset/Contents.json` | Accent dark tier |
| `apps/ios/Pebbles/Resources/Assets.xcassets/AccentShaded.colorset/Contents.json` | Accent shaded tier |
| `apps/ios/Pebbles/Resources/Assets.xcassets/AccentPrimary.colorset/Contents.json` | Accent primary tier |
| `apps/ios/Pebbles/Resources/Assets.xcassets/AccentSecondary.colorset/Contents.json` | Accent secondary tier |
| `apps/ios/Pebbles/Resources/Assets.xcassets/AccentLight.colorset/Contents.json` | Accent light tier |
| `apps/ios/Pebbles/Resources/Assets.xcassets/AccentSurface.colorset/Contents.json` | Accent surface tier (alpha 0.10 baked in) |
| `apps/ios/Pebbles/Theme/Palettes.swift` | `SystemPalette`, `AccentPalette` structs + `Color.system`, `Color.accent` static instances |
| `apps/ios/Pebbles/Theme/ColorTokensPreview.swift` | Light + dark grid of every token, for visual verification |

### Modified files

| Path | Change |
|------|--------|
| `apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset/Contents.json` | Retune to `C07A7A` scheme-independent (alias of `AccentPrimary`) for Apple's tooling |
| `apps/ios/Pebbles/Services/EmotionPaletteService.swift` | Doc-comment update only |
| Shared widgets (Task 6 file list) | Replace legacy tokens with new palette |
| Per-feature files (Tasks 7–13 file lists) | Replace legacy tokens with new palette |
| `apps/ios/Pebbles/Features/Path/PathView.swift` | Additionally: inline the `pebblesPathBackground` recipe locally |
| `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift` | Doc-comment refs from `pebblesAccent` → `accent.primary` |

### Deleted files

| Path | Note |
|------|------|
| `apps/ios/Pebbles/Theme/Color+Pebbles.swift` | Final cleanup commit |
| 11 legacy colorset folders in `Assets.xcassets/` | Final cleanup commit (see Task 15) |

---

## Task 1: Add the four System colorsets

**Files:**
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/SystemForeground.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/SystemSecondary.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/SystemMuted.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/SystemBackground.colorset/Contents.json`

- [ ] **Step 1: Create the SystemForeground colorset**

File: `apps/ios/Pebbles/Resources/Assets.xcassets/SystemForeground.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x39",
          "green" : "0x36",
          "red" : "0x4A"
        }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xE4",
          "green" : "0xE2",
          "red" : "0xE9"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 2: Create the SystemSecondary colorset**

File: `apps/ios/Pebbles/Resources/Assets.xcassets/SystemSecondary.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x64",
          "green" : "0x5E",
          "red" : "0x7A"
        }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x9D",
          "green" : "0x97",
          "red" : "0xAF"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 3: Create the SystemMuted colorset**

File: `apps/ios/Pebbles/Resources/Assets.xcassets/SystemMuted.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xE4",
          "green" : "0xE2",
          "red" : "0xE9"
        }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x24",
          "green" : "0x20",
          "red" : "0x2E"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 4: Create the SystemBackground colorset**

File: `apps/ios/Pebbles/Resources/Assets.xcassets/SystemBackground.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xFF",
          "green" : "0xFF",
          "red" : "0xFF"
        }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x12",
          "green" : "0x10",
          "red" : "0x17"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 5: Verify all four files parse as JSON**

Run from repo root:
```bash
for f in apps/ios/Pebbles/Resources/Assets.xcassets/System*.colorset/Contents.json; do
  python3 -c "import json; json.load(open('$f')); print('OK: $f')"
done
```

Expected: four `OK:` lines. No JSON parse errors.

---

## Task 2: Add the six Accent colorsets

**Files:**
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentDark.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentShaded.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentPrimary.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentSecondary.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentLight.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentSurface.colorset/Contents.json`

All accent tiers are scheme-independent — a single color entry (no `dark` appearance variant).

- [ ] **Step 1: Create the AccentDark colorset (`#341B1B`)**

File: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentDark.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x1B",
          "green" : "0x1B",
          "red" : "0x34"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 2: Create the AccentShaded colorset (`#8C4949`)**

File: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentShaded.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x49",
          "green" : "0x49",
          "red" : "0x8C"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 3: Create the AccentPrimary colorset (`#C07A7A`)**

File: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentPrimary.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x7A",
          "green" : "0x7A",
          "red" : "0xC0"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 4: Create the AccentSecondary colorset (`#EAD3D3`)**

File: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentSecondary.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xD3",
          "green" : "0xD3",
          "red" : "0xEA"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 5: Create the AccentLight colorset (`#FAF4F4`)**

File: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentLight.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xF4",
          "green" : "0xF4",
          "red" : "0xFA"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 6: Create the AccentSurface colorset (`#C07A7A` @ alpha 0.10)**

The alpha is baked into the asset, so call sites just write `Color.accent.surface`.

File: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentSurface.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "0.100",
          "blue" : "0x7A",
          "green" : "0x7A",
          "red" : "0xC0"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 7: Verify all six files parse as JSON**

Run from repo root:
```bash
for f in apps/ios/Pebbles/Resources/Assets.xcassets/Accent{Dark,Shaded,Primary,Secondary,Light,Surface}.colorset/Contents.json; do
  python3 -c "import json; json.load(open('$f')); print('OK: $f')"
done
```

Expected: six `OK:` lines.

---

## Task 3: Retune `AccentColor.colorset` to match `AccentPrimary`

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset/Contents.json`

`AccentColor` is Apple's reserved slot used by SwiftUI's default tint and by `AppIcon` tinting. We keep it (no Swift code references it by name post-migration) and re-tune it to scheme-independent `#C07A7A` so it matches `AccentPrimary`.

- [ ] **Step 1: Overwrite `AccentColor.colorset/Contents.json` with scheme-independent `#C07A7A`**

File: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset/Contents.json`

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x7A",
          "green" : "0x7A",
          "red" : "0xC0"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 2: Verify it parses**

```bash
python3 -c "import json; json.load(open('apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset/Contents.json')); print('OK')"
```

Expected: `OK`.

---

## Task 4: Add `Palettes.swift` Swift façade

**Files:**
- Create: `apps/ios/Pebbles/Theme/Palettes.swift`

- [ ] **Step 1: Create the file with both palette structs and instances**

File: `apps/ios/Pebbles/Theme/Palettes.swift`

```swift
import SwiftUI

/// Four-tier system palette for interface chrome. Mirrors the structural
/// shape of `EmotionPalette` so token-aware UI code reads uniformly.
struct SystemPalette {
    let foreground: Color
    let secondary: Color
    let muted: Color
    let background: Color
}

/// Six-tier brand-accent palette. Designed on the same model as
/// per-emotion palettes (see `EmotionPalette`), extended with `dark` and
/// `shaded` tiers above `primary`.
///
/// `primaryHex` is exposed for SVG-text injection in `PebbleRenderView`
/// (which replaces `currentColor` literally inside SVG markup).
struct AccentPalette {
    let dark: Color
    let shaded: Color
    let primary: Color
    let secondary: Color
    let light: Color
    let surface: Color

    let primaryHex: String
}

extension Color {
    static let system = SystemPalette(
        foreground: Color("SystemForeground"),
        secondary:  Color("SystemSecondary"),
        muted:      Color("SystemMuted"),
        background: Color("SystemBackground")
    )

    static let accent = AccentPalette(
        dark:       Color("AccentDark"),
        shaded:     Color("AccentShaded"),
        primary:    Color("AccentPrimary"),
        secondary:  Color("AccentSecondary"),
        light:      Color("AccentLight"),
        surface:    Color("AccentSurface"),
        primaryHex: "#C07A7A"
    )
}
```

---

## Task 5: Add `ColorTokensPreview.swift` (visual reference)

**Files:**
- Create: `apps/ios/Pebbles/Theme/ColorTokensPreview.swift`

This file's only purpose is to render every token as a labelled swatch grid in both light and dark mode, so we can verify rendering throughout the rest of the migration.

- [ ] **Step 1: Create the preview file**

File: `apps/ios/Pebbles/Theme/ColorTokensPreview.swift`

```swift
import SwiftUI

private struct Swatch: View {
    let name: String
    let color: Color

    var body: some View {
        VStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(color)
                .frame(height: 56)
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.08))
                )
            Text(name)
                .font(.caption2.monospaced())
                .foregroundStyle(.primary)
        }
    }
}

private struct TokensGrid: View {
    private let columns = [GridItem(.adaptive(minimum: 110), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Section {
                    LazyVGrid(columns: columns, spacing: 12) {
                        Swatch(name: "system.foreground", color: .system.foreground)
                        Swatch(name: "system.secondary",  color: .system.secondary)
                        Swatch(name: "system.muted",      color: .system.muted)
                        Swatch(name: "system.background", color: .system.background)
                    }
                } header: {
                    Text("System").font(.headline)
                }

                Section {
                    LazyVGrid(columns: columns, spacing: 12) {
                        Swatch(name: "accent.dark",      color: .accent.dark)
                        Swatch(name: "accent.shaded",    color: .accent.shaded)
                        Swatch(name: "accent.primary",   color: .accent.primary)
                        Swatch(name: "accent.secondary", color: .accent.secondary)
                        Swatch(name: "accent.light",     color: .accent.light)
                        Swatch(name: "accent.surface",   color: .accent.surface)
                    }
                } header: {
                    Text("Accent").font(.headline)
                }
            }
            .padding()
        }
        .background(Color.system.background)
    }
}

#Preview("Tokens — Light") {
    TokensGrid().preferredColorScheme(.light)
}

#Preview("Tokens — Dark") {
    TokensGrid().preferredColorScheme(.dark)
}
```

- [ ] **Step 2: Build the iOS target to verify it compiles**

In Xcode: open `apps/ios/Pebbles.xcodeproj`, build with `⌘B`.

Or from CLI:
```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -30
```

Expected: `** BUILD SUCCEEDED **` (or the equivalent indicator).

If `xcodegen generate` is required (it shouldn't be — `project.yml` doesn't reference colorsets or Swift files explicitly), run `npm run generate --workspace=@pbbls/ios` first.

- [ ] **Step 3: Open `ColorTokensPreview.swift` in Xcode and verify previews render**

Open the file, enable canvas (`⌥⌘↩`), confirm both `Tokens — Light` and `Tokens — Dark` previews render all 10 swatches with the expected colors. **PAUSE for user visual confirmation.**

- [ ] **Step 4: Commit (commit 1 of the PR)**

```bash
git add apps/ios/Pebbles/Resources/Assets.xcassets/SystemForeground.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/SystemSecondary.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/SystemMuted.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/SystemBackground.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/AccentDark.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/AccentShaded.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/AccentPrimary.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/AccentSecondary.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/AccentLight.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/AccentSurface.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset/Contents.json \
        apps/ios/Pebbles/Theme/Palettes.swift \
        apps/ios/Pebbles/Theme/ColorTokensPreview.swift
git commit -m "$(cat <<'EOF'
feat(ios): add system + accent palettes

Introduces SystemPalette and AccentPalette structs (Color.system and
Color.accent) backed by 10 new colorsets in Assets.xcassets. AccentColor
slot retuned to C07A7A so SwiftUI's default tint and AppIcon stay
consistent post-migration. A ColorTokensPreview view renders every
tier in both schemes as visual ground truth for the upcoming
component migration.

Refs #456

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Mechanical mapping reference (used by Tasks 6–14)

Every per-feature migration commit applies these mechanical mappings via find-and-replace. Per-call-site decisions (the second table) are walked interactively with the user at each commit.

### Mechanical (search → replace, exact tokens)

| Legacy                       | New                  |
|------------------------------|----------------------|
| `Color.pebblesBackground`    | `Color.system.background`  |
| `Color.pebblesForeground`    | `Color.system.foreground`  |
| `Color.pebblesMuted`         | `Color.system.muted`       |
| `Color.pebblesMutedForeground` | `Color.system.secondary` |
| `Color.pebblesAccent`        | `Color.accent.primary`     |
| `Color.pebblesAccentSoft`    | `Color.accent.secondary`   |
| `Color.pebblesAccentHex`     | `Color.accent.primaryHex`  |
| `Color.rippleActive`         | `Color.accent.primary`     |

### Per-call-site decisions (walk WITH USER at each commit)

| Legacy                  | Likely candidates |
|-------------------------|-------------------|
| `Color.pebblesBorder`   | `Color.system.muted` / `Color.system.secondary` / `Color.accent.secondary` |
| `Color.pebblesSurface`  | `Color.system.muted` / `Color.accent.surface` |
| `Color.pebblesSurfaceAlt` | `Color.system.muted` / `Color.accent.surface` |
| `Color.rippleDefault`   | `Color.system.muted` / `Color.accent.surface` |
| `Color.rippleInactive`  | `Color.system.secondary` / `Color.accent.secondary` |
| `Color.pebblesListRow`  | Inline scheme switch locally (per spec); decide per call site whether to use `white`/`Color.system.muted` or a different mapping |
| `Color.pebblesPathBackground` | Inline scheme switch in `PathView` only |

### Per-feature workflow (apply inside Tasks 6–14)

1. List the files in this feature that touch legacy tokens (the task gives them).
2. Open the Xcode preview for each file (or, if missing, the next ancestor view that previews it).
3. Apply the mechanical mappings via the per-file `sed`/`Edit` calls.
4. For each per-call-site decision in this feature, propose a mapping; **PAUSE for user confirmation** before applying.
5. Build + visually verify previews in both schemes.
6. Commit.

---

## Task 6: Migrate shared `Pebbles/Components/*` widgets

**Files (relative to `apps/ios/`):**
- Modify: `Pebbles/Components/PebbleRow.swift`
- Modify: `Pebbles/Components/Buttons/GoogleSignInButton.swift`
- Modify: `Pebbles/Components/Buttons/AppleSignInButton.swift`
- Modify: `Pebbles/Components/Buttons/PebblesPrimaryButtonStyle.swift`
- Modify: `Pebbles/Components/Auth/PebblesAuthSwitcher.swift`
- Modify: `Pebbles/Components/Auth/LegalDisclaimerText.swift`
- Modify: `Pebbles/Components/Checkboxes/PebblesCheckbox.swift`
- Modify: `Pebbles/Components/Inputs/PebblesTextInput.swift`
- Modify: `Pebbles/Services/EmotionPaletteService.swift` (doc comment only)

- [ ] **Step 1: Inventory exact tokens used in each file**

Run from repo root:
```bash
for f in apps/ios/Pebbles/Components/PebbleRow.swift \
         apps/ios/Pebbles/Components/Buttons/GoogleSignInButton.swift \
         apps/ios/Pebbles/Components/Buttons/AppleSignInButton.swift \
         apps/ios/Pebbles/Components/Buttons/PebblesPrimaryButtonStyle.swift \
         apps/ios/Pebbles/Components/Auth/PebblesAuthSwitcher.swift \
         apps/ios/Pebbles/Components/Auth/LegalDisclaimerText.swift \
         apps/ios/Pebbles/Components/Checkboxes/PebblesCheckbox.swift \
         apps/ios/Pebbles/Components/Inputs/PebblesTextInput.swift \
         apps/ios/Pebbles/Services/EmotionPaletteService.swift; do
  echo "=== $f ==="
  grep -n "Color\.pebbles\|Color\.ripple" "$f"
done
```

Note the per-call-site tokens (anything in the "Per-call-site" table above).

- [ ] **Step 2: Apply mechanical mappings to all 9 files**

Use the `Edit` tool per file, replacing exact occurrences from the mechanical mapping table. For `Services/EmotionPaletteService.swift` the change is a doc-comment update only — `Color.pebblesAccent` → `Color.accent.primary`, `Color.pebblesAccentHex` → `Color.accent.primaryHex`.

- [ ] **Step 3: Walk per-call-site decisions WITH USER**

For each non-mechanical token discovered in Step 1, propose the candidate from the table, then **PAUSE for user confirmation** before applying.

- [ ] **Step 4: Build the iOS target**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -30
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Verify Xcode previews for any of the touched files that have `#Preview` blocks**

```bash
grep -l "#Preview" apps/ios/Pebbles/Components/PebbleRow.swift \
                   apps/ios/Pebbles/Components/Buttons/*.swift \
                   apps/ios/Pebbles/Components/Auth/*.swift \
                   apps/ios/Pebbles/Components/Checkboxes/*.swift \
                   apps/ios/Pebbles/Components/Inputs/*.swift 2>/dev/null
```

For each match, open in Xcode and confirm light + dark previews render without regression. **PAUSE for user visual confirmation.**

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Components apps/ios/Pebbles/Services/EmotionPaletteService.swift
git commit -m "$(cat <<'EOF'
quality(ios): migrate shared components to new palette

Replaces legacy Color.pebbles* tokens in Pebbles/Components/* (shared
auth widgets, buttons, inputs, checkboxes) and updates a doc-comment
reference in EmotionPaletteService. No behavior change; visual output
unchanged where mechanical mappings apply.

Refs #456

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Migrate `Features/Auth/`

**Files (relative to `apps/ios/`):** none direct in `Features/Auth/`. Auth UI is composed of the shared widgets migrated in Task 6.

- [ ] **Step 1: Re-grep to confirm**

```bash
grep -rn "Color\.pebbles\|Color\.ripple" apps/ios/Pebbles/Features/Auth --include="*.swift"
```

Expected: zero results.

- [ ] **Step 2: Manually verify the Auth flow in Xcode previews**

Open `apps/ios/Pebbles/Features/Auth/AuthView.swift`. Confirm `#Preview` renders in both schemes after the Task 6 changes. **PAUSE for user visual confirmation.**

- [ ] **Step 3: Skip commit — there is nothing to commit for this task**

Task 7 is verification-only. Proceed to Task 8.

---

## Task 8: Migrate `Features/Glyph/`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift`

- [ ] **Step 1: Inventory tokens**

```bash
for f in apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift \
         apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift; do
  echo "=== $f ==="
  grep -n "Color\.pebbles\|Color\.ripple" "$f"
done
```

- [ ] **Step 2: Apply mechanical mappings**

Edit each file per the mechanical mapping table.

- [ ] **Step 3: Walk per-call-site decisions WITH USER**

For each non-mechanical token, propose candidate, **PAUSE for confirmation.**

- [ ] **Step 4: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Verify Xcode previews for both files**

Open each in Xcode, confirm light + dark previews unchanged. **PAUSE for user visual confirmation.**

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph
git commit -m "$(cat <<'EOF'
quality(ios): migrate Glyph feature to new palette

Refs #456

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Migrate `Features/Lab/`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Lab/LabView.swift`
- Modify: `apps/ios/Pebbles/Features/Lab/Components/LogTimeline.swift`
- Modify: `apps/ios/Pebbles/Features/Lab/Components/FeaturedCommunityCard.swift`
- Modify: `apps/ios/Pebbles/Features/Lab/Components/AnnouncementRow.swift`
- Modify: `apps/ios/Pebbles/Features/Lab/Components/ReactionButton.swift`
- Modify: `apps/ios/Pebbles/Features/Lab/Views/AnnouncementDetailView.swift`

- [ ] **Step 1: Inventory tokens**

```bash
for f in apps/ios/Pebbles/Features/Lab/LabView.swift \
         apps/ios/Pebbles/Features/Lab/Components/LogTimeline.swift \
         apps/ios/Pebbles/Features/Lab/Components/FeaturedCommunityCard.swift \
         apps/ios/Pebbles/Features/Lab/Components/AnnouncementRow.swift \
         apps/ios/Pebbles/Features/Lab/Components/ReactionButton.swift \
         apps/ios/Pebbles/Features/Lab/Views/AnnouncementDetailView.swift; do
  echo "=== $f ==="
  grep -n "Color\.pebbles\|Color\.ripple" "$f"
done
```

- [ ] **Step 2: Apply mechanical mappings**

Edit each file per the mechanical mapping table.

- [ ] **Step 3: Walk per-call-site decisions WITH USER**

**PAUSE for confirmation** on each non-mechanical token.

- [ ] **Step 4: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Verify Xcode previews**

Open files with `#Preview` blocks in Xcode, confirm light + dark unchanged. **PAUSE for user visual confirmation.**

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Lab
git commit -m "$(cat <<'EOF'
quality(ios): migrate Lab feature to new palette

Refs #456

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Migrate `Features/Onboarding/`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Onboarding/OnboardingPageView.swift`

- [ ] **Step 1: Inventory tokens**

```bash
grep -n "Color\.pebbles\|Color\.ripple" apps/ios/Pebbles/Features/Onboarding/OnboardingPageView.swift
```

- [ ] **Step 2: Apply mechanical mappings**

- [ ] **Step 3: Walk per-call-site decisions WITH USER (if any)**

- [ ] **Step 4: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -10
```

- [ ] **Step 5: Verify Xcode preview**

Open `OnboardingPageView.swift` in Xcode. **PAUSE for user visual confirmation.**

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Onboarding
git commit -m "$(cat <<'EOF'
quality(ios): migrate Onboarding feature to new palette

Refs #456

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Migrate `Features/Path/` (largest feature)

**Files (22 files):**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`  *(also: inline `pebblesPathBackground` recipe here)*
- Modify: `apps/ios/Pebbles/Features/Path/ValencePickerSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/SoulPill.swift`
- Modify: `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/EmotionPickerSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebblePillFlow.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadTitle.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift` *(doc comments only)*
- Modify: `apps/ios/Pebbles/Features/Path/Components/WeekHeaderView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Components/PathBottomBar.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Components/NewPebbleButton.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Components/WeekRollCairnCell.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Components/WeekPathView.swift`

- [ ] **Step 1: Inventory all tokens used across the Path feature**

```bash
grep -rn "Color\.pebbles\|Color\.ripple" apps/ios/Pebbles/Features/Path --include="*.swift"
```

Save the output — this list drives the per-call-site review.

- [ ] **Step 2: Special case — `PathView.swift`**

Replace `Color.pebblesPathBackground` with an inline scheme switch:

```swift
private var pathBackground: Color {
    Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(named: "SystemBackground") ?? .black
            : .white
    })
}
```

Reference the local `pathBackground` property at the use site instead of the legacy token. **PAUSE for user confirmation on this inline shape before applying.**

- [ ] **Step 3: Special case — `PebbleAnimatedRenderView.swift` and SVG hex injection**

If this file (or any sibling) uses `Color.pebblesAccentHex` to inject hex into SVG markup, swap to `Color.accent.primaryHex`. The hex value is identical (`#C07A7A`) so visual output is unchanged.

- [ ] **Step 4: Apply mechanical mappings to the remaining files**

Edit each file per the mechanical mapping table.

- [ ] **Step 5: Walk per-call-site decisions WITH USER**

Walk file-by-file. For each non-mechanical token, propose candidate, **PAUSE for confirmation**, then apply.

- [ ] **Step 6: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -15
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 7: Verify Xcode previews (Path has many `#Preview` blocks — sweep them)**

```bash
grep -l "#Preview" apps/ios/Pebbles/Features/Path --include="*.swift" -r
```

Open each match in Xcode, confirm light + dark renderings. **PAUSE for user visual confirmation per file.**

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Path
git commit -m "$(cat <<'EOF'
quality(ios): migrate Path feature to new palette

Inlines the pebblesPathBackground recipe locally in PathView per the
spec (component-level, not a token). All other Path views adopt
Color.system.* / Color.accent.* directly.

Refs #456

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Migrate `Features/Profile/`

**Files (14 files):**
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulSelectableCell.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileStatsCard.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionsCard.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutTile.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/RipplesRow.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileBanner.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileLogoutPill.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileLabCard.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionCard.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/AssiduityGrid.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileCountersRow.swift`

- [ ] **Step 1: Inventory tokens**

```bash
grep -rn "Color\.pebbles\|Color\.ripple" apps/ios/Pebbles/Features/Profile --include="*.swift"
```

- [ ] **Step 2: Apply mechanical mappings**

- [ ] **Step 3: Walk per-call-site decisions WITH USER**

Profile is where `pebblesListRow` is concentrated (rows, lists, settings sheets). The spec says inline locally — for each call site, propose either:
- `Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(named: "SystemMuted") ?? .systemGray5 : .white })` (mirrors today's recipe with the new token), or
- A different mapping that fits the site better.

**PAUSE for user confirmation per call site.** If a clear shared shape emerges across many of these, surface it — we may want a tiny shared helper, otherwise stay inline.

- [ ] **Step 4: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -10
```

- [ ] **Step 5: Verify Xcode previews**

Open each file with a `#Preview` in Xcode. **PAUSE for user visual confirmation.**

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile
git commit -m "$(cat <<'EOF'
quality(ios): migrate Profile feature to new palette

Inlines pebblesListRow recipes locally per spec. No behavior change.

Refs #456

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Migrate `Features/Shared/Ripples/`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Shared/Ripples/RippleBadge.swift`

- [ ] **Step 1: Inventory tokens**

```bash
grep -n "Color\.pebbles\|Color\.ripple" apps/ios/Pebbles/Features/Shared/Ripples/RippleBadge.swift
```

This file is the primary consumer of the `Color.ripple*` tokens. Expect mixed mechanical + per-call-site work.

- [ ] **Step 2: Apply mechanical mappings (`rippleActive` → `accent.primary`)**

- [ ] **Step 3: Walk per-call-site decisions WITH USER**

`rippleDefault` and `rippleInactive` need explicit decisions — propose candidates from the table, **PAUSE for confirmation.**

- [ ] **Step 4: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -10
```

- [ ] **Step 5: Verify the RippleBadge preview, plus the PathBottomBar preview (where RippleBadge is consumed)**

**PAUSE for user visual confirmation.**

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Shared
git commit -m "$(cat <<'EOF'
quality(ios): migrate Shared ripples to new palette

Refs #456

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Migrate `Features/Welcome/`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Welcome/WelcomeCarousel.swift`
- Modify: `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift`
- Modify: `apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift`

- [ ] **Step 1: Inventory tokens**

```bash
for f in apps/ios/Pebbles/Features/Welcome/WelcomeCarousel.swift \
         apps/ios/Pebbles/Features/Welcome/WelcomeView.swift \
         apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift; do
  echo "=== $f ==="
  grep -n "Color\.pebbles\|Color\.ripple" "$f"
done
```

- [ ] **Step 2: Apply mechanical mappings**

- [ ] **Step 3: Walk per-call-site decisions WITH USER**

**PAUSE for confirmation.**

- [ ] **Step 4: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -10
```

- [ ] **Step 5: Verify Xcode previews**

**PAUSE for user visual confirmation.**

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome
git commit -m "$(cat <<'EOF'
quality(ios): migrate Welcome feature to new palette

Refs #456

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Remove legacy color tokens (final cleanup)

**Files:**
- Delete: `apps/ios/Pebbles/Theme/Color+Pebbles.swift`
- Delete: 11 legacy colorset folders in `apps/ios/Pebbles/Resources/Assets.xcassets/`:
  - `Background.colorset`
  - `Foreground.colorset`
  - `Surface.colorset`
  - `SurfaceAlt.colorset`
  - `Muted.colorset`
  - `MutedForeground.colorset`
  - `Border.colorset`
  - `AccentSoft.colorset`
  - `RippleDefault.colorset`
  - `RippleActive.colorset`
  - `RippleInactive.colorset`

(`AccentColor.colorset` is preserved — see Task 3.)

- [ ] **Step 1: Verify zero remaining `Color.pebbles*` / `Color.ripple*` references**

```bash
grep -rn "Color\.pebbles\|Color\.ripple" apps/ios/Pebbles --include="*.swift"
```

Expected: zero results. If there are any, STOP and finish migrating those sites first (do not delete the source file yet).

- [ ] **Step 2: Verify zero remaining `Color("Background"|"Foreground"|...)` direct references**

```bash
grep -rn 'Color("\(Background\|Foreground\|Surface\|SurfaceAlt\|Muted\|MutedForeground\|Border\|AccentSoft\|Ripple[A-Za-z]*\)")' apps/ios/Pebbles --include="*.swift"
```

Expected: zero results.

- [ ] **Step 3: Delete the Swift façade**

```bash
rm apps/ios/Pebbles/Theme/Color+Pebbles.swift
```

- [ ] **Step 4: Delete the 11 legacy colorset folders**

```bash
rm -rf apps/ios/Pebbles/Resources/Assets.xcassets/Background.colorset \
       apps/ios/Pebbles/Resources/Assets.xcassets/Foreground.colorset \
       apps/ios/Pebbles/Resources/Assets.xcassets/Surface.colorset \
       apps/ios/Pebbles/Resources/Assets.xcassets/SurfaceAlt.colorset \
       apps/ios/Pebbles/Resources/Assets.xcassets/Muted.colorset \
       apps/ios/Pebbles/Resources/Assets.xcassets/MutedForeground.colorset \
       apps/ios/Pebbles/Resources/Assets.xcassets/Border.colorset \
       apps/ios/Pebbles/Resources/Assets.xcassets/AccentSoft.colorset \
       apps/ios/Pebbles/Resources/Assets.xcassets/RippleDefault.colorset \
       apps/ios/Pebbles/Resources/Assets.xcassets/RippleActive.colorset \
       apps/ios/Pebbles/Resources/Assets.xcassets/RippleInactive.colorset
```

- [ ] **Step 5: Verify `AccentColor.colorset` still exists**

```bash
test -d apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset && echo "OK: AccentColor preserved"
```

Expected: `OK: AccentColor preserved`.

- [ ] **Step 6: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -20
```

Expected: `** BUILD SUCCEEDED **`. Any failure here means a stale reference slipped through — fix and rebuild before committing.

- [ ] **Step 7: Smoke test in simulator (light + dark)**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -5
```

Then run the app in the simulator and walk: Welcome → Onboarding → Auth → Path (timeline, pebble detail, pebble read) → Profile (collections, settings sheet, ripples row) → Lab. Toggle Appearance to Dark in the simulator (`Features → Toggle Appearance`, `⇧⌘A`). **PAUSE for user visual confirmation across both schemes.**

- [ ] **Step 8: Commit**

```bash
git add -A apps/ios
git commit -m "$(cat <<'EOF'
quality(ios): remove legacy color tokens

Deletes Pebbles/Theme/Color+Pebbles.swift and 11 legacy colorsets
(Background, Foreground, Surface, SurfaceAlt, Muted, MutedForeground,
Border, AccentSoft, RippleDefault, RippleActive, RippleInactive).
AccentColor.colorset is preserved as an alias of AccentPrimary so
SwiftUI's default tint and AppIcon tinting stay correct.

Closes #456

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: Open the PR

- [ ] **Step 1: Confirm labels + milestone with user**

Per the spec's PR-metadata section:
- Proposed labels: `quality`, `ios`, `ui` (swap issue's `feat` → `quality`).
- Milestone: `M32 · iOS Quality` (inherited).

**PAUSE — ask the user to confirm or override.**

- [ ] **Step 2: Push the branch**

```bash
git push -u origin quality/456-ios-colors-refacto
```

- [ ] **Step 3: Open the PR**

```bash
gh pr create \
  --title "quality(ios): refactor colors system" \
  --label quality --label ios --label ui \
  --milestone "M32 · iOS Quality" \
  --body "$(cat <<'EOF'
Resolves #456

## Summary
- Introduces `Color.system` (4 primitives) and `Color.accent` (6 tiers) palettes, mirroring `EmotionPalette`.
- Migrates every consumer of legacy `Color.pebbles*` and `Color.ripple*` tokens across all features, one feature per commit, reviewed visually in Xcode previews.
- Removes the legacy Swift façade and 11 legacy colorsets. `AccentColor.colorset` is preserved (re-tuned to `#C07A7A`) for Apple's tooling.

## Commit map
- `feat(ios)`: foundation — 10 new colorsets, `Palettes.swift`, `ColorTokensPreview.swift`, retuned `AccentColor`.
- `quality(ios)`: migrate shared components — `Pebbles/Components/*` + a doc-comment in `EmotionPaletteService`.
- `quality(ios)`: migrate `Glyph`, `Lab`, `Onboarding`, `Path`, `Profile`, `Shared/Ripples`, `Welcome` (one commit each).
- `quality(ios)`: remove legacy tokens.

## Test plan
- [ ] Build green: `xcodebuild -scheme Pebbles build`
- [ ] No residual references: `grep -rn "Color\.pebbles\|Color\.ripple" apps/ios/Pebbles --include="*.swift"` returns nothing
- [ ] No residual asset refs by string: `grep -rn 'Color("\(Background\|Foreground\|Surface\|SurfaceAlt\|Muted\|MutedForeground\|Border\|AccentSoft\|Ripple[A-Za-z]*\)")' apps/ios/Pebbles --include="*.swift"` returns nothing
- [ ] Smoke in simulator, light + dark: Welcome → Onboarding → Auth → Path → Profile → Lab
- [ ] `ColorTokensPreview` renders all 10 tiers in both schemes

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 4: Confirm the PR URL with the user**

Output the PR URL returned by `gh pr create` so the user can review.

---

## Self-review notes (already applied)

- Sequence refined to add a "shared components" commit (Task 6) before Auth (Task 7) because Auth has no direct color refs of its own.
- `Features/PebbleMedia/` skipped (zero refs).
- `Services/EmotionPaletteService.swift` doc-comment update folded into Task 6.
- Mechanical mapping reference is centralized once, not repeated in every task (engineer reads it before starting Tasks 6–14).
- All file lists captured from live grep at planning time; if the codebase has drifted since this plan was written, re-run the per-task inventory step first.
