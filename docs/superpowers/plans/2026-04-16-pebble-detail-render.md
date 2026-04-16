# Pebble Detail Render in Edit Sheet — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display the server-composed pebble artwork at the top of `EditPebbleSheet` when opening an existing pebble.

**Architecture:** Add an optional `renderSvg` parameter to `PebbleFormView`. When non-nil, render `PebbleRenderView` as a header row at the top of the `Form`. `EditPebbleSheet` passes the value from `PebbleDetail`; `CreatePebbleSheet` passes `nil`.

**Tech Stack:** SwiftUI, iOS 17+, SVGView (exyte)

---

### File Map

- **Modify:** `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` — add optional `renderSvg` param, render artwork header
- **Modify:** `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` — store `renderSvg` from loaded detail, pass to form
- **Modify:** `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` — pass `renderSvg: nil` to form

---

### Task 1: Add `renderSvg` parameter to `PebbleFormView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`

- [ ] **Step 1: Add the property and render the artwork**

Add `let renderSvg: String?` to `PebbleFormView`. At the top of the `Form` body, before the first `Section`, conditionally render the artwork:

```swift
struct PebbleFormView: View {
    @Binding var draft: PebbleDraft
    let emotions: [Emotion]
    let domains: [Domain]
    let souls: [Soul]
    let collections: [PebbleCollection]
    let saveError: String?
    let renderSvg: String?

    var body: some View {
        Form {
            if let svg = renderSvg {
                PebbleRenderView(svg: svg)
                    .frame(maxWidth: .infinity)
                    .frame(height: 260)
                    .padding(.vertical)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            }

            Section {
                // ... existing fields unchanged
```

The `.listRowInsets(EdgeInsets())` removes default Form padding so the artwork spans full width. `.listRowBackground(Color.clear)` removes the row's card background.

- [ ] **Step 2: Verify it compiles (will fail — callers don't pass the new param yet)**

Run: `cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -20`

Expected: compile errors in `CreatePebbleSheet.swift` and `EditPebbleSheet.swift` — missing argument `renderSvg`.

---

### Task 2: Pass `renderSvg` from `EditPebbleSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`

- [ ] **Step 1: Add state and wire it up**

Add a `@State` property and populate it in `load()`:

```swift
@State private var renderSvg: String?
```

In `load()`, after `self.draft = PebbleDraft(from: detail)` (line 134), add:

```swift
self.renderSvg = detail.renderSvg
```

In the `content` builder, update the `PebbleFormView` call to pass the new parameter:

```swift
PebbleFormView(
    draft: $draft,
    emotions: emotions,
    domains: domains,
    souls: souls,
    collections: collections,
    saveError: saveError,
    renderSvg: renderSvg
)
```

---

### Task 3: Pass `nil` from `CreatePebbleSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`

- [ ] **Step 1: Add `renderSvg: nil` to the `PebbleFormView` call**

Update the call site (line 60-67):

```swift
PebbleFormView(
    draft: $draft,
    emotions: emotions,
    domains: domains,
    souls: souls,
    collections: collections,
    saveError: saveError,
    renderSvg: nil
)
```

- [ ] **Step 2: Build and verify**

Run: `cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -20`

Expected: BUILD SUCCEEDED

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleFormView.swift apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift
git commit -m "feat(ui): render pebble artwork in edit sheet (#263)"
```
