# Pebble Detail Render in Edit Sheet

**Issue:** #263 — Render the Pebble in detail view
**Date:** 2026-04-16

## Summary

Display the server-composed pebble artwork when opening an existing pebble's edit sheet. The artwork already exists in the DB (`render_svg` column) and is already fetched by `EditPebbleSheet`'s `load()` via `PebbleDetail`. The change surfaces it visually.

## Design

### Approach

Add an optional `renderSvg: String?` parameter to `PebbleFormView`. When non-nil, render `PebbleRenderView` as a header row at the top of the `Form`, before the first `Section`. This keeps a single scroll container and avoids nesting scrollable views.

### Changes

**`PebbleFormView.swift`**
- Add `let renderSvg: String?` property
- At the top of the `Form` body (before the first `Section`), conditionally render `PebbleRenderView(svg:)` with the same sizing as `PebbleDetailSheet`: 260pt height, full width, vertical padding

**`EditPebbleSheet.swift`**
- Add `@State private var renderSvg: String?`
- In `load()`, assign `self.renderSvg = detail.renderSvg` alongside the draft prefill
- Pass `renderSvg` to `PebbleFormView`

**`CreatePebbleSheet.swift`**
- Pass `renderSvg: nil` to `PebbleFormView` (no artwork exists pre-save)

### What doesn't change

- `PebbleDetailSheet` (post-create reveal) — untouched
- `PebbleRenderView` — reused as-is
- Backend / edge functions — no changes needed
- Data fetching — `EditPebbleSheet` already fetches `render_svg` via `PebbleDetail`

### Edge cases

- **Legacy pebbles without a render:** `renderSvg` is nil, artwork section simply doesn't appear. Form renders exactly as before.
- **Soft-success pebbles (render failed on create):** Same as above — nil svg, no artwork shown.

## Acceptance criteria

- Opening an existing pebble with a render shows the artwork above the form fields
- Opening an existing pebble without a render shows the form as before (no empty space)
- Creating a new pebble still works identically (no artwork in create flow)
