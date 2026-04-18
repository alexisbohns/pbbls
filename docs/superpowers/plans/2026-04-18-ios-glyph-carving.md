# iOS Glyph Carving Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an iOS user carve glyphs by finger, pick one for a pebble during record or edit, see it composed into the pebble render, and manage all glyphs from Profile → Glyphs.

**Architecture:** New `Features/Glyph/` folder containing models, utilities (RDP simplification + SVG path (de)serialization), a `GlyphService` wrapper over Supabase, and the three SwiftUI views (canvas, carve sheet, picker sheet). The record form gains a "Glyph" row. The edit flow migrates from the bare `update_pebble` RPC to a new client-callable `compose-pebble-update` edge function so the server regenerates `render_svg` when the glyph changes — mirroring the existing create flow.

**Tech Stack:** SwiftUI `Canvas` + `DragGesture` (drawing), SVGView (display), Supabase Swift SDK (direct single-table inserts for glyphs, `.functions.invoke` for pebble create/update), Deno-TypeScript edge functions, Swift Testing (`@Suite`/`@Test`/`#expect`).

**Design spec:** `docs/superpowers/specs/2026-04-18-ios-glyph-carving-design.md`

**Issue:** [#278](https://github.com/alexisbohns/pbbls/issues/278) · Milestone **M23 · TestFlight V1** · Labels `feat`, `core`, `ios`

---

## File map

### New files

| Path | Responsibility |
|---|---|
| `apps/ios/Pebbles/Features/Glyph/Models/Glyph.swift` | Full glyph struct with `id, name, strokes, viewBox` |
| `apps/ios/Pebbles/Features/Glyph/Models/GlyphStroke.swift` | `{ d, width }` — matches web `MarkStroke` |
| `apps/ios/Pebbles/Features/Glyph/Models/GlyphInsertPayload.swift` | Encodable for direct insert into `public.glyphs` |
| `apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift` | `list()`, `create()`, hardcoded square shape id |
| `apps/ios/Pebbles/Features/Glyph/Utils/PathSimplification.swift` | RDP port (pure) |
| `apps/ios/Pebbles/Features/Glyph/Utils/SVGPath.swift` | Points → `d` string; `d` string → SwiftUI `Path` |
| `apps/ios/Pebbles/Features/Glyph/Views/GlyphCanvasView.swift` | SwiftUI `Canvas` + `DragGesture` |
| `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift` | Full-screen cover host for the carve flow |
| `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift` | Picker + "Carve new glyph" CTA |
| `apps/ios/Pebbles/Features/Glyph/Views/GlyphThumbnail.swift` | Reusable 200×200 SVG rendering of a glyph |
| `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift` | Moved from `Profile/Lists/`, grid + toolbar carve button |
| `apps/ios/PebblesTests/Features/Glyph/PathSimplificationTests.swift` | RDP unit tests |
| `apps/ios/PebblesTests/Features/Glyph/SVGPathTests.swift` | Serialize + parse unit tests |
| `apps/ios/PebblesTests/Features/Glyph/GlyphInsertPayloadEncodingTests.swift` | Payload encoding tests |
| `packages/supabase/supabase/functions/compose-pebble-update/index.ts` | Edge function wrapping `update_pebble` + compose-and-write |

### Deleted files

| Path | Reason |
|---|---|
| `apps/ios/Pebbles/Features/Profile/Models/Glyph.swift` | Moved (superseded by `Features/Glyph/Models/Glyph.swift`) |
| `apps/ios/Pebbles/Features/Profile/Lists/GlyphsListView.swift` | Moved (superseded by `Features/Glyph/Views/GlyphsListView.swift`) |

### Modified files

| Path | Change |
|---|---|
| `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift` | Add `glyphId: UUID?` → `glyph_id` |
| `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift` | Add `glyphId: UUID?` → `glyph_id` |
| `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift` | Add `glyphId: UUID?`; extend `init(from: PebbleDetail)` |
| `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift` | Decode `glyph_id` alongside the existing fields |
| `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` | New "Glyph" row |
| `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` | Nothing changes in save flow (payload already carries `glyphId`); verify pass-through |
| `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` | Extend select to include `glyph_id`; switch from `.rpc("update_pebble", …)` to `.functions.invoke("compose-pebble-update", …)`; update in-memory `renderSvg` from the response before dismissing |
| `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift` | New tests for `glyph_id` encoding |
| `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift` | Same |
| `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift` | New test asserting `draft.glyphId` is prefilled |

---

## Phase 0: Branch hygiene

Spec commit already exists on branch `feat/278-ios-glyph-carving`. All work below lands on this branch.

- [ ] **Step 0.1: Confirm branch**

Run: `git rev-parse --abbrev-ref HEAD`
Expected: `feat/278-ios-glyph-carving`

If different, create/switch: `git checkout feat/278-ios-glyph-carving` (or create from `main` if missing).

---

## Phase 1: Foundation — models + pure utilities (TDD)

### Task 1: `GlyphStroke` model

**Files:**
- Create: `apps/ios/Pebbles/Features/Glyph/Models/GlyphStroke.swift`

- [ ] **Step 1.1: Create the file**

```swift
import Foundation

/// One stroke within a carved glyph. Mirrors the web `MarkStroke` shape so
/// glyphs are interoperable between web and iOS.
///
/// - `d`: SVG path string ("M x,y L x,y …" or with quadratic Beziers for smoothed strokes).
/// - `width`: stroke width in the glyph's 200x200 coordinate space. iOS-carved
///   strokes always use 6 per the issue constraint (no user-facing slider).
struct GlyphStroke: Codable, Hashable {
    let d: String
    let width: Double
}
```

- [ ] **Step 1.2: Verify compile via lint-only check (no test yet — data struct)**

Run: `cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: `** BUILD SUCCEEDED **` at the end.

- [ ] **Step 1.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Models/GlyphStroke.swift apps/ios/project.yml 2>/dev/null; git add apps/ios/Pebbles/Features/Glyph/Models/GlyphStroke.swift
git commit -m "feat(ios): add GlyphStroke model"
```

---

### Task 2: `Glyph` model (full shape)

**Files:**
- Create: `apps/ios/Pebbles/Features/Glyph/Models/Glyph.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Models/Glyph.swift`

- [ ] **Step 2.1: Create the new Glyph**

```swift
import Foundation

/// Full glyph model. Replaces the minimal read-only stub that used to live in
/// `Features/Profile/Models/Glyph.swift`.
///
/// Stored in `public.glyphs`. `viewBox` is always `"0 0 200 200"` for glyphs
/// carved on iOS; imported web glyphs may use different viewBox values.
struct Glyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String?
    let strokes: [GlyphStroke]
    let viewBox: String

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case strokes
        case viewBox = "view_box"
    }
}
```

- [ ] **Step 2.2: Delete the old minimal Glyph**

```bash
git rm apps/ios/Pebbles/Features/Profile/Models/Glyph.swift
```

- [ ] **Step 2.3: Build**

Run: `cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: `** BUILD SUCCEEDED **`. If `GlyphsListView` fails to compile because it references a field the new Glyph has (it shouldn't — the old Glyph only had `id, name`, and the new one is a superset), continue to Task 16 where that file gets replaced anyway.

- [ ] **Step 2.4: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Models/Glyph.swift apps/ios/Pebbles/Features/Profile/Models/Glyph.swift
git commit -m "feat(ios): promote Glyph model to full carving shape"
```

---

### Task 3: `PathSimplification` utility (TDD)

**Files:**
- Create: `apps/ios/PebblesTests/Features/Glyph/PathSimplificationTests.swift`
- Create: `apps/ios/Pebbles/Features/Glyph/Utils/PathSimplification.swift`

- [ ] **Step 3.1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

@Suite("PathSimplification (RDP)")
struct PathSimplificationTests {

    @Test("empty input returns empty")
    func empty() {
        #expect(PathSimplification.simplify(points: [], epsilon: 1.5).isEmpty)
    }

    @Test("two-point input is unchanged")
    func twoPoints() {
        let input = [CGPoint(x: 0, y: 0), CGPoint(x: 10, y: 10)]
        #expect(PathSimplification.simplify(points: input, epsilon: 1.5) == input)
    }

    @Test("collinear points reduce to endpoints")
    func collinearCollapse() {
        let input = (0...9).map { CGPoint(x: Double($0), y: Double($0)) }  // y = x
        let simplified = PathSimplification.simplify(points: input, epsilon: 1.5)
        #expect(simplified == [CGPoint(x: 0, y: 0), CGPoint(x: 9, y: 9)])
    }

    @Test("sharp corner survives simplification")
    func sharpCorner() {
        // An L-shape: 5 points along x-axis, then 5 up y-axis. Neither arm is
        // reducible to a single segment at ε = 1.5 without losing the corner.
        let input: [CGPoint] = [
            .init(x: 0, y: 0),
            .init(x: 5, y: 0),
            .init(x: 10, y: 0),
            .init(x: 10, y: 5),
            .init(x: 10, y: 10),
        ]
        let simplified = PathSimplification.simplify(points: input, epsilon: 1.5)
        // Must contain the corner (10, 0)
        #expect(simplified.contains(CGPoint(x: 10, y: 0)))
        #expect(simplified.first == CGPoint(x: 0, y: 0))
        #expect(simplified.last == CGPoint(x: 10, y: 10))
    }
}
```

- [ ] **Step 3.2: Run and verify failure**

Run: `cd apps/ios && xcodegen generate && xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PathSimplificationTests 2>&1 | tail -40`
Expected: `** TEST FAILED **` with "Cannot find 'PathSimplification' in scope".

- [ ] **Step 3.3: Implement the utility**

Create `apps/ios/Pebbles/Features/Glyph/Utils/PathSimplification.swift`:

```swift
import CoreGraphics

/// Ramer-Douglas-Peucker path simplification.
/// Direct port of `apps/web/lib/utils/simplify-path.ts` (ε = 1.5 in callers).
enum PathSimplification {
    static func simplify(points: [CGPoint], epsilon: Double) -> [CGPoint] {
        guard points.count > 2 else { return points }

        var maxDist = 0.0
        var maxIndex = 0
        let first = points[0]
        let last = points[points.count - 1]

        for i in 1..<(points.count - 1) {
            let dist = perpendicularDistance(points[i], lineStart: first, lineEnd: last)
            if dist > maxDist {
                maxDist = dist
                maxIndex = i
            }
        }

        if maxDist > epsilon {
            let left = simplify(points: Array(points[0...maxIndex]), epsilon: epsilon)
            let right = simplify(points: Array(points[maxIndex..<points.count]), epsilon: epsilon)
            return Array(left.dropLast()) + right
        }

        return [first, last]
    }

    private static func perpendicularDistance(
        _ point: CGPoint,
        lineStart: CGPoint,
        lineEnd: CGPoint
    ) -> Double {
        let dx = lineEnd.x - lineStart.x
        let dy = lineEnd.y - lineStart.y
        let lengthSq = Double(dx * dx + dy * dy)

        if lengthSq == 0 {
            let ex = Double(point.x - lineStart.x)
            let ey = Double(point.y - lineStart.y)
            return (ex * ex + ey * ey).squareRoot()
        }

        let t = max(
            0.0,
            min(
                1.0,
                Double((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / lengthSq
            )
        )
        let projX = Double(lineStart.x) + t * Double(dx)
        let projY = Double(lineStart.y) + t * Double(dy)
        let ex = Double(point.x) - projX
        let ey = Double(point.y) - projY
        return (ex * ex + ey * ey).squareRoot()
    }
}
```

- [ ] **Step 3.4: Run tests — expect pass**

Run: same as Step 3.2.
Expected: `** TEST SUCCEEDED **` with 4 tests passing.

- [ ] **Step 3.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Utils/PathSimplification.swift apps/ios/PebblesTests/Features/Glyph/PathSimplificationTests.swift
git commit -m "feat(ios): add RDP path simplification utility"
```

---

### Task 4: `SVGPath` utility (TDD — serialize + parse)

**Files:**
- Create: `apps/ios/PebblesTests/Features/Glyph/SVGPathTests.swift`
- Create: `apps/ios/Pebbles/Features/Glyph/Utils/SVGPath.swift`

- [ ] **Step 4.1: Write the failing tests**

```swift
import CoreGraphics
import SwiftUI
import Testing
@testable import Pebbles

@Suite("SVGPath.svgPathString")
struct SVGPathSerializationTests {

    @Test("empty produces empty string")
    func empty() {
        #expect(SVGPath.svgPathString(from: []) == "")
    }

    @Test("one point produces a zero-length line")
    func onePoint() {
        let out = SVGPath.svgPathString(from: [CGPoint(x: 10, y: 20)])
        #expect(out == "M10,20 L10,20")
    }

    @Test("two points produce a straight line")
    func twoPoints() {
        let out = SVGPath.svgPathString(from: [CGPoint(x: 0, y: 0), CGPoint(x: 10, y: 20)])
        #expect(out == "M0,0 L10,20")
    }

    @Test("three points produce a smoothed path with a quadratic Bezier")
    func threePointsSmoothed() {
        // Matches the web `pointsToSvgPath` logic: M p0 Q p1 midpoint(p1,p2) L p2
        let out = SVGPath.svgPathString(from: [
            CGPoint(x: 0, y: 0),
            CGPoint(x: 10, y: 10),
            CGPoint(x: 20, y: 20),
        ])
        #expect(out == "M0,0 Q10,10 15,15 L20,20")
    }

    @Test("fractional points round to two decimals")
    func rounding() {
        let out = SVGPath.svgPathString(from: [
            CGPoint(x: 0.123, y: 0.987),
            CGPoint(x: 9.999, y: 5.111),
        ])
        #expect(out == "M0.12,0.99 L10,5.11")
    }
}

@Suite("SVGPath.path(from:)")
struct SVGPathParsingTests {

    @Test("parses an M-only command into a non-empty path")
    func parseMOnly() {
        let path = SVGPath.path(from: "M10,10 L20,20")
        #expect(!path.isEmpty)
    }

    @Test("parses a quadratic Bezier (Q) without crashing")
    func parseQ() {
        let path = SVGPath.path(from: "M0,0 Q10,10 15,15 L20,20")
        #expect(!path.isEmpty)
    }

    @Test("malformed input returns an empty path")
    func malformed() {
        let path = SVGPath.path(from: "totally not an svg path ⚡")
        #expect(path.isEmpty)
    }

    @Test("empty string returns an empty path")
    func emptyString() {
        #expect(SVGPath.path(from: "").isEmpty)
    }
}
```

- [ ] **Step 4.2: Run and verify failure**

Run: `cd apps/ios && xcodegen generate && xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/SVGPathSerializationTests -only-testing:PebblesTests/SVGPathParsingTests 2>&1 | tail -40`
Expected: FAIL with "Cannot find 'SVGPath' in scope".

- [ ] **Step 4.3: Implement the utility**

Create `apps/ios/Pebbles/Features/Glyph/Utils/SVGPath.swift`:

```swift
import CoreGraphics
import SwiftUI
import os

/// Serialization + parsing between a `[CGPoint]` stroke and an SVG `d` string.
///
/// Serialization mirrors `apps/web/lib/utils/simplify-path.ts` (`pointsToSvgPath`):
/// uses quadratic Bezier curves through midpoints for ≥3 points to produce a
/// visually smooth stroke. Parsing supports `M`, `L`, `Q`, `C` — the commands
/// the codebase emits (iOS-carved) or persists (web + system seed glyphs).
enum SVGPath {

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "svg-path")

    // MARK: - Serialize

    static func svgPathString(from points: [CGPoint], precision: Int = 2) -> String {
        if points.isEmpty { return "" }

        let fmt = { (value: CGFloat) in round(value: Double(value), precision: precision) }

        if points.count == 1 {
            let p = points[0]
            return "M\(fmt(p.x)),\(fmt(p.y)) L\(fmt(p.x)),\(fmt(p.y))"
        }

        if points.count == 2 {
            return "M\(fmt(points[0].x)),\(fmt(points[0].y)) L\(fmt(points[1].x)),\(fmt(points[1].y))"
        }

        var d = "M\(fmt(points[0].x)),\(fmt(points[0].y))"

        // Smooth via midpoints
        for i in 1..<(points.count - 1) {
            let midX = (points[i].x + points[i + 1].x) / 2
            let midY = (points[i].y + points[i + 1].y) / 2
            d += " Q\(fmt(points[i].x)),\(fmt(points[i].y)) \(fmt(midX)),\(fmt(midY))"
        }

        let last = points[points.count - 1]
        d += " L\(fmt(last.x)),\(fmt(last.y))"
        return d
    }

    private static func round(value: Double, precision: Int) -> String {
        let multiplier = pow(10.0, Double(precision))
        let rounded = (value * multiplier).rounded() / multiplier
        // Trim trailing zeros to match web output (`10` not `10.00`).
        if rounded == rounded.rounded() {
            return String(Int(rounded))
        }
        return String(format: "%.\(precision)g", rounded)
    }

    // MARK: - Parse

    /// Parses a subset of SVG path syntax (M/L/Q/C) into a SwiftUI `Path`.
    /// On malformed input, logs a warning and returns an empty `Path`.
    static func path(from d: String) -> Path {
        guard !d.isEmpty else { return Path() }

        var path = Path()
        var index = d.startIndex
        var current = CGPoint.zero

        while index < d.endIndex {
            // Skip whitespace
            while index < d.endIndex, d[index].isWhitespace { index = d.index(after: index) }
            guard index < d.endIndex else { break }

            let command = d[index]
            guard "MLQCmlqc".contains(command) else {
                logger.warning("unknown svg command \(command, privacy: .public) — aborting parse")
                return Path()
            }
            index = d.index(after: index)

            switch command {
            case "M", "m":
                guard let (pt, next) = readPoint(d, from: index) else {
                    logger.warning("malformed M — aborting parse")
                    return Path()
                }
                let absolute = command == "M" ? pt : CGPoint(x: current.x + pt.x, y: current.y + pt.y)
                path.move(to: absolute)
                current = absolute
                index = next
            case "L", "l":
                guard let (pt, next) = readPoint(d, from: index) else {
                    logger.warning("malformed L — aborting parse")
                    return Path()
                }
                let absolute = command == "L" ? pt : CGPoint(x: current.x + pt.x, y: current.y + pt.y)
                path.addLine(to: absolute)
                current = absolute
                index = next
            case "Q", "q":
                guard
                    let (control, next1) = readPoint(d, from: index),
                    let (end, next2) = readPoint(d, from: next1)
                else {
                    logger.warning("malformed Q — aborting parse")
                    return Path()
                }
                let absControl = command == "Q" ? control : CGPoint(x: current.x + control.x, y: current.y + control.y)
                let absEnd = command == "Q" ? end : CGPoint(x: current.x + end.x, y: current.y + end.y)
                path.addQuadCurve(to: absEnd, control: absControl)
                current = absEnd
                index = next2
            case "C", "c":
                guard
                    let (c1, next1) = readPoint(d, from: index),
                    let (c2, next2) = readPoint(d, from: next1),
                    let (end, next3) = readPoint(d, from: next2)
                else {
                    logger.warning("malformed C — aborting parse")
                    return Path()
                }
                let absC1 = command == "C" ? c1 : CGPoint(x: current.x + c1.x, y: current.y + c1.y)
                let absC2 = command == "C" ? c2 : CGPoint(x: current.x + c2.x, y: current.y + c2.y)
                let absEnd = command == "C" ? end : CGPoint(x: current.x + end.x, y: current.y + end.y)
                path.addCurve(to: absEnd, control1: absC1, control2: absC2)
                current = absEnd
                index = next3
            default:
                return Path()
            }
        }

        return path
    }

    /// Reads `x,y` (or `x y`) starting at `from`, skipping leading whitespace.
    /// Returns the parsed point and the next index on success.
    private static func readPoint(_ s: String, from: String.Index) -> (CGPoint, String.Index)? {
        var index = from
        while index < s.endIndex, s[index].isWhitespace { index = s.index(after: index) }
        guard let (x, afterX) = readNumber(s, from: index) else { return nil }
        index = afterX
        // Skip comma or whitespace separator
        while index < s.endIndex, s[index] == "," || s[index].isWhitespace {
            index = s.index(after: index)
        }
        guard let (y, afterY) = readNumber(s, from: index) else { return nil }
        return (CGPoint(x: x, y: y), afterY)
    }

    private static func readNumber(_ s: String, from: String.Index) -> (Double, String.Index)? {
        var end = from
        while end < s.endIndex, "0123456789.-+eE".contains(s[end]) {
            end = s.index(after: end)
        }
        guard end > from, let value = Double(s[from..<end]) else { return nil }
        return (value, end)
    }
}
```

- [ ] **Step 4.4: Run tests — expect pass**

Run: same as Step 4.2.
Expected: all 9 tests pass.

If the "fractional points round to two decimals" test fails because `%.2g` produces unexpected output, adjust `round(value:precision:)` until the expected strings match — the goal is to match web's output (`10` not `10.00`, `5.11` not `5.110`).

- [ ] **Step 4.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Utils/SVGPath.swift apps/ios/PebblesTests/Features/Glyph/SVGPathTests.swift
git commit -m "feat(ios): add SVGPath serialize/parse utility"
```

---

### Task 5: `GlyphInsertPayload` (TDD)

**Files:**
- Create: `apps/ios/PebblesTests/Features/Glyph/GlyphInsertPayloadEncodingTests.swift`
- Create: `apps/ios/Pebbles/Features/Glyph/Models/GlyphInsertPayload.swift`

- [ ] **Step 5.1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("GlyphInsertPayload encoding")
struct GlyphInsertPayloadEncodingTests {

    private func encode(_ payload: GlyphInsertPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data)
        return try #require(object as? [String: Any])
    }

    @Test("encodes snake_case keys")
    func snakeCaseKeys() throws {
        let userId = UUID()
        let shapeId = UUID()
        let payload = GlyphInsertPayload(
            userId: userId,
            shapeId: shapeId,
            strokes: [GlyphStroke(d: "M0,0 L10,10", width: 6)],
            viewBox: "0 0 200 200",
            name: nil
        )
        let json = try encode(payload)
        #expect((json["user_id"] as? String) == userId.uuidString)
        #expect((json["shape_id"] as? String) == shapeId.uuidString)
        #expect((json["view_box"] as? String) == "0 0 200 200")
        #expect(json["name"] is NSNull)
    }

    @Test("strokes encode as a JSON array of { d, width }")
    func strokeShape() throws {
        let payload = GlyphInsertPayload(
            userId: UUID(),
            shapeId: UUID(),
            strokes: [
                GlyphStroke(d: "M0,0 L1,1", width: 6),
                GlyphStroke(d: "M2,2 L3,3", width: 6),
            ],
            viewBox: "0 0 200 200",
            name: nil
        )
        let json = try encode(payload)
        let strokes = try #require(json["strokes"] as? [[String: Any]])
        #expect(strokes.count == 2)
        #expect(strokes[0]["d"] as? String == "M0,0 L1,1")
        #expect(strokes[0]["width"] as? Double == 6)
    }
}
```

- [ ] **Step 5.2: Run and verify failure**

Run: `cd apps/ios && xcodegen generate && xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/GlyphInsertPayloadEncodingTests 2>&1 | tail -30`
Expected: FAIL — "Cannot find 'GlyphInsertPayload' in scope".

- [ ] **Step 5.3: Implement the payload**

Create `apps/ios/Pebbles/Features/Glyph/Models/GlyphInsertPayload.swift`:

```swift
import Foundation

/// Body for a direct `INSERT INTO public.glyphs` via the Supabase Swift SDK.
/// Single-table write — no RPC needed (see `AGENTS.md`).
struct GlyphInsertPayload: Encodable {
    let userId: UUID
    let shapeId: UUID
    let strokes: [GlyphStroke]
    let viewBox: String
    let name: String?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case shapeId = "shape_id"
        case viewBox = "view_box"
        case strokes
        case name
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(userId, forKey: .userId)
        try container.encode(shapeId, forKey: .shapeId)
        try container.encode(strokes, forKey: .strokes)
        try container.encode(viewBox, forKey: .viewBox)
        // Explicit null so absence is unambiguous.
        try container.encode(name, forKey: .name)
    }
}
```

- [ ] **Step 5.4: Run tests — expect pass**

Run: same as Step 5.2.
Expected: 2 tests pass.

- [ ] **Step 5.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Models/GlyphInsertPayload.swift apps/ios/PebblesTests/Features/Glyph/GlyphInsertPayloadEncodingTests.swift
git commit -m "feat(ios): add GlyphInsertPayload encodable"
```

---

### Task 6: Extend `PebbleCreatePayload` with `glyphId`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`
- Modify: `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift`

- [ ] **Step 6.1: Write the failing test**

Append to `PebbleCreatePayloadEncodingTests`:

```swift
    @Test("encodes glyph_id as null when draft has no glyph")
    func nullGlyphId() throws {
        let draft = makeValidDraft()
        let json = try encode(PebbleCreatePayload(from: draft))
        #expect(json["glyph_id"] is NSNull)
    }

    @Test("encodes glyph_id as uuid string when set")
    func setGlyphId() throws {
        let glyphId = UUID()
        var draft = makeValidDraft()
        draft.glyphId = glyphId
        let json = try encode(PebbleCreatePayload(from: draft))
        #expect((json["glyph_id"] as? String) == glyphId.uuidString)
    }
```

- [ ] **Step 6.2: Run and verify failure**

Run: `cd apps/ios && xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleCreatePayloadEncodingTests 2>&1 | tail -30`
Expected: FAIL — `draft.glyphId` not defined.

- [ ] **Step 6.3: Add `glyphId` to `PebbleDraft`**

Modify `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`: add the property just before `visibility`:

```swift
    var soulId: UUID?                     // optional
    var collectionId: UUID?               // optional
    var glyphId: UUID?                    // optional — set via GlyphPickerSheet
    var visibility: Visibility = .private // mandatory
```

- [ ] **Step 6.4: Add `glyphId` to `PebbleCreatePayload`**

Modify `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`:

1. Add stored property:
```swift
    let glyphId: UUID?
```

2. Add to `CodingKeys`:
```swift
        case glyphId = "glyph_id"
```

3. Add to `encode(to:)`:
```swift
        try container.encode(glyphId, forKey: .glyphId)
```

4. Add to `init(from: PebbleDraft)`:
```swift
        self.glyphId = draft.glyphId
```

- [ ] **Step 6.5: Run tests — expect pass**

Run: same as Step 6.2.
Expected: all PebbleCreatePayload tests pass (including the two new ones).

- [ ] **Step 6.6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift
git commit -m "feat(ios): plumb glyph_id through PebbleCreatePayload"
```

---

### Task 7: Extend `PebbleUpdatePayload` with `glyphId`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift`
- Modify: `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift`

- [ ] **Step 7.1: Write the failing test**

Open `PebbleUpdatePayloadEncodingTests.swift` and append (assume the file already follows the same structure as the create test — if not, mirror the encode/makeValidDraft helpers):

```swift
    @Test("encodes glyph_id as null when draft has no glyph")
    func nullGlyphId() throws {
        let draft = makeValidDraft()
        let json = try encode(PebbleUpdatePayload(from: draft))
        #expect(json["glyph_id"] is NSNull)
    }

    @Test("encodes glyph_id as uuid string when set")
    func setGlyphId() throws {
        let glyphId = UUID()
        var draft = makeValidDraft()
        draft.glyphId = glyphId
        let json = try encode(PebbleUpdatePayload(from: draft))
        #expect((json["glyph_id"] as? String) == glyphId.uuidString)
    }
```

- [ ] **Step 7.2: Run and verify failure**

Run: `cd apps/ios && xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleUpdatePayloadEncodingTests 2>&1 | tail -30`
Expected: FAIL.

- [ ] **Step 7.3: Modify `PebbleUpdatePayload`**

In `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift`:

1. Add stored property after `collectionIds`:
```swift
    let glyphId: UUID?
```

2. Add to `CodingKeys`:
```swift
        case glyphId = "glyph_id"
```

3. Add to `encode(to:)` after `collectionIds`:
```swift
        try container.encode(glyphId, forKey: .glyphId)
```

4. Add to `init(from: PebbleDraft)`:
```swift
        self.glyphId = draft.glyphId
```

- [ ] **Step 7.4: Run tests — expect pass**

Run: same as Step 7.2.
Expected: pass.

- [ ] **Step 7.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift
git commit -m "feat(ios): plumb glyph_id through PebbleUpdatePayload"
```

---

### Task 8: Extend `PebbleDetail` + `PebbleDraft(from: detail)` with glyph id

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`
- Modify: `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift`

- [ ] **Step 8.1: Write the failing test**

Open `PebbleDraftFromDetailTests.swift` and append a test asserting the `glyphId` round-trip. If the existing test file uses a helper to build a `PebbleDetail` from JSON, follow that pattern; otherwise, add a JSON-driven test:

```swift
    @Test("draft.glyphId is populated from detail.glyphId")
    func glyphIdRoundTrip() throws {
        let glyphId = UUID()
        let json = """
        {
          "id": "\(UUID().uuidString)",
          "name": "Test",
          "description": null,
          "happened_at": "2026-04-18T12:00:00Z",
          "intensity": 2,
          "positiveness": 1,
          "visibility": "private",
          "render_svg": null,
          "render_version": null,
          "glyph_id": "\(glyphId.uuidString)",
          "emotion": {"id": "\(UUID().uuidString)", "name": "Joy", "color": "#fff"},
          "pebble_domains": [{"domain": {"id": "\(UUID().uuidString)", "name": "Work"}}],
          "pebble_souls": [],
          "collection_pebbles": []
        }
        """.data(using: .utf8)!

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let detail = try decoder.decode(PebbleDetail.self, from: json)
        let draft = PebbleDraft(from: detail)
        #expect(draft.glyphId == glyphId)
    }
```

- [ ] **Step 8.2: Run and verify failure**

Run: `cd apps/ios && xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleDraftFromDetailTests 2>&1 | tail -30`
Expected: FAIL — either `glyph_id` unknown or `draft.glyphId` not set.

- [ ] **Step 8.3: Modify `PebbleDetail`**

In `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift`:

1. Add stored property alongside `renderSvg`:
```swift
    let glyphId: UUID?
```

2. Add to `CodingKeys`:
```swift
        case glyphId = "glyph_id"
```

3. Decode inside `init(from:)`, alongside `renderSvg`:
```swift
        self.glyphId = try container.decodeIfPresent(UUID.self, forKey: .glyphId)
```

- [ ] **Step 8.4: Modify `PebbleDraft.init(from: detail)`**

Add near the end of the init:
```swift
        self.glyphId = detail.glyphId
```

- [ ] **Step 8.5: Run tests — expect pass**

Run: same as Step 8.2.
Expected: pass.

- [ ] **Step 8.6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift
git commit -m "feat(ios): decode glyph_id on PebbleDetail and prefill draft"
```

---

## Phase 2: Services + new edge function

### Task 9: `GlyphService`

**Files:**
- Create: `apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift`

- [ ] **Step 9.1: Create the service**

```swift
import Foundation
import Supabase
import os

/// Thin wrapper over Supabase for the `public.glyphs` table.
///
/// Single-table reads/writes only (see `AGENTS.md` — multi-table ops must
/// become RPCs, but glyphs don't cross table boundaries).
///
/// The `squareShapeId` is hardcoded from the deterministic id pattern in
/// `packages/supabase/supabase/migrations/20260411000006_deterministic_reference_ids.sql`
/// (`md5('pebble_shapes:' || slug)::uuid`). This satisfies the V1 constraint
/// "Glyph zone is a square, no such thing as shape" without a schema change.
struct GlyphService {
    let supabase: SupabaseService

    /// Deterministic UUID from `md5('pebble_shapes:square')` reinterpreted as UUID.
    static let squareShapeId = UUID(uuidString: "3753e7c7-a7dc-5da8-034c-94968e4c7eba")!

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-service")

    /// Fetches the current user's glyphs. Excludes system glyphs (user_id is
    /// null) — those are domain-default fallbacks, not personal carvings.
    func list() async throws -> [Glyph] {
        let rows: [Glyph] = try await supabase.client
            .from("glyphs")
            .select("id, name, strokes, view_box")
            .not("user_id", operator: .is, value: "null")
            .order("created_at", ascending: false)
            .execute()
            .value
        return rows
    }

    /// Inserts a new glyph owned by the current user. Returns the persisted row.
    func create(strokes: [GlyphStroke], name: String? = nil) async throws -> Glyph {
        guard let userId = supabase.client.auth.currentSession?.user.id else {
            Self.logger.error("glyph save without session")
            throw GlyphServiceError.missingSession
        }
        let payload = GlyphInsertPayload(
            userId: userId,
            shapeId: Self.squareShapeId,
            strokes: strokes,
            viewBox: "0 0 200 200",
            name: name
        )
        let created: Glyph = try await supabase.client
            .from("glyphs")
            .insert(payload)
            .select("id, name, strokes, view_box")
            .single()
            .execute()
            .value
        return created
    }
}

enum GlyphServiceError: Error, LocalizedError {
    case missingSession

    var errorDescription: String? {
        switch self {
        case .missingSession: return "Please sign in again."
        }
    }
}
```

- [ ] **Step 9.2: Verify build**

Run: `cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: `** BUILD SUCCEEDED **`.

If `.not("user_id", operator: .is, value: "null")` fails to compile against the Supabase Swift SDK version pinned in `project.yml` (≥ 2.0.0), fall back to `.is("user_id", value: "not.null")` or `.filter("user_id", operator: "not.is", value: "null")`; consult the Supabase Swift SDK docs for the exact operator form in the installed version.

- [ ] **Step 9.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift
git commit -m "feat(ios): add GlyphService for list + create"
```

---

### Task 10: New `compose-pebble-update` edge function

**Files:**
- Create: `packages/supabase/supabase/functions/compose-pebble-update/index.ts`

- [ ] **Step 10.1: Create the function**

```ts
/**
 * Edge function: compose-pebble-update
 *
 * Client-facing counterpart to compose-pebble for the edit flow. Wraps
 * update_pebble RPC + compose-and-write so that editing a pebble (for
 * example, associating a personal glyph) re-renders render_svg atomically
 * from the iOS client's perspective.
 *
 * 1. Auth-forwards the caller's JWT so update_pebble runs as the end user
 *    (ownership check inside the RPC)
 * 2. Calls update_pebble(p_pebble_id, payload)
 * 3. Calls compose-and-write → writes render columns + returns composed output
 * 4. Responds with { pebble_id, render_svg, render_manifest, render_version }
 *
 * On RPC failure: 4xx with the RPC error.
 * On compose failure after successful update: 500 with pebble_id in the body
 * so the iOS client can advance to the detail sheet (soft-success path —
 * mirrors compose-pebble).
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAuthForwardedClient, createAdminClient } from "../_shared/supabase-client.ts";
import { composeAndWriteRender } from "../_shared/compose-and-write.ts";

interface RequestBody {
  pebble_id: string;
  // deno-lint-ignore no-explicit-any
  payload: any;
}

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS });
  }
  if (req.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  let body: RequestBody;
  try {
    body = await req.json();
  } catch (err) {
    console.error("compose-pebble-update: body parse failed:", err);
    return json({ error: "invalid body: not JSON" }, 400);
  }
  if (!body || typeof body !== "object" || !body.pebble_id || !("payload" in body)) {
    console.error("compose-pebble-update: invalid body");
    return json({ error: "invalid body: pebble_id and payload required" }, 400);
  }

  // Step 1: update_pebble with auth-forwarded client
  const authClient = createAuthForwardedClient(req);
  const { error: rpcError } = await authClient.rpc("update_pebble", {
    p_pebble_id: body.pebble_id,
    payload: body.payload,
  });

  if (rpcError) {
    console.error("compose-pebble-update: update_pebble rpc failed:", rpcError);
    return json({ error: rpcError.message }, 400);
  }

  // Step 2: compose + write-back
  const admin = createAdminClient();
  try {
    const rendered = await composeAndWriteRender(admin, body.pebble_id);
    return json({ pebble_id: body.pebble_id, ...rendered }, 200);
  } catch (err) {
    console.error("compose-pebble-update: composeAndWrite failed:", err);
    return json(
      {
        error: `compose failed: ${err instanceof Error ? err.message : String(err)}`,
        pebble_id: body.pebble_id,
      },
      500,
    );
  }
});

// deno-lint-ignore no-explicit-any
function json(body: any, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}
```

- [ ] **Step 10.2: Deno type-check the function**

Run: `cd packages/supabase/supabase/functions && deno check compose-pebble-update/index.ts 2>&1 | tail -10`
Expected: no type errors. If `deno` isn't installed locally, skip this step — Supabase will type-check on deploy.

- [ ] **Step 10.3: Deploy the function**

Run: `cd packages/supabase && npx supabase functions deploy compose-pebble-update`
Expected: `Deployed Function compose-pebble-update`.

If the user has not linked the CLI to a remote project, ask them to run `npm run db:link` from `packages/supabase` first.

- [ ] **Step 10.4: Commit**

```bash
git add packages/supabase/supabase/functions/compose-pebble-update/index.ts
git commit -m "feat(api): add compose-pebble-update edge function for edit flow"
```

---

### Task 11: Switch `EditPebbleSheet` to the new edge function

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`

- [ ] **Step 11.1: Extend the select to include `glyph_id`**

In `EditPebbleSheet.load()`, change the `.select(...)` string (currently at around line 94) to add `glyph_id` among the scalar fields:

```swift
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    render_svg, render_version, glyph_id,
                    emotion:emotions(id, name, color),
                    pebble_domains(domain:domains(id, name)),
                    pebble_souls(soul:souls(id, name)),
                    collection_pebbles(collection:collections(id, name))
                """)
```

- [ ] **Step 11.2: Replace the save body**

Replace `private func save()` with the edge-function version. The new body constructs the same `PebbleUpdatePayload`, wraps it in a `ComposePebbleUpdateRequest`, invokes `compose-pebble-update`, decodes into `ComposePebbleResponse`, updates `renderSvg` from the response (so the animation is fresh even though the sheet is about to dismiss; also useful for future flows that keep the sheet open), then dismisses. Full replacement:

```swift
    private func save() async {
        guard draft.isValid else { return }
        isSaving = true
        saveError = nil

        let payload = PebbleUpdatePayload(from: draft)
        let requestBody = ComposePebbleUpdateRequest(pebbleId: pebbleId, payload: payload)

        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let response: ComposePebbleResponse = try await supabase.client.functions
                .invoke(
                    "compose-pebble-update",
                    options: FunctionInvokeOptions(body: requestBody),
                    decoder: decoder
                )
            self.renderSvg = response.renderSvg ?? self.renderSvg
            onSaved()
            dismiss()
        } catch let functionsError as FunctionsError {
            // Soft-success: update succeeded but compose failed — the row was
            // updated, so advance as if saved and rely on the parent's list
            // refetch to eventually pick up a freshly composed render.
            if case .httpError(let status, _) = functionsError, status >= 500 {
                logger.warning("compose-pebble-update returned \(status, privacy: .public) — advancing on soft-success")
                onSaved()
                dismiss()
            } else {
                logger.error("compose-pebble-update failed: \(functionsError.localizedDescription, privacy: .private)")
                self.saveError = "Couldn't save your changes. Please try again."
                self.isSaving = false
            }
        } catch {
            logger.error("update pebble failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = "Couldn't save your changes. Please try again."
            self.isSaving = false
        }
    }
```

- [ ] **Step 11.3: Replace `UpdatePebbleParams` with `ComposePebbleUpdateRequest`**

Replace the `private struct UpdatePebbleParams` at the bottom of the file with:

```swift
/// Body for the `compose-pebble-update` edge function.
/// Shape: `{ "pebble_id": "...", "payload": { ... update_pebble payload ... } }`.
private struct ComposePebbleUpdateRequest: Encodable {
    let pebbleId: UUID
    let payload: PebbleUpdatePayload

    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
        case payload
    }
}
```

Also remove the `import Supabase` adjustment if it's already imported elsewhere — check that `Supabase` is imported at the top of `EditPebbleSheet.swift`. If not, add `import Supabase` to the imports so `FunctionInvokeOptions` and `FunctionsError` resolve.

- [ ] **Step 11.4: Build + lint**

Run: `cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: build succeeds.

- [ ] **Step 11.5: Manual smoke**

With the app running on the simulator, edit an existing pebble and save without changing anything. Verify:
- The edit dismisses cleanly (no error banner).
- The list refetches on `onSaved()`.
- Server logs show `compose-pebble-update` was invoked.

- [ ] **Step 11.6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift
git commit -m "feat(ios): edit flow uses compose-pebble-update for server render refresh"
```

---

## Phase 3: UI primitives

### Task 12: `GlyphThumbnail` view

**Files:**
- Create: `apps/ios/Pebbles/Features/Glyph/Views/GlyphThumbnail.swift`

- [ ] **Step 12.1: Create the view**

```swift
import SwiftUI

/// Square preview of a glyph. Renders each `GlyphStroke.d` via `SVGPath.path(from:)`
/// inside a 200x200 coordinate space, scaled to the requested side length.
///
/// Used in:
/// - `PebbleFormView` glyph row (32pt)
/// - `GlyphPickerSheet` grid cells (~100pt)
/// - `GlyphsListView` grid cells (~100pt)
/// - `GlyphCarveSheet` "saved" confirmation (200pt)
struct GlyphThumbnail: View {
    let strokes: [GlyphStroke]
    var side: CGFloat = 100
    var strokeColor: Color = .primary
    var backgroundColor: Color = Color.secondary.opacity(0.08)

    var body: some View {
        Canvas { ctx, size in
            // Scale 200x200 glyph coords to the requested frame size.
            let scale = size.width / 200.0
            for stroke in strokes {
                var path = SVGPath.path(from: stroke.d)
                path = path.applying(CGAffineTransform(scaleX: scale, y: scale))
                ctx.stroke(
                    path,
                    with: .color(strokeColor),
                    style: StrokeStyle(
                        lineWidth: stroke.width * scale,
                        lineCap: .round,
                        lineJoin: .round
                    )
                )
            }
        }
        .frame(width: side, height: side)
        .background(backgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

#Preview {
    GlyphThumbnail(
        strokes: [
            GlyphStroke(d: "M30,30 L170,170", width: 6),
            GlyphStroke(d: "M170,30 L30,170", width: 6),
        ],
        side: 120
    )
    .padding()
}
```

- [ ] **Step 12.2: Build**

Run: `cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: build succeeds.

- [ ] **Step 12.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Views/GlyphThumbnail.swift
git commit -m "feat(ios): add GlyphThumbnail for reusable glyph previews"
```

---

### Task 13: `GlyphCanvasView`

**Files:**
- Create: `apps/ios/Pebbles/Features/Glyph/Views/GlyphCanvasView.swift`

- [ ] **Step 13.1: Create the view**

```swift
import SwiftUI

/// The drawing surface. Pure UI — owns no persistence, only the in-progress
/// stroke buffer. The host (`GlyphCarveSheet`) owns the committed strokes
/// array and receives a freshly-simplified `GlyphStroke` via `onStrokeCommit`.
///
/// Coordinate system: the visible canvas is `side × side` points; strokes are
/// serialized to the 200x200 SVG coordinate space. The scale factor is applied
/// when converting to `GlyphStroke.d`.
struct GlyphCanvasView: View {
    let committedStrokes: [GlyphStroke]
    let onStrokeCommit: (GlyphStroke) -> Void
    var side: CGFloat = 280
    var strokeColor: Color = .primary

    @State private var activePoints: [CGPoint] = []

    private static let epsilon = 1.5
    private static let storedWidth = 6.0

    var body: some View {
        Canvas { ctx, size in
            // Already-committed strokes
            let scale = size.width / 200.0
            for stroke in committedStrokes {
                var path = SVGPath.path(from: stroke.d)
                path = path.applying(CGAffineTransform(scaleX: scale, y: scale))
                ctx.stroke(
                    path,
                    with: .color(strokeColor),
                    style: StrokeStyle(
                        lineWidth: stroke.width * scale,
                        lineCap: .round,
                        lineJoin: .round
                    )
                )
            }

            // In-progress stroke
            if activePoints.count > 1 {
                var livePath = Path()
                livePath.move(to: activePoints[0])
                for point in activePoints.dropFirst() {
                    livePath.addLine(to: point)
                }
                ctx.stroke(
                    livePath,
                    with: .color(strokeColor),
                    style: StrokeStyle(
                        lineWidth: Self.storedWidth * scale,
                        lineCap: .round,
                        lineJoin: .round
                    )
                )
            }
        }
        .frame(width: side, height: side)
        .background(Color.secondary.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .local)
                .onChanged { value in
                    let clamped = CGPoint(
                        x: max(0, min(side, value.location.x)),
                        y: max(0, min(side, value.location.y))
                    )
                    activePoints.append(clamped)
                }
                .onEnded { _ in commit() }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Drawing canvas")
        .accessibilityAddTraits(.allowsDirectInteraction)
        .accessibilityValue("\(committedStrokes.count) strokes drawn")
    }

    private func commit() {
        defer { activePoints = [] }
        guard activePoints.count >= 1 else { return }

        let simplified = PathSimplification.simplify(points: activePoints, epsilon: Self.epsilon)
        let scale = 200.0 / Double(side)
        let scaled = simplified.map { CGPoint(x: Double($0.x) * scale, y: Double($0.y) * scale) }
        let d = SVGPath.svgPathString(from: scaled)
        guard !d.isEmpty else { return }

        onStrokeCommit(GlyphStroke(d: d, width: Self.storedWidth))
    }
}

#Preview {
    GlyphCanvasView(
        committedStrokes: [],
        onStrokeCommit: { _ in }
    )
    .padding()
}
```

- [ ] **Step 13.2: Build**

Run: `cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: build succeeds.

- [ ] **Step 13.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Views/GlyphCanvasView.swift
git commit -m "feat(ios): add GlyphCanvasView drawing surface"
```

---

### Task 14: `GlyphCarveSheet`

**Files:**
- Create: `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift`

- [ ] **Step 14.1: Create the view**

```swift
import SwiftUI
import os

/// Full-screen cover for carving a new glyph. Presented from
/// `GlyphPickerSheet` (during pebble record/edit) and from `GlyphsListView`
/// (from the profile page).
///
/// Full-screen cover — not a sheet — so the canvas can't be dismissed by an
/// accidental downward stroke. User exits via Cancel/Save only.
struct GlyphCarveSheet: View {
    let onSaved: (Glyph) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var strokes: [GlyphStroke] = []
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var showDiscardAlert = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-carve")

    private var service: GlyphService { GlyphService(supabase: supabase) }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("New glyph")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { cancelTapped() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving {
                            ProgressView()
                        } else {
                            Button("Save") {
                                Task { await save() }
                            }
                            .disabled(strokes.isEmpty)
                        }
                    }
                }
                .pebblesScreen()
                .alert("Discard your glyph?", isPresented: $showDiscardAlert) {
                    Button("Keep editing", role: .cancel) {}
                    Button("Discard", role: .destructive) { dismiss() }
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        VStack(spacing: 24) {
            Spacer(minLength: 0)

            GlyphCanvasView(
                committedStrokes: strokes,
                onStrokeCommit: { stroke in strokes.append(stroke) }
            )

            if let saveError {
                Text(saveError)
                    .foregroundStyle(.red)
                    .font(.callout)
            }

            HStack(spacing: 24) {
                Button {
                    if !strokes.isEmpty { strokes.removeLast() }
                } label: {
                    Label("Undo", systemImage: "arrow.uturn.backward")
                }
                .disabled(strokes.isEmpty)

                Button(role: .destructive) {
                    strokes.removeAll()
                } label: {
                    Label("Clear", systemImage: "trash")
                }
                .disabled(strokes.isEmpty)
            }
            .buttonStyle(.bordered)

            Spacer(minLength: 0)
        }
        .padding()
    }

    private func cancelTapped() {
        if strokes.isEmpty {
            dismiss()
        } else {
            showDiscardAlert = true
        }
    }

    private func save() async {
        guard !strokes.isEmpty else { return }
        isSaving = true
        saveError = nil
        do {
            let glyph = try await service.create(strokes: strokes)
            onSaved(glyph)
            dismiss()
        } catch {
            logger.error("glyph create failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = "Couldn't save your glyph. Please try again."
            self.isSaving = false
        }
    }
}

#Preview {
    GlyphCarveSheet(onSaved: { _ in })
        .environment(SupabaseService())
}
```

- [ ] **Step 14.2: Build**

Run: `cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: build succeeds.

- [ ] **Step 14.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift
git commit -m "feat(ios): add GlyphCarveSheet full-screen cover"
```

---

### Task 15: `GlyphPickerSheet`

**Files:**
- Create: `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift`

- [ ] **Step 15.1: Create the view**

```swift
import SwiftUI
import os

/// Sheet that lists the user's glyphs and offers to carve a new one.
/// Presented from `PebbleFormView`'s "Glyph" row.
struct GlyphPickerSheet: View {
    let currentGlyphId: UUID?
    let onSelected: (UUID?) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var glyphs: [Glyph] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var showCarveSheet = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-picker")
    private var service: GlyphService { GlyphService(supabase: supabase) }

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 12)]

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Choose a glyph")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { dismiss() }
                    }
                }
                .pebblesScreen()
                .task { await load() }
                .fullScreenCover(isPresented: $showCarveSheet) {
                    GlyphCarveSheet(onSaved: { glyph in
                        glyphs.insert(glyph, at: 0)
                        onSelected(glyph.id)
                        dismiss()
                    })
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load glyphs",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
            .overlay(alignment: .bottom) {
                Button("Retry") { Task { await load() } }
                    .padding()
            }
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    carveNewRow

                    if !glyphs.isEmpty {
                        Text("Your glyphs")
                            .font(.headline)
                            .padding(.top)

                        LazyVGrid(columns: columns, spacing: 12) {
                            ForEach(glyphs) { glyph in
                                Button {
                                    onSelected(glyph.id)
                                    dismiss()
                                } label: {
                                    GlyphThumbnail(
                                        strokes: glyph.strokes,
                                        side: 96,
                                        backgroundColor: glyph.id == currentGlyphId
                                            ? Color.accentColor.opacity(0.15)
                                            : Color.secondary.opacity(0.08)
                                    )
                                }
                                .accessibilityLabel(glyph.name ?? "Untitled glyph")
                            }
                        }
                    }
                }
                .padding()
            }
        }
    }

    private var carveNewRow: some View {
        Button {
            showCarveSheet = true
        } label: {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 8)
                    .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4]))
                    .frame(width: 48, height: 48)
                    .overlay(Image(systemName: "plus"))
                Text("Carve new glyph")
                    .font(.body)
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
            }
            .padding(12)
            .background(Color.secondary.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            self.glyphs = try await service.list()
        } catch {
            logger.error("glyphs list failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    GlyphPickerSheet(currentGlyphId: nil, onSelected: { _ in })
        .environment(SupabaseService())
}
```

- [ ] **Step 15.2: Build**

Run: `cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: build succeeds.

- [ ] **Step 15.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift
git commit -m "feat(ios): add GlyphPickerSheet for pick-or-carve flow"
```

---

## Phase 4: Integration

### Task 16: Move + rewrite `GlyphsListView` (profile)

**Files:**
- Create: `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Lists/GlyphsListView.swift`

- [ ] **Step 16.1: Write the new file**

```swift
import SwiftUI
import os

/// Profile → Glyphs. Grid of thumbnails; toolbar "+" carves a new glyph.
struct GlyphsListView: View {
    @Environment(SupabaseService.self) private var supabase

    @State private var glyphs: [Glyph] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var showCarveSheet = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.glyphs")
    private var service: GlyphService { GlyphService(supabase: supabase) }

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 12)]

    var body: some View {
        content
            .navigationTitle("Glyphs")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showCarveSheet = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Carve new glyph")
                }
            }
            .task { await load() }
            .fullScreenCover(isPresented: $showCarveSheet) {
                GlyphCarveSheet(onSaved: { glyph in
                    glyphs.insert(glyph, at: 0)
                })
            }
            .pebblesScreen()
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load glyphs",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if glyphs.isEmpty {
            ContentUnavailableView(
                "No glyphs yet",
                systemImage: "scribble",
                description: Text("Tap + to carve your first glyph.")
            )
        } else {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(glyphs) { glyph in
                        VStack(spacing: 4) {
                            GlyphThumbnail(strokes: glyph.strokes, side: 96)
                            if let name = glyph.name {
                                Text(name)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                    }
                }
                .padding()
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            self.glyphs = try await service.list()
        } catch {
            logger.error("glyphs fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        GlyphsListView()
            .environment(SupabaseService())
    }
}
```

- [ ] **Step 16.2: Delete the old file**

```bash
git rm apps/ios/Pebbles/Features/Profile/Lists/GlyphsListView.swift
```

- [ ] **Step 16.3: Build**

Run: `cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: build succeeds. `ProfileView`'s `NavigationLink` destination `GlyphsListView()` resolves against the new file (same type name, same module).

- [ ] **Step 16.4: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift apps/ios/Pebbles/Features/Profile/Lists/GlyphsListView.swift
git commit -m "feat(ios): move and rewrite GlyphsListView as thumbnail grid with carve CTA"
```

---

### Task 17: Add the "Glyph" row to `PebbleFormView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`

- [ ] **Step 17.1: Add the row section**

`PebbleFormView` needs new state (the picker presentation + the selected glyph metadata for the thumbnail) and a new Section. Because the view is a `View` struct with `@Binding` and no state-holding facilities beyond `@State`, we add `@State private var showPicker = false` and `@State private var selectedGlyph: Glyph?` and load the selected glyph on appear.

Full replacement of `PebbleFormView.swift` body section — insert this block between the existing "Mood" section and the "Optional" section, and add the state/task at the top of the struct. Exact diff:

Add near the top, after the struct's stored properties:
```swift
    @State private var showPicker = false
    @State private var selectedGlyph: Glyph?

    @Environment(SupabaseService.self) private var supabase
```

In `body`, insert this Section between "Mood" and "Optional":

```swift
            Section("Glyph") {
                Button {
                    showPicker = true
                } label: {
                    HStack(spacing: 12) {
                        if let glyph = selectedGlyph {
                            GlyphThumbnail(strokes: glyph.strokes, side: 32)
                                .accessibilityHidden(true)
                        } else {
                            RoundedRectangle(cornerRadius: 6)
                                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                                .frame(width: 32, height: 32)
                                .foregroundStyle(.secondary)
                        }
                        Text(glyphRowLabel)
                            .foregroundStyle(selectedGlyph == nil ? .secondary : .primary)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                    }
                }
                .buttonStyle(.plain)
                .contextMenu {
                    if draft.glyphId != nil {
                        Button(role: .destructive) {
                            draft.glyphId = nil
                            selectedGlyph = nil
                        } label: {
                            Label("Remove glyph", systemImage: "trash")
                        }
                    }
                }
            }
```

Add these helpers inside the struct (outside `body`):

```swift
    private var glyphRowLabel: String {
        if draft.glyphId == nil { return "Carve or pick a glyph" }
        if let name = selectedGlyph?.name { return name }
        return "Untitled glyph"
    }
```

Chain `.sheet(...)` + `.task(id:)` directly onto the `Form { … }` at the end of `body` (same level as the other modifiers on `Form` — there are none currently, so add them both after the closing `}` of the `Form`). The `.task(id: draft.glyphId)` re-runs every time the glyph selection changes, including the initial appearance:

```swift
        .sheet(isPresented: $showPicker) {
            GlyphPickerSheet(
                currentGlyphId: draft.glyphId,
                onSelected: { id in draft.glyphId = id }
            )
        }
        .task(id: draft.glyphId) { await loadSelectedGlyph() }
```

Add the loader:

```swift
    private func loadSelectedGlyph() async {
        guard let id = draft.glyphId else {
            selectedGlyph = nil
            return
        }
        if selectedGlyph?.id == id { return }
        do {
            let fetched: Glyph = try await supabase.client
                .from("glyphs")
                .select("id, name, strokes, view_box")
                .eq("id", value: id)
                .single()
                .execute()
                .value
            self.selectedGlyph = fetched
        } catch {
            // Non-fatal: the row renders without a thumbnail until the refetch works.
            Logger(subsystem: "app.pbbls.ios", category: "pebble-form")
                .warning("glyph fetch for preview failed: \(error.localizedDescription, privacy: .private)")
        }
    }
```

And add `import os` at the top of the file if not already imported.

- [ ] **Step 17.2: Build**

Run: `cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: build succeeds.

- [ ] **Step 17.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleFormView.swift
git commit -m "feat(ios): add Glyph row to pebble record form"
```

---

### Task 18: Verify `CreatePebbleSheet` + `EditPebbleSheet` pass-through

Both sheets already pass `draft` (which now carries `glyphId`) into `PebbleFormView`, and both use `PebbleCreatePayload(from: draft)` / `PebbleUpdatePayload(from: draft)` — both already encode `glyphId`. No code changes required.

- [ ] **Step 18.1: Verify compile**

Run: `cd apps/ios && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20`
Expected: build succeeds (no-op task).

---

## Phase 5: Verification & PR

### Task 19: Run the full test suite

- [ ] **Step 19.1: Run all iOS tests**

Run: `cd apps/ios && xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1 | tail -40`
Expected: all tests pass, including the 4 new suites (`PathSimplificationTests`, `SVGPathSerializationTests`, `SVGPathParsingTests`, `GlyphInsertPayloadEncodingTests`) and the new tests inside `PebbleCreatePayloadEncodingTests`, `PebbleUpdatePayloadEncodingTests`, `PebbleDraftFromDetailTests`.

### Task 20: Web + repo-wide sanity

- [ ] **Step 20.1: Top-level build + lint**

Run: `cd /Users/alexis/code/pbbls && npm run build && npm run lint 2>&1 | tail -30`
Expected: both pass. Web is unchanged, so any build/lint regression is surprising and should be investigated.

### Task 21: Manual acceptance pass

Run the iOS app on the simulator with a real Supabase session and verify each acceptance criterion from issue #278:

- [ ] **Step 21.1: Acceptance 1 — draw during record**

Tap "+" to open Create Pebble → tap the Glyph row → tap "Carve new glyph" → draw → tap Save.
Expected: sheet dismisses, Glyph row now shows the carved glyph as the selection.

- [ ] **Step 21.2: Acceptance 2 — pick existing during record**

Open Create Pebble → tap the Glyph row → pick an existing glyph from the grid.
Expected: picker dismisses, Glyph row shows the picked glyph.

- [ ] **Step 21.3: Acceptance 3 — glyph appears in saved pebble**

Complete and save a pebble that has a selected glyph. Open the saved pebble from the timeline.
Expected: the pebble's render shows the glyph composed into the stone.

- [ ] **Step 21.4: Acceptance 4 — edit refreshes render**

Open an existing pebble with a domain-default glyph → Edit → pick a personal glyph → Save → open the pebble again.
Expected: the render reflects the new glyph (server recomposed via `compose-pebble-update`).

- [ ] **Step 21.5: Acceptance 5 — save persists**

After carving a glyph during record, go to Profile → Glyphs.
Expected: the glyph appears in the grid.

- [ ] **Step 21.6: Acceptance 6 — profile lists glyphs**

Verify Profile → Glyphs shows all previously-carved glyphs as thumbnails.

- [ ] **Step 21.7: Acceptance 7 — profile creates glyphs**

From Profile → Glyphs, tap "+" → carve → Save.
Expected: new glyph prepends to the grid.

- [ ] **Step 21.8: Warning check — sheet dismissal during carving**

While drawing (finger still down), move to the top of the canvas.
Expected: no sheet dismissal (full-screen cover has no swipe-to-dismiss).

### Task 22: Open the PR

- [ ] **Step 22.1: Push the branch**

```bash
git push -u origin feat/278-ios-glyph-carving
```

- [ ] **Step 22.2: Create the PR**

Labels: `feat`, `core`, `ios`. Milestone: `M23 · TestFlight V1`.

```bash
gh pr create --title "feat(ios): glyph carving" --label feat --label core --label ios --milestone "M23 · TestFlight V1" --body "$(cat <<'EOF'
Resolves #278.

## Summary
- New `Features/Glyph/` feature folder: models, service, utilities (RDP + SVG path), and three SwiftUI views (canvas, carve sheet, picker sheet).
- New "Glyph" row in the pebble record form; opens a picker + "Carve new glyph" CTA.
- `Profile → Glyphs` becomes a thumbnail grid with a toolbar carve button.
- New `compose-pebble-update` edge function so edits recompose `render_svg` server-side — fixes acceptance criterion #4.
- `EditPebbleSheet` switches from the direct `update_pebble` RPC to the new edge function.
- `shape_id` deprecation handled iOS-side only: iOS writes the deterministic `square` shape id on every glyph insert. No schema changes, no web changes.

## Test plan
- [x] Unit tests pass (`PathSimplification`, `SVGPath`, payload encoding, draft round-trip)
- [x] `npm run build` and `npm run lint` pass at repo root
- [ ] Acceptance criteria 1–7 verified in TestFlight (see plan Task 21)
- [ ] `compose-pebble-update` deployed and reachable
EOF
)"
```

- [ ] **Step 22.3: Ship**

Wait for CI, then merge per team convention (usually squash).

---

## Rollback plan

If any phase breaks `main`:

- **Phases 1–4** are local to iOS; revert the offending commit(s) with `git revert`. The iOS app falls back to pre-#278 behavior (no glyph carving, glyph row absent).
- **Phase 2, Task 10** adds a new edge function. Roll back by `supabase functions delete compose-pebble-update` (or leaving it in place — it's client-called only; unused functions cost nothing).
- **Phase 2, Task 11** switches `EditPebbleSheet`'s save path. If the new edge function is failing in prod, revert Task 11's commit to return to the direct `update_pebble` RPC — at the cost of temporarily losing render refresh on edits (already the pre-#278 state).

No database migration, so no schema rollback needed.
