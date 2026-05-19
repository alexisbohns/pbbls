# iOS Pebble Outline Frame Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Layer a per-(size × valence) silhouette behind every iOS site that renders a single pebble. Silhouette is fill-only, colored from the pebble's emotion palette via an intensity-driven rule.

**Architecture:** Wrapper-level frame. A new `PebbleOutlineBackdropView` renders one of 9 outline SVGs (3 sizes × 3 valences) underneath the existing `PebbleRenderView` inside a `ZStack` at each consumer site. The pebble render's geometry is untouched — it's scaled down (~0.74×) so it fits inside the larger backdrop viewBox. A new `PebbleOutlineGeometry` helper exposes the per-size scale + aspect ratio; a new `pebbleFrameColors(forIntensity:)` method on `EmotionPalette` is the single source of truth for the intensity → role mapping. Server compose pipeline, `render_version`, and backfill are all untouched.

**Tech Stack:** SwiftUI (iOS 17+), `SVGView`, Swift Testing (`@Suite`, `@Test`, `#expect`).

**Reference spec:** [`docs/superpowers/specs/2026-05-19-pebble-outline-frame-ios-design.md`](../specs/2026-05-19-pebble-outline-frame-ios-design.md) · [Issue #474](https://github.com/Bohns/pbbls/issues/474) · Parent issue #473.

**Pre-flight notes:**
- Spec acceptance line "No change under `packages/supabase/`" is intentionally softened in this plan: the `path_pebbles` RPC return shape is widened to include `positiveness`, which `PathPebbleRow` needs to pick the lowlight/neutral/highlight outline. The engine, compose pipeline, `render_version`, and backfill stay untouched — those are the spirit of the constraint.
- The `PebbleFrameColors` helper lives as an extension on `EmotionPalette` (not on a `Palettes` namespace — the existing `Palettes.swift` only declares `SystemPalette` and `AccentPalette` structs).

**Branching:** Before Task 1, ensure you are on a feature branch named `feat/474-ios-pebble-outline-frame` cut from updated `origin/main`. The spec commits (`61e5c12`, `f74c5b6`) currently sit on local `main` ahead of origin — those should already be pushed (via PR or directly) before this branch is cut so the spec is on the remote.

---

## File map

**New files:**
- `apps/ios/Pebbles/Resources/Outlines/small-neutral.svg`
- `apps/ios/Pebbles/Resources/Outlines/small-lowlight.svg`
- `apps/ios/Pebbles/Resources/Outlines/small-highlight.svg`
- `apps/ios/Pebbles/Resources/Outlines/medium-neutral.svg`
- `apps/ios/Pebbles/Resources/Outlines/medium-lowlight.svg`
- `apps/ios/Pebbles/Resources/Outlines/medium-highlight.svg`
- `apps/ios/Pebbles/Resources/Outlines/large-neutral.svg`
- `apps/ios/Pebbles/Resources/Outlines/large-lowlight.svg`
- `apps/ios/Pebbles/Resources/Outlines/large-highlight.svg`
- `apps/ios/Pebbles/Features/Path/Render/PebbleOutlineGeometry.swift`
- `apps/ios/Pebbles/Features/Path/Render/PebbleOutlineBackdropView.swift`
- `apps/ios/PebblesTests/PebbleOutlineGeometryTests.swift`
- `apps/ios/PebblesTests/PebbleOutlineBackdropViewTests.swift`
- `apps/ios/PebblesTests/PebbleFrameColorsTests.swift`
- `apps/ios/PebblesTests/OutlineAssetSentinelTests.swift`
- `packages/supabase/supabase/migrations/<timestamp>_path_pebbles_positiveness.sql`

**Modified files:**
- `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift` — add `PebbleFrameColors` + `pebbleFrameColors(forIntensity:)` extension method.
- `apps/ios/Pebbles/Features/Path/Models/Pebble.swift` — add `positiveness: Int`, computed `valence: Valence`.
- `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift` — wrap thumbnail in ZStack with backdrop; remove the rounded-rectangle chrome (`RoundedRectangle(cornerRadius: 12).fill(thumbnailFill)` at line 76–77).
- `apps/ios/Pebbles/Components/PebbleRow.swift` — same ZStack wrap; the file currently has no chrome to remove (it sits the SVG directly in a 40×40 frame), so this is purely additive.
- `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift` — accept `(size, valence, fillHex)` props; wrap existing animation body in a ZStack with the backdrop springing in first.
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift` — pass the new backdrop props through to `PebbleAnimatedRenderView`.
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift` — add `positiveness` to SELECT columns (line 163).
- `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift` — add `positiveness` to SELECT columns (line 164).
- `apps/ios/PebblesTests/PebbleDecodingTests.swift` — extend with `positiveness` decoding.
- `packages/supabase/types/database.ts` — regenerated after migration.

---

## Task 1: Add the 9 outline SVG assets

**Files:**
- Create: `apps/ios/Pebbles/Resources/Outlines/small-neutral.svg`
- Create: `apps/ios/Pebbles/Resources/Outlines/small-lowlight.svg`
- Create: `apps/ios/Pebbles/Resources/Outlines/small-highlight.svg`
- Create: `apps/ios/Pebbles/Resources/Outlines/medium-neutral.svg`
- Create: `apps/ios/Pebbles/Resources/Outlines/medium-lowlight.svg`
- Create: `apps/ios/Pebbles/Resources/Outlines/medium-highlight.svg`
- Create: `apps/ios/Pebbles/Resources/Outlines/large-neutral.svg`
- Create: `apps/ios/Pebbles/Resources/Outlines/large-lowlight.svg`
- Create: `apps/ios/Pebbles/Resources/Outlines/large-highlight.svg`

Each file contents copied verbatim from issue #473's "Assets" section. Sentinel fill is `#FF00FF` — keep it literally; the backdrop view does `replacingOccurrences(of: "#FF00FF", ...)` at construction time.

- [ ] **Step 1.1: Create `small-neutral.svg`**

```svg
<svg width="337" height="270" viewBox="0 0 337 270" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M97.3622 12.4435C113.519 11.1895 131.229 12.7257 148.37 15.4786C182.876 21.0204 221.693 32.6028 254.815 45.1075C303.81 63.6047 322.089 110.938 320.947 149.9C319.845 187.515 299.786 234.141 252.429 248.683C224.933 257.125 179.693 260.315 141.135 258.511C121.386 257.587 100.917 255.258 83.4101 250.674C74.6831 248.389 65.3402 245.2 56.7773 240.521C48.631 236.069 37.7723 228.412 30.7812 215.548C23.5292 202.204 20.0318 185.841 18.1864 171.955C16.2093 157.077 15.5939 140.681 16.2558 124.806C16.9136 109.026 18.8833 92.5355 22.6376 77.7101C25.9793 64.514 32.2854 46.077 45.9101 32.8653L46.6523 32.1573C62.3173 17.4633 82.8022 13.5735 97.3622 12.4435Z" fill="#FF00FF"/>
</svg>
```

- [ ] **Step 1.2: Create `small-lowlight.svg`**

```svg
<svg width="337" height="270" viewBox="0 0 337 270" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M146.551 12.7088C159.529 11.0948 171.594 15.4082 180.409 23.3211L195.077 22.1248L195.96 22.0623C204.826 21.5239 214.481 23.6981 222.989 29.5926L223.835 30.1941L226.607 32.2312C235.034 38.4691 256.511 54.7339 276.799 73.1834C288.248 83.5945 300.616 95.8604 310.091 108.224C314.807 114.378 319.764 121.78 323.377 129.977C326.757 137.642 330.839 150.202 328.109 164.741C324.426 184.351 311.252 198.593 302.792 206.531C293.069 215.653 281.643 223.614 271.554 229.928C251.212 242.659 231.072 251.911 227.414 253.566C222.275 255.891 217.029 257.062 211.852 257.307C206.731 257.55 169.399 259.175 130.463 256.396C111.178 255.02 89.7173 252.451 71.0162 247.427C61.6677 244.915 51.6122 241.427 42.4029 236.323C33.4935 231.385 22.3553 223.265 15.0962 210.066C11.0955 202.791 9.9809 195.827 9.55717 192.531C9.04207 188.523 8.96094 184.701 9.01225 181.554C9.11549 175.227 9.826 168.343 10.7378 161.776C12.5879 148.453 15.7999 132.735 19.4673 117.782C23.1304 102.847 27.5204 87.5647 31.9732 75.1971C34.1533 69.1418 36.7139 62.7546 39.6177 57.1561C41.046 54.4023 43.0655 50.8425 45.7447 47.3113C47.7125 44.7178 53.39 37.5087 63.313 33.0125L64.647 32.4197C78.5745 26.3492 96.7914 21.9877 110.46 19.1219C125.583 15.9508 139.467 13.7587 145.43 12.8631L146.551 12.7088Z" fill="#FF00FF"/>
</svg>
```

- [ ] **Step 1.3: Create `small-highlight.svg`**

```svg
<svg width="337" height="270" viewBox="0 0 337 270" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M68.5639 30.411C119.595 -9.09251 190.185 1.41185 238.475 24.8407C263.947 37.199 287.946 55.1105 304.913 77.6718C322.036 100.439 333.431 130.217 327.366 163.168C320.974 197.885 296.709 222.07 271.481 237.247C245.964 252.597 215.027 261.687 184.803 265.243C154.523 268.806 122.162 267.145 93.3139 258.445C64.8656 249.866 35.3119 232.902 18.8842 202.432C10.6261 187.114 8.71482 170.004 9.03262 155.767C9.3646 140.896 12.2094 125.424 16.7152 110.774C25.4057 82.5193 42.4245 50.6459 68.5639 30.411Z" fill="#FF00FF"/>
</svg>
```

- [ ] **Step 1.4: Create `medium-neutral.svg`**

```svg
<svg width="350" height="350" viewBox="0 0 350 350" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M160.977 25.4278C185.242 24.2104 210.523 25.5265 232.558 30.211C243.579 32.5541 254.856 35.9628 265.151 40.9913C274.854 45.7303 285.948 53.0111 294.278 64.3555L295.076 65.4659L295.937 66.7022C314.024 92.8837 332.275 132.307 339.131 171.214C345.655 208.237 344.202 263.382 295.876 292.503C278.885 302.741 256.605 310.098 234.747 315.138C212.235 320.329 187.204 323.732 163.047 324.697C139.247 325.648 114.255 324.318 92.6133 318.826C73.2455 313.911 44.896 302.665 32.3662 274.502C22.1241 251.481 12.1488 217.402 8.4844 183.072C5.03301 150.737 5.99671 107.727 28.9834 75.5616C43.9673 54.5946 69.3051 43.5544 89.3565 37.3399C111.393 30.5101 136.664 26.6476 160.977 25.4278Z" fill="#FF00FF"/>
</svg>
```

- [ ] **Step 1.5: Create `medium-lowlight.svg`**

```svg
<svg width="350" height="350" viewBox="0 0 350 350" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M138.7 15.8184C149.697 14.2297 159.726 16.7951 167.804 22.0078L170.274 21.1026C178.135 18.2245 187.363 17.4576 196.681 20.0908C202.399 21.7065 228.036 29.255 255.043 42.6885C268.555 49.4097 283.566 58.166 296.889 69.1973C309.953 80.0152 323.783 94.9871 331.235 114.734C338.54 134.093 333.831 152.577 328.933 164.624C323.768 177.33 315.773 189.419 307.434 200.106C290.61 221.671 267.307 243.912 244.411 263.206C221.301 282.68 196.983 300.508 177.062 313.08C167.272 319.259 157.477 324.848 148.852 328.667C144.735 330.49 139.187 332.68 133.127 333.926C130.096 334.549 125.413 335.264 119.885 334.902C114.556 334.554 105.204 332.98 96.2645 326.027C87.2483 319.016 80.5926 309.343 76.2518 302.376C71.2946 294.42 66.4588 285.149 61.9129 275.492C52.7878 256.107 43.685 232.713 36.0418 209.753C28.4332 186.896 21.8526 163.216 18.1942 143.308C16.3923 133.502 15.0381 123.241 15.0008 113.838C14.9822 109.142 15.2781 103.481 16.5194 97.6221C17.628 92.3892 20.1454 83.7759 26.743 75.7197C64.6552 28.2207 124.892 17.8133 138.7 15.8184Z" fill="#FF00FF"/>
</svg>
```

- [ ] **Step 1.6: Create `medium-highlight.svg`**

```svg
<svg width="350" height="350" viewBox="0 0 350 350" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M97.8467 32.5016C128.105 9.47221 163.091 9.3764 192.653 19.2653C221.221 28.8214 247.369 48.1735 268.492 69.8541C289.889 91.8165 308.411 118.493 320.752 145.911C332.746 172.558 340.821 204.254 335.151 234.538C328.626 269.39 303.919 293.468 278.53 308.484C252.783 323.713 221.57 332.73 191.075 336.257C160.519 339.792 127.89 338.141 98.8242 329.522C70.2281 321.043 40.3433 304.247 23.667 273.835C12.1266 252.788 10.8914 228.124 12.6465 207.547C14.4889 185.946 20.1354 163.153 27.7148 141.812C35.3337 120.36 45.3502 99.127 56.7529 80.611C67.8393 62.6086 81.6826 44.8041 97.8467 32.5016Z" fill="#FF00FF"/>
</svg>
```

- [ ] **Step 1.7: Create `large-neutral.svg`**

```svg
<svg width="335" height="400" viewBox="0 0 335 400" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M93.0089 29.7421C116.691 23.3096 144.015 23.8359 174.33 28.831C202.126 33.411 225.944 37.8012 245.01 43.7792C264.324 49.835 283.411 58.8723 297.763 75.788C312.229 92.8385 317.666 112.913 320.139 132.025C322.498 150.26 322.647 172.283 322.651 196.678C322.663 196.848 322.677 197.027 322.689 197.214C322.777 198.654 322.873 200.597 322.935 202.973C323.057 207.71 323.048 214.252 322.56 222.02C321.599 237.312 318.714 258.595 310.519 280.552C302.278 302.631 288.119 326.783 263.882 345.361C239.242 364.248 207.264 375 167.826 374.999C138.151 374.999 112.282 373.184 90.839 367.289C68.2295 361.073 48.314 349.675 34.4357 329.573C21.5212 310.867 17.0242 289.045 14.9943 268.829C12.9551 248.521 12.9998 224.251 13.0001 198.126C13.0001 175.835 13.2682 153.146 15.7736 132.65C18.256 112.342 23.317 90.3996 35.4239 71.3622C48.4437 50.8897 67.8158 36.5852 93.0089 29.7421Z" fill="#FF00FF"/>
</svg>
```

- [ ] **Step 1.8: Create `large-lowlight.svg`**

```svg
<svg width="335" height="400" viewBox="0 0 335 400" fill="none" xmlns="http://www.w3.org/2000/svg">
<path fill-rule="evenodd" clip-rule="evenodd" d="M61.7568 21.1192C69.6811 19.7521 77.8089 19.847 84.6445 20.3008C94.0955 20.9283 101.107 24.7547 102.613 25.5518C104.793 26.7052 106.733 27.9297 108.288 28.9756C114.858 26.955 122.565 26.2419 131.302 28.1319L132.488 28.4034L134.271 28.8399C171.792 38.1892 214.684 58.5779 248.978 80.4707C266.559 91.6949 283.186 104.137 296.352 116.855C307.828 127.941 323.021 145.12 327.101 166.3C328.312 172.588 328.019 178.312 327.816 181.2C327.564 184.79 327.07 188.449 326.512 191.858C325.389 198.716 323.657 206.661 321.616 214.942C317.507 231.616 311.611 251.797 305.071 271.559C298.561 291.232 291.118 311.388 283.793 327.767C280.167 335.874 276.182 343.935 271.964 350.825C269.855 354.269 267.285 358.088 264.243 361.71C261.534 364.935 256.667 370.185 249.52 374.228C225.129 388.023 203.213 373.414 199.777 371.178C194.067 367.461 187.089 361.453 184.127 359.04C175.611 352.102 167.266 346.213 156.273 342.988C147.185 340.322 140.55 335.406 136.033 330.943C128.679 332.547 120.934 334.005 113.322 335.13C103.14 336.634 91.729 337.77 81.04 337.484C75.6858 337.341 69.4914 336.821 63.2129 335.377C57.7486 334.12 49.8548 331.636 42.2539 326.14C33.173 319.955 28.4786 311.828 26.5371 308.189C23.8676 303.184 22.0123 298.041 20.6816 293.728C17.984 284.983 15.9526 274.686 14.3711 264.266C11.161 243.115 9.08482 216.543 8.32519 189.745C7.56456 162.912 8.09016 134.661 10.4131 110.157C11.5725 97.9272 13.2399 85.9723 15.6269 75.2696C17.8511 65.2973 21.3496 53.303 27.624 43.0332L28.1309 42.2227C38.8877 25.4141 56.5983 22.0092 61.7568 21.1192ZM40.9414 325.159C40.9596 325.173 40.9787 325.186 40.9971 325.2C40.8142 325.059 40.6295 324.918 40.4473 324.773L40.9414 325.159Z" fill="#FF00FF"/>
</svg>
```

- [ ] **Step 1.9: Create `large-highlight.svg`**

```svg
<svg width="335" height="400" viewBox="0 0 335 400" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M112.475 13.0536C136.045 6.94066 159.376 10.815 178.886 17.4863C198.628 24.2372 217.47 34.8634 233.99 46.2275C266.665 68.7036 295.702 97.8723 310.859 118.67C325.014 138.092 329.182 162.297 329.844 182.729C330.538 204.162 327.545 227.152 322.455 248.891C317.337 270.749 309.791 292.66 300.536 312.073C291.534 330.958 279.743 349.959 264.998 363.827C237.54 389.652 202.944 393.599 172.65 387.407C143.231 381.394 114.958 365.604 91.3253 346.883C67.4115 327.938 45.763 304.038 30.1798 278.646C15.0637 254.016 3.09834 223.746 5.25009 193.099C6.87653 169.933 15.0518 132.698 29.6085 98.9453C36.9651 81.8874 46.6659 64.0256 59.2921 48.8788C71.8049 33.8682 89.2781 19.0698 112.475 13.0536Z" fill="#FF00FF"/>
</svg>
```

- [ ] **Step 1.10: Run xcodegen + build to confirm bundle pickup**

`project.yml` declares `sources: - path: Pebbles` for the app target. xcodegen classifies SVG files under that path as bundle resources automatically (same as the existing `.riv` files in `Resources/`). No `project.yml` edit needed.

```bash
cd apps/ios && npm run generate --workspace=@pbbls/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO | tail -30
```

Expected: `BUILD SUCCEEDED`. Confirm the build phase log shows the 9 SVGs being copied to the bundle's `Resources` folder. If they're not picked up, add the folder to `project.yml`'s target sources explicitly:

```yaml
targets:
  Pebbles:
    sources:
      - path: Pebbles
      - path: Pebbles/Resources/Outlines
        buildPhase: resources
```

- [ ] **Step 1.11: Commit**

```bash
git add apps/ios/Pebbles/Resources/Outlines/
git commit -m "feat(ios): add 9 pebble outline svg assets"
```

---

## Task 2: `PebbleOutlineGeometry` helper + tests

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Render/PebbleOutlineGeometry.swift`
- Create: `apps/ios/PebblesTests/PebbleOutlineGeometryTests.swift`

`PebbleOutlineGeometry` exposes per-size scale (pebbleViewBox/outlineViewBox) and per-size aspect ratio (outlineWidth/outlineHeight). Keyed by `ValenceSizeGroup` (small/medium/large) — the existing type. Pure constants, no SVG parsing at runtime.

ViewBox constants:
- Outline: small 337×270, medium 350×350, large 335×400.
- Pebble (composed SVG canvas at current `render_version`): small 250×200, medium 260×260, large 260×310. These are the existing canvas dimensions used by the server compositor; confirm against `packages/supabase/supabase/functions/_shared/engine/layout.ts` when implementing — if they have changed, update the constants. (The current layout.ts has these dims as of 2026-05-19; if a later compose pipeline change has shifted them, treat the engine value as source of truth.)

- [ ] **Step 2.1: Write the failing test**

`apps/ios/PebblesTests/PebbleOutlineGeometryTests.swift`:

```swift
import Testing
@testable import Pebbles

@Suite
struct PebbleOutlineGeometryTests {

    @Test func pebbleScaleSmall() {
        // outline 337×270; pebble 250×200. Linear scale = 250/337 ≈ 0.742.
        #expect(abs(PebbleOutlineGeometry.pebbleScale(for: .small) - 0.742) < 0.005)
    }

    @Test func pebbleScaleMedium() {
        // outline 350×350; pebble 260×260. 260/350 ≈ 0.743.
        #expect(abs(PebbleOutlineGeometry.pebbleScale(for: .medium) - 0.743) < 0.005)
    }

    @Test func pebbleScaleLarge() {
        // outline 335×400; pebble 260×310. 260/335 ≈ 0.776.
        #expect(abs(PebbleOutlineGeometry.pebbleScale(for: .large) - 0.776) < 0.005)
    }

    @Test func aspectRatioSmall() {
        // 337/270 ≈ 1.248.
        #expect(abs(PebbleOutlineGeometry.aspectRatio(for: .small) - (337.0 / 270.0)) < 0.001)
    }

    @Test func aspectRatioMedium() {
        #expect(abs(PebbleOutlineGeometry.aspectRatio(for: .medium) - 1.0) < 0.001)
    }

    @Test func aspectRatioLarge() {
        // 335/400 ≈ 0.8375.
        #expect(abs(PebbleOutlineGeometry.aspectRatio(for: .large) - (335.0 / 400.0)) < 0.001)
    }

    @Test func pebbleAndOutlineAspectsMatchWithin0_1Percent() {
        // The pebble's viewBox aspect must match the outline's per size,
        // otherwise the scaleEffect approach would distort. Spec invariant.
        for (size, outline, pebble) in [
            (ValenceSizeGroup.small,  (337.0, 270.0), (250.0, 200.0)),
            (ValenceSizeGroup.medium, (350.0, 350.0), (260.0, 260.0)),
            (ValenceSizeGroup.large,  (335.0, 400.0), (260.0, 310.0)),
        ] {
            let outlineAspect = outline.0 / outline.1
            let pebbleAspect  = pebble.0  / pebble.1
            let drift = abs(outlineAspect - pebbleAspect) / outlineAspect
            #expect(drift < 0.001, "aspect drift for \(size): \(drift)")
        }
    }
}
```

- [ ] **Step 2.2: Run test to verify it fails**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleOutlineGeometryTests test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -20
```

Expected: build fails with "Cannot find 'PebbleOutlineGeometry' in scope".

- [ ] **Step 2.3: Implement `PebbleOutlineGeometry`**

`apps/ios/Pebbles/Features/Path/Render/PebbleOutlineGeometry.swift`:

```swift
import CoreGraphics

/// Layout constants for composing `PebbleOutlineBackdropView` underneath
/// `PebbleRenderView`. The outline viewBox is intentionally ~1.35× the
/// pebble's viewBox so the silhouette frames the artwork with ~12–13%
/// margin per edge. `SVGView` scales each child to fill its proposed
/// frame, so the pebble must be down-scaled explicitly to land at the
/// correct relative size inside the backdrop.
enum PebbleOutlineGeometry {

    private static let outlineSize: [ValenceSizeGroup: CGSize] = [
        .small:  CGSize(width: 337, height: 270),
        .medium: CGSize(width: 350, height: 350),
        .large:  CGSize(width: 335, height: 400),
    ]

    /// Pebble composed-SVG canvas dims per size. Mirrors
    /// `packages/supabase/supabase/functions/_shared/engine/layout.ts`.
    private static let pebbleSize: [ValenceSizeGroup: CGSize] = [
        .small:  CGSize(width: 250, height: 200),
        .medium: CGSize(width: 260, height: 260),
        .large:  CGSize(width: 260, height: 310),
    ]

    /// Linear scale factor to apply to `PebbleRenderView` so it fits
    /// inside the larger backdrop viewBox. Computed from the per-size
    /// viewBox width ratio; per-axis match is guaranteed by the matched
    /// aspect ratios (see `PebbleOutlineGeometryTests`).
    static func pebbleScale(for size: ValenceSizeGroup) -> Double {
        guard let outline = outlineSize[size], let pebble = pebbleSize[size] else {
            return 1
        }
        return Double(pebble.width / outline.width)
    }

    /// Aspect ratio (`width / height`) of the outline viewBox for the
    /// outer `ZStack` to adopt via `.aspectRatio(_:contentMode:)`.
    static func aspectRatio(for size: ValenceSizeGroup) -> Double {
        guard let outline = outlineSize[size] else { return 1 }
        return Double(outline.width / outline.height)
    }
}
```

- [ ] **Step 2.4: Run test to verify it passes**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleOutlineGeometryTests test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: all 7 tests pass.

- [ ] **Step 2.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Render/PebbleOutlineGeometry.swift apps/ios/PebblesTests/PebbleOutlineGeometryTests.swift
git commit -m "feat(ios): add pebble outline geometry helper"
```

---

## Task 3: `PebbleOutlineBackdropView` + tests

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Render/PebbleOutlineBackdropView.swift`
- Create: `apps/ios/PebblesTests/OutlineAssetSentinelTests.swift`
- Create: `apps/ios/PebblesTests/PebbleOutlineBackdropViewTests.swift`

The view takes `(size: ValenceSizeGroup, polarity: ValencePolarity, fillHex: String)`, loads the matching SVG from the bundle, swaps the `#FF00FF` sentinel for `fillHex`, and renders via `SVGView`. Accessibility-hidden — the parent pebble carries the label.

Asset filename convention: `\(size.rawValue)-\(polarity.rawValue).svg` (e.g. `small-neutral.svg`). `ValenceSizeGroup.rawValue` is `"small"` etc.; `ValencePolarity.rawValue` is `"lowlight"` / `"neutral"` / `"highlight"`. Both already match the asset filenames.

- [ ] **Step 3.1: Write the asset-sentinel test (it should pass against Task 1's assets)**

`apps/ios/PebblesTests/OutlineAssetSentinelTests.swift`:

```swift
import Testing
import Foundation

@Suite
struct OutlineAssetSentinelTests {

    private static let names: [String] = [
        "small-neutral",   "small-lowlight",   "small-highlight",
        "medium-neutral",  "medium-lowlight",  "medium-highlight",
        "large-neutral",   "large-lowlight",   "large-highlight",
    ]

    @Test("Each outline asset is bundled and parseable")
    func assetsExist() throws {
        for name in Self.names {
            let url = Bundle.main.url(forResource: name, withExtension: "svg")
            #expect(url != nil, "missing asset: \(name).svg")
        }
    }

    @Test("Each outline contains exactly one #FF00FF sentinel")
    func sentinelExactlyOnce() throws {
        for name in Self.names {
            let url = try #require(Bundle.main.url(forResource: name, withExtension: "svg"))
            let body = try String(contentsOf: url, encoding: .utf8)
            let count = body.components(separatedBy: "#FF00FF").count - 1
            #expect(count == 1, "\(name).svg expected 1 sentinel, got \(count)")
        }
    }
}
```

- [ ] **Step 3.2: Run sentinel test to verify it passes**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/OutlineAssetSentinelTests test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: 2 tests pass. If `assetsExist` fails, the resources aren't being bundled — re-check Task 1 step 1.10.

- [ ] **Step 3.3: Implement `PebbleOutlineBackdropView`**

`apps/ios/Pebbles/Features/Path/Render/PebbleOutlineBackdropView.swift`:

```swift
import SwiftUI
import SVGView
import os

/// Renders a per-(size × polarity) pebble silhouette behind the
/// composed pebble artwork. Fill-only — no stroke. Color injection
/// follows the same sentinel-swap pattern as `PebbleRenderView`: the
/// asset ships with `fill="#FF00FF"` and the view replaces it at
/// construction time.
///
/// The view fills its proposed frame via `.aspectRatio(.fit)`;
/// consumers compose it inside a `ZStack` and apply the matching
/// `PebbleOutlineGeometry.aspectRatio(for:)` on the outer container so
/// the pebble + backdrop share a single on-screen rectangle.
struct PebbleOutlineBackdropView: View {
    let size: ValenceSizeGroup
    let polarity: ValencePolarity
    let fillHex: String

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-outline")

    private var coloredSvg: String? {
        let name = "\(size.rawValue)-\(polarity.rawValue)"
        guard let url = Bundle.main.url(forResource: name, withExtension: "svg"),
              let raw = try? String(contentsOf: url, encoding: .utf8) else {
            Self.logger.error("missing outline asset: \(name, privacy: .public).svg")
            return nil
        }
        // Trim 8-digit hex (#RRGGBBAA) to 6-digit for SVGView reliability.
        // Mirrors PathPebbleRow.glyphStrokeHex; SVGView fills assume opaque.
        let safeHex = fillHex.count == 9 ? String(fillHex.prefix(7)) : fillHex
        return raw.replacingOccurrences(of: "#FF00FF", with: safeHex)
    }

    var body: some View {
        Group {
            if let svg = coloredSvg {
                SVGView(string: svg)
                    .aspectRatio(contentMode: .fit)
            } else {
                Color.clear
            }
        }
        .accessibilityHidden(true)
    }
}

#Preview {
    HStack(spacing: 16) {
        PebbleOutlineBackdropView(size: .small,  polarity: .neutral,   fillHex: "#C07A7A")
        PebbleOutlineBackdropView(size: .medium, polarity: .lowlight,  fillHex: "#5C7AB8")
        PebbleOutlineBackdropView(size: .large,  polarity: .highlight, fillHex: "#D4A85E")
    }
    .frame(height: 200)
    .padding()
}
```

- [ ] **Step 3.4: Write the color-injection test**

`apps/ios/PebblesTests/PebbleOutlineBackdropViewTests.swift`:

```swift
import Testing
import Foundation
@testable import Pebbles

@Suite
struct PebbleOutlineBackdropViewTests {

    /// The view's `coloredSvg` is private. We test the same logic by
    /// loading the asset directly and running the swap — exercises the
    /// contract (sentinel + replacement), not the SwiftUI body.
    @Test func sentinelSwapProducesNoMagentaResidue() throws {
        let url = try #require(Bundle.main.url(forResource: "small-neutral", withExtension: "svg"))
        let raw = try String(contentsOf: url, encoding: .utf8)
        let swapped = raw.replacingOccurrences(of: "#FF00FF", with: "#C07A7A")
        #expect(!swapped.contains("#FF00FF"))
        #expect(swapped.contains("#C07A7A"))
    }

    @Test func eightDigitHexTrimsAlphaBeforeInjection() {
        let input = "#C07A7AFF"
        let trimmed = input.count == 9 ? String(input.prefix(7)) : input
        #expect(trimmed == "#C07A7A")
    }
}
```

- [ ] **Step 3.5: Run tests to verify they pass**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleOutlineBackdropViewTests test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: 2 tests pass.

- [ ] **Step 3.6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Render/PebbleOutlineBackdropView.swift apps/ios/PebblesTests/OutlineAssetSentinelTests.swift apps/ios/PebblesTests/PebbleOutlineBackdropViewTests.swift
git commit -m "feat(ios): add pebble outline backdrop view"
```

---

## Task 4: `PebbleFrameColors` + `EmotionPalette.pebbleFrameColors(forIntensity:)`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift`
- Create: `apps/ios/PebblesTests/PebbleFrameColorsTests.swift`

Single source of truth for the intensity → (stroke role, fill role) mapping. Intensity 3 → `(light, primary)`; intensity 1 or 2 → `(secondary, surface)`. Hex variants only (the SVG injection path is hex-based).

- [ ] **Step 4.1: Write the failing test**

`apps/ios/PebblesTests/PebbleFrameColorsTests.swift`:

```swift
import Testing
@testable import Pebbles

@Suite
struct PebbleFrameColorsTests {

    private let palette = EmotionPalette(
        primaryHex:   "#C07A7A",
        secondaryHex: "#9B5C5C",
        lightHex:     "#E8B8B8",
        surfaceHex:   "#F4DCDC"
    )!

    @Test func intensity3UsesLightStrokeAndPrimaryFill() {
        let colors = palette.pebbleFrameColors(forIntensity: 3)
        #expect(colors.strokeHex == "#E8B8B8")
        #expect(colors.fillHex   == "#C07A7A")
    }

    @Test func intensity2UsesSecondaryStrokeAndSurfaceFill() {
        let colors = palette.pebbleFrameColors(forIntensity: 2)
        #expect(colors.strokeHex == "#9B5C5C")
        #expect(colors.fillHex   == "#F4DCDC")
    }

    @Test func intensity1UsesSecondaryStrokeAndSurfaceFill() {
        let colors = palette.pebbleFrameColors(forIntensity: 1)
        #expect(colors.strokeHex == "#9B5C5C")
        #expect(colors.fillHex   == "#F4DCDC")
    }

    @Test func unexpectedIntensityFallsBackToSmallMediumRule() {
        // Defensive: clamp anything outside 1/2/3 to the small/medium rule.
        // DB CHECK guarantees 1–3; this is belt-and-braces for decode drift.
        let colors = palette.pebbleFrameColors(forIntensity: 99)
        #expect(colors.strokeHex == "#9B5C5C")
        #expect(colors.fillHex   == "#F4DCDC")
    }
}
```

- [ ] **Step 4.2: Run test to verify it fails**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleFrameColorsTests test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: build fails with "value of type 'EmotionPalette' has no member 'pebbleFrameColors'".

- [ ] **Step 4.3: Add `PebbleFrameColors` + extension to `EmotionPalette.swift`**

Append at the end of `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift` (after the closing `}` of `EmotionPalette`):

```swift
/// Pair of hex strings handed to the pebble render stack: `strokeHex`
/// is consumed by `PebbleRenderView` (replaces `currentColor` in the
/// composed SVG); `fillHex` is consumed by `PebbleOutlineBackdropView`
/// (replaces the `#FF00FF` sentinel in the outline SVG).
struct PebbleFrameColors: Equatable {
    let strokeHex: String
    let fillHex: String
}

extension EmotionPalette {

    /// Single source of truth for the intensity → role mapping.
    /// - intensity 3 (large): `light` stroke + `primary` fill (opaque body).
    /// - intensity 1 / 2:     `secondary` stroke + `surface` fill (alpha body).
    func pebbleFrameColors(forIntensity intensity: Int) -> PebbleFrameColors {
        switch intensity {
        case 3:
            return PebbleFrameColors(strokeHex: lightHex,     fillHex: primaryHex)
        default:
            return PebbleFrameColors(strokeHex: secondaryHex, fillHex: surfaceHex)
        }
    }
}
```

- [ ] **Step 4.4: Run test to verify it passes**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleFrameColorsTests test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: 4 tests pass.

- [ ] **Step 4.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift apps/ios/PebblesTests/PebbleFrameColorsTests.swift
git commit -m "feat(ios): add pebble frame colors helper on emotion palette"
```

---

## Task 5: Widen `path_pebbles` RPC to return `positiveness`

**Files:**
- Create: `packages/supabase/supabase/migrations/<timestamp>_path_pebbles_positiveness.sql`
- Modify: `packages/supabase/types/database.ts` (regenerated)

The existing RPC at `20260516000002_path_pebbles_created_at.sql` does not return `positiveness`. Drop and recreate following that migration's pattern; `pebbles.positiveness` already lives on the table.

- [ ] **Step 5.1: Create the migration**

```bash
cd packages/supabase && npm run db:migration:new -- path_pebbles_positiveness
```

This creates an empty file at `packages/supabase/supabase/migrations/<timestamp>_path_pebbles_positiveness.sql`. Populate it with:

```sql
-- Migration: path_pebbles + positiveness
-- Widens the path_pebbles RPC return shape to include positiveness so
-- iOS can pick the matching pebble-outline silhouette per (size × polarity).
--
-- No engine change, no render_version bump, no backfill. The compose
-- pipeline and existing render_svg rows are untouched — this only
-- exposes a column already on public.pebbles.

drop function if exists public.path_pebbles();
create function public.path_pebbles()
returns table (
  id uuid,
  name text,
  happened_at timestamptz,
  created_at timestamptz,
  intensity smallint,
  positiveness smallint,
  render_svg text,
  emotion jsonb,
  first_snap_path text
)
language sql
security invoker
stable
set search_path = public
as $$
  select
    p.id,
    p.name,
    p.happened_at,
    p.created_at,
    p.intensity,
    p.positiveness,
    p.render_svg,
    case
      when e.id is null then null
      else jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name)
    end as emotion,
    (
      select s.storage_path
      from public.snaps s
      where s.pebble_id = p.id
      order by s.sort_order asc nulls last, s.created_at asc
      limit 1
    ) as first_snap_path
  from public.pebbles p
  left join public.emotions e on e.id = p.emotion_id
  where p.user_id = auth.uid()
  order by p.happened_at desc;
$$;

grant execute on function public.path_pebbles() to authenticated;
```

- [ ] **Step 5.2: Apply migration locally + regenerate types**

```bash
cd packages/supabase && npm run db:reset && npm run db:types
```

Expected: `db:reset` runs all migrations cleanly; `db:types` rewrites `types/database.ts`. If local Supabase isn't running, follow `packages/supabase/CLAUDE.md`'s remote fallback: `npm run db:types:remote`.

- [ ] **Step 5.3: Verify generated types include positiveness on path_pebbles**

```bash
grep -A 20 "path_pebbles:" packages/supabase/types/database.ts | head -30
```

Expected: the `Returns:` shape lists `positiveness: number` alongside `intensity: number`.

- [ ] **Step 5.4: Commit**

```bash
git add packages/supabase/supabase/migrations/*_path_pebbles_positiveness.sql packages/supabase/types/database.ts
git commit -m "feat(db): widen path_pebbles rpc with positiveness"
```

---

## Task 6: Add `positiveness` + `valence` to the `Pebble` model

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Pebble.swift`
- Modify: `apps/ios/PebblesTests/PebbleDecodingTests.swift`

Add `positiveness: Int` to the decoded shape and expose a computed `valence: Valence` matching the `(positiveness, intensity)` pairing logic from `PebbleDetail.valence` (in `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift`).

- [ ] **Step 6.1: Read the existing decoding test for fixture shape**

```bash
sed -n '1,80p' apps/ios/PebblesTests/PebbleDecodingTests.swift
```

Note the JSON shape used by existing tests so the new test follows the same conventions.

- [ ] **Step 6.2: Write the failing test**

Append inside the existing `@Suite` in `apps/ios/PebblesTests/PebbleDecodingTests.swift`:

```swift
    @Test func decodesPositiveness() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Sample",
          "happened_at": "2026-05-19T10:00:00Z",
          "created_at": "2026-05-19T10:00:00Z",
          "intensity": 2,
          "positiveness": -1,
          "render_svg": null,
          "emotion": null,
          "first_snap_path": null
        }
        """.data(using: .utf8)!
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let pebble = try decoder.decode(Pebble.self, from: json)
        #expect(pebble.positiveness == -1)
        #expect(pebble.valence == .lowlightMedium)
    }

    @Test func valenceCoversAllNineCases() {
        let cases: [(Int, Int, Valence)] = [
            (-1, 1, .lowlightSmall),  (-1, 2, .lowlightMedium),  (-1, 3, .lowlightLarge),
            ( 0, 1, .neutralSmall),   ( 0, 2, .neutralMedium),   ( 0, 3, .neutralLarge),
            ( 1, 1, .highlightSmall), ( 1, 2, .highlightMedium), ( 1, 3, .highlightLarge),
        ]
        for (pos, int, expected) in cases {
            let pebble = Pebble(
                id: UUID(),
                name: "x",
                happenedAt: .now,
                createdAt: .now,
                intensity: int,
                positiveness: pos,
                renderSvg: nil,
                emotion: nil,
                firstSnapPath: nil
            )
            #expect(pebble.valence == expected, "(\(pos), \(int))")
        }
    }
```

The first test will fail because `Pebble` has no `positiveness` field; the second won't compile because `Pebble.init` doesn't accept `positiveness`.

- [ ] **Step 6.3: Run tests to verify they fail**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleDecodingTests test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: build fails on the new test.

- [ ] **Step 6.4: Update `Pebble.swift`**

Overwrite `apps/ios/Pebbles/Features/Path/Models/Pebble.swift`:

```swift
import Foundation
import os

struct Pebble: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let happenedAt: Date
    let createdAt: Date
    let intensity: Int                  // 1=small, 2=medium, 3=large
    let positiveness: Int               // -1=lowlight, 0=neutral, +1=highlight
    let renderSvg: String?
    let emotion: EmotionRef?
    let firstSnapPath: String?

    private enum CodingKeys: String, CodingKey {
        case id, name, intensity, positiveness, emotion
        case happenedAt = "happened_at"
        case createdAt = "created_at"
        case renderSvg = "render_svg"
        case firstSnapPath = "first_snap_path"
    }

    /// Derived from `(positiveness, intensity)`. Mirrors
    /// `PebbleDetail.valence`; logs and falls back to `.neutralMedium`
    /// if the pair is out of range (DB CHECKs guarantee the pair, so
    /// this branch is only reachable via decode drift).
    var valence: Valence {
        switch (positiveness, intensity) {
        case (-1, 1): return .lowlightSmall
        case (-1, 2): return .lowlightMedium
        case (-1, 3): return .lowlightLarge
        case ( 0, 1): return .neutralSmall
        case ( 0, 2): return .neutralMedium
        case ( 0, 3): return .neutralLarge
        case ( 1, 1): return .highlightSmall
        case ( 1, 2): return .highlightMedium
        case ( 1, 3): return .highlightLarge
        default:
            Logger(subsystem: "app.pbbls.ios", category: "pebble-model").error(
                """
                unexpected (positiveness, intensity) pair: \
                (\(self.positiveness, privacy: .public), \(self.intensity, privacy: .public)) \
                — defaulting to neutralMedium
                """
            )
            return .neutralMedium
        }
    }
}
```

- [ ] **Step 6.5: Fix `Pebble(...)` initializers in existing previews**

The memberwise init now requires `positiveness`. Find all call sites:

```bash
grep -rn "Pebble($" apps/ios/Pebbles apps/ios/PebblesTests --include="*.swift" -A 12 | head -80
```

Known sites: `PathPebbleRow.swift` preview (line 142), `PebbleRow.swift` preview (line 68). For each, add `positiveness: 0,` between `intensity:` and `renderSvg:`.

- [ ] **Step 6.6: Run tests to verify they pass**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleDecodingTests test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: all decoding tests (existing + 2 new) pass.

- [ ] **Step 6.7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Pebble.swift apps/ios/PebblesTests/PebbleDecodingTests.swift apps/ios/Pebbles/Components/PebbleRow.swift apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift
git commit -m "feat(ios): add positiveness and derived valence to pebble model"
```

---

## Task 7: Update `SoulDetailView` + `CollectionDetailView` SELECT columns

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift` (line 163)
- Modify: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift` (line 164)

Both files declare a `columns` string passed to `.select(...)`. Add `positiveness`.

- [ ] **Step 7.1: Update `SoulDetailView`**

Find:

```swift
let columns = "id, name, happened_at, created_at, intensity, render_svg"
```

Replace with:

```swift
let columns = "id, name, happened_at, created_at, intensity, positiveness, render_svg"
```

- [ ] **Step 7.2: Update `CollectionDetailView`**

Same change at line 164 of `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`.

- [ ] **Step 7.3: Build to verify no compile regressions**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 7.4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift
git commit -m "fix(ios): select positiveness in soul + collection detail fetches"
```

---

## Task 8: Wire `PathPebbleRow` with the new backdrop

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift`

Replace the `RoundedRectangle(cornerRadius: 12).fill(thumbnailFill)` chrome at lines 75–82 with `PebbleOutlineBackdropView` underneath the pebble in a `ZStack`. Source the `(strokeHex, fillHex)` pair from `palette.pebbleFrameColors(forIntensity:)`; the existing `thumbnailFill` + `glyphStrokeHex` accessors collapse into one helper.

- [ ] **Step 8.1: Rewrite the thumbnail view + supporting accessors**

In `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift`:

Replace the `thumbnail` body (lines 73–84):

```swift
    @ViewBuilder
    private var thumbnail: some View {
        ZStack {
            PebbleOutlineBackdropView(
                size: pebble.valence.sizeGroup,
                polarity: pebble.valence.polarity,
                fillHex: frameColors?.fillHex ?? Color.accent.primaryHex
            )
            if let svg = pebble.renderSvg {
                PebbleRenderView(svg: svg, strokeColor: frameColors?.strokeHex ?? Color.accent.primaryHex)
                    .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: pebble.valence.sizeGroup))
            }
        }
        .aspectRatio(PebbleOutlineGeometry.aspectRatio(for: pebble.valence.sizeGroup), contentMode: .fit)
        .frame(width: thumbnailSize, height: thumbnailSize)
    }
```

Replace the `thumbnailFill` and `glyphStrokeHex` accessors (lines 104–120) with one helper:

```swift
    private var frameColors: PebbleFrameColors? {
        palette?.pebbleFrameColors(forIntensity: pebble.intensity)
    }
```

Delete the now-unused `private static let glyphInset: CGFloat = 8` and the original `thumbnailFill` + `glyphStrokeHex` properties.

Keep `palette`, `nameColor`, and the rest of the file unchanged.

- [ ] **Step 8.2: Build + run the existing `PathPebbleRowGeometryTests`**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PathPebbleRowGeometryTests test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: existing row-height tests still pass (we only changed thumbnail composition, not row geometry).

- [ ] **Step 8.3: Visual smoke check in simulator**

Boot the simulator and run `PebblesApp`. Open `PathView`. Confirm: each row shows the colored silhouette behind the pebble, no rounded-square chrome remains, and sizing reads correctly across small / medium / large intensities.

- [ ] **Step 8.4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift
git commit -m "feat(ios): wire pebble outline backdrop into path pebble row"
```

---

## Task 9: Wire `PebbleRow` with the new backdrop

**Files:**
- Modify: `apps/ios/Pebbles/Components/PebbleRow.swift`

`PebbleRow` doesn't have a current backdrop chrome to remove — it just sits a 40×40 `PebbleRenderView` in an `HStack`. Wrap it in the same `ZStack` pattern, sourcing colors from `palette.pebbleFrameColors(forIntensity:)`.

- [ ] **Step 9.1: Rewrite the thumbnail block + accessors**

Replace the `thumbnail` body (lines 46–56) and `strokeHex` accessor (lines 58–62) in `apps/ios/Pebbles/Components/PebbleRow.swift`:

```swift
    @ViewBuilder
    private var thumbnail: some View {
        if let svg = pebble.renderSvg {
            ZStack {
                PebbleOutlineBackdropView(
                    size: pebble.valence.sizeGroup,
                    polarity: pebble.valence.polarity,
                    fillHex: frameColors?.fillHex ?? Color.accent.primaryHex
                )
                PebbleRenderView(svg: svg, strokeColor: frameColors?.strokeHex ?? Color.accent.primaryHex)
                    .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: pebble.valence.sizeGroup))
            }
            .aspectRatio(PebbleOutlineGeometry.aspectRatio(for: pebble.valence.sizeGroup), contentMode: .fit)
            .frame(width: 40, height: 40)
        } else {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.secondary.opacity(0.15))
                .frame(width: 40, height: 40)
        }
    }

    private var palette: EmotionPalette? {
        guard let emotionId = pebble.emotion?.id else { return nil }
        return palettes.palette(for: emotionId)
    }

    private var frameColors: PebbleFrameColors? {
        palette?.pebbleFrameColors(forIntensity: pebble.intensity)
    }
```

Remove the `colorScheme` environment property — the new color rule is scheme-independent.

- [ ] **Step 9.2: Build**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 9.3: Visual smoke check**

Open `SoulDetailView` or `CollectionDetailView`. Confirm the 40pt thumbnails now show the silhouette + tinted pebble.

- [ ] **Step 9.4: Commit**

```bash
git add apps/ios/Pebbles/Components/PebbleRow.swift
git commit -m "feat(ios): wire pebble outline backdrop into pebble row"
```

---

## Task 10: Wire `PebbleAnimatedRenderView` (read banner) with staged spring-in

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`

`PebbleAnimatedRenderView` becomes responsible for the ZStack composition: backdrop springs in first, pebble draws/fades over it. Its sole consumer is `PebbleReadBanner` (verified via `grep`), which already knows valence from its props. Pass `(size, polarity, fillHex)` through.

In the static `PebbleRenderView` fallback path (Reduce Motion / unknown render version / parse failure), the backdrop is present but un-animated.

- [ ] **Step 10.1: Widen `PebbleAnimatedRenderView`'s signature + body**

Open `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift`. Replace the struct definition's properties + body (preserving the existing `animatedBody`, `stroke`, `progress(for:)`, and `startAnimation` helpers below it):

```swift
struct PebbleAnimatedRenderView: View {
    let svg: String
    let strokeColor: Color
    let strokeColorHex: String
    let fillHex: String
    let size: ValenceSizeGroup
    let polarity: ValencePolarity
    let renderVersion: String?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var model: PebbleSVGModel?
    @State private var glyphProgress: Double = 0
    @State private var shapeProgress: Double = 0
    @State private var fossilProgress: Double = 0
    @State private var settleScale: Double = 1
    @State private var backdropIn: Bool = false
    @State private var pebbleIn: Bool = false

    var body: some View {
        ZStack {
            PebbleOutlineBackdropView(size: size, polarity: polarity, fillHex: fillHex)
                .scaleEffect(reduceMotion ? 1 : (backdropIn ? 1 : 0.6))
                .opacity(reduceMotion ? 1 : (backdropIn ? 1 : 0))

            pebbleLayer
                .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: size))
                .opacity(reduceMotion ? 1 : (pebbleIn ? 1 : 0))
        }
        .aspectRatio(PebbleOutlineGeometry.aspectRatio(for: size), contentMode: .fit)
        .onAppear {
            if model == nil {
                model = PebbleSVGModel(svg: svg)
                if model == nil {
                    Logger(subsystem: "app.pbbls.ios", category: "pebble-render")
                        .info("PebbleAnimatedRenderView: parse failed; using SVGView fallback")
                }
            }
            startEntryAnimation()
            startAnimation()
        }
        .onDisappear { resetProgress() }
    }

    @ViewBuilder
    private var pebbleLayer: some View {
        if let model, let timings = PebbleAnimationTimings.forVersion(renderVersion), !reduceMotion {
            animatedBody(model: model, timings: timings)
        } else {
            PebbleRenderView(svg: svg, strokeColor: strokeColorHex)
        }
    }

    private func startEntryAnimation() {
        guard !reduceMotion else {
            backdropIn = true
            pebbleIn = true
            return
        }
        withAnimation(.spring(response: 0.42, dampingFraction: 0.7)) {
            backdropIn = true
        }
        Task {
            try? await Task.sleep(for: .milliseconds(180))
            await MainActor.run {
                withAnimation(.easeOut(duration: 0.25)) {
                    pebbleIn = true
                }
            }
        }
    }
```

- [ ] **Step 10.2: Update `resetProgress`**

In the same file, extend `resetProgress` to reset the new state too:

```swift
    private func resetProgress() {
        glyphProgress = 0
        shapeProgress = 0
        fossilProgress = 0
        settleScale = 1
        backdropIn = false
        pebbleIn = false
    }
```

- [ ] **Step 10.3: Update the two `#Preview` blocks at the bottom of the file**

Each `PebbleAnimatedRenderView(...)` preview gets the four new args. Insert alongside the existing params:

```swift
        fillHex: "#7C5CFA",
        size: .medium,
        polarity: .neutral,
```

- [ ] **Step 10.4: Update `PebbleReadBanner` to pass the new props**

In `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`, replace the `renderedPebble` body (lines 156–170):

```swift
    @ViewBuilder
    private var renderedPebble: some View {
        if let renderSvg {
            let palette = palettes.palette(for: emotionId)
            let frameColors = palette?.pebbleFrameColors(forIntensity: valence.intensity)
            PebbleAnimatedRenderView(
                svg: renderSvg,
                strokeColor: palette?.stroke(for: colorScheme) ?? Color.accent.primary,
                strokeColorHex: frameColors?.strokeHex ?? Color.accent.primaryHex,
                fillHex: frameColors?.fillHex ?? Color.accent.primaryHex,
                size: valence.sizeGroup,
                polarity: valence.polarity,
                renderVersion: renderVersion
            )
            .frame(height: pebbleHeight)
        } else {
            EmptyView()
        }
    }
```

`strokeColor` keeps the existing `palette?.stroke(for: colorScheme)` source so the SwiftUI stroke path is unchanged; only the SVG-text path (`strokeColorHex`) shifts to the new helper.

- [ ] **Step 10.5: Build + run the existing animation timing tests**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleAnimationTimingsTests test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -10
```

Expected: existing timing tests unchanged. If any test references the old `PebbleAnimatedRenderView` initializer directly, update its call to include the new props (search: `grep -rn "PebbleAnimatedRenderView(" apps/ios/PebblesTests`).

- [ ] **Step 10.6: Visual smoke check — pebble detail sheet**

Open a pebble detail sheet in the simulator. Confirm: backdrop springs in first (~0.42s spring), pebble fades on top (~250ms after 180ms delay) and then strokes draw per `PebbleAnimationTimings`. With Reduce Motion enabled (Settings → Accessibility → Motion), backdrop + pebble appear instantly together without spring.

- [ ] **Step 10.7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift
git commit -m "feat(ios): stage pebble outline backdrop into animated read banner"
```

---

## Task 11: Full build + visual review across the 9 (size × valence) combinations

**Files:** none (verification only).

- [ ] **Step 11.1: Workspace-scoped build**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -15
```

Expected: `BUILD SUCCEEDED` with no new warnings related to the changes.

- [ ] **Step 11.2: Full iOS test suite**

```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&1 | tail -20
```

Expected: all existing + new tests pass.

- [ ] **Step 11.3: Visual review across all 9 combinations**

Seed or filter to one pebble per `(size, polarity)` cell — 9 total. For each, open `PathView` and ideally `PebbleDetailSheet` (to see the animation) and check against the design screenshots in issue #473:

| Size × Polarity      | List row | Animated banner |
|----------------------|----------|------------------|
| small × lowlight     | ☐        | ☐                |
| small × neutral      | ☐        | ☐                |
| small × highlight    | ☐        | ☐                |
| medium × lowlight    | ☐        | ☐                |
| medium × neutral     | ☐        | ☐                |
| medium × highlight   | ☐        | ☐                |
| large × lowlight     | ☐        | ☐                |
| large × neutral      | ☐        | ☐                |
| large × highlight    | ☐        | ☐                |

For each: silhouette is the right shape; fill resolves to the expected hex (large = `palette.primary`, others = `palette.surface`); pebble strokes resolve to the expected hex (large = `palette.light`, others = `palette.secondary`); pebble geometry is unchanged — it sits centered at ~74% of the backdrop with no clipping.

- [ ] **Step 11.4: Confirm acceptance criteria from the spec**

Walk through the "Acceptance criteria" list in `docs/superpowers/specs/2026-05-19-pebble-outline-frame-ios-design.md`. The "No `render_version` bump. No migration file." line is partially deviated by Task 5 — note this in the PR description (no engine / no render_version / no backfill — only the RPC return shape widens).

- [ ] **Step 11.5: Arkaik map check (skip likely)**

Per CLAUDE.md task-size triage: this is a medium change with no new screens, routes, data models, or endpoints. The `Pebble` model field addition and the RPC widening are non-architectural extensions. **Skip the arkaik update unless review surfaces an architectural concern.**

- [ ] **Step 11.6: Open the PR**

Branch: `feat/474-ios-pebble-outline-frame`. PR body must `Resolves #474`. Inherit issue 474's labels and milestone (confirm via `gh issue view 474 --json labels,milestone`).

PR body skeleton (adjust during creation):

```
Resolves #474

## Summary
- Adds a per-(size × valence) silhouette behind every iOS site that renders a single pebble.
- New: `PebbleOutlineBackdropView`, `PebbleOutlineGeometry`, `PebbleFrameColors` (on `EmotionPalette`), 9 outline SVG assets.
- Wires `PathPebbleRow`, `PebbleRow`, and `PebbleAnimatedRenderView` (via `PebbleReadBanner`) to the new backdrop.
- Widens `path_pebbles` RPC + Soul/Collection detail SELECTs to include `positiveness` so list rows can pick the matching outline polarity. No engine, no render_version, no backfill change.

## Implementation notes
- Sentinel `#FF00FF` swap pattern mirrors `PebbleRenderView`'s `currentColor` swap.
- Backdrop in `PebbleAnimatedRenderView` springs in first (~420ms), pebble fades on top (~250ms after 180ms delay). Reduce Motion bypasses both.
- Size→role mapping lives once, on `EmotionPalette.pebbleFrameColors(forIntensity:)`.

## Test plan
- [ ] Full iOS test suite green
- [ ] Visual review across all 9 (size × polarity) combinations against design screenshots in #473
- [ ] Reduce Motion path: backdrop appears instantly with no spring
```

---

## Self-review checklist (run before declaring complete)

- [ ] **Spec coverage:** every "Acceptance criteria" bullet in the spec maps to a step here (with the noted deviation on the migration line documented in the PR body).
- [ ] **Type consistency:** `ValenceSizeGroup`, `ValencePolarity`, and `Valence` names are used consistently in `PebbleOutlineGeometry`, `PebbleOutlineBackdropView`, `Pebble.valence`, and consumer wiring.
- [ ] **No placeholders:** every Swift snippet and every SVG body is full content, ready to paste.
- [ ] **Frequent commits:** one per logical change (assets / geometry / backdrop / palette helper / migration / model / SELECTs / PathPebbleRow / PebbleRow / animated banner).
- [ ] **No `render_version` bump.** No backfill migration. No engine change under `_shared/engine/`.
