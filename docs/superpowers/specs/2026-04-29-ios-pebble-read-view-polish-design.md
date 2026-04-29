# iOS Pebble Read View — Polish (Issue #331)

## Context

The current iOS pebble read view (`PebbleReadView` + `PebbleDetailSheet`) is functional but visually flat: pebble + title stacked above a full-width photo, then a list of full-width metadata rows. Issue #331 calls for a polished layout where the photo is a 16:9 banner with floating chrome, the pebble sits in a small overlapping box, and metadata reads as inline pills — closer to a finished product surface than an internal tool.

This spec covers the iOS app only. Mockups (provided in the issue and chat) define the target.

## Goals

- Replace the stacked layout with a banner-led composition: 16:9 photo banner, pebble in a fixed-size box overlapping the photo's bottom edge, smaller title, inline pill metadata.
- Keep the privacy + edit affordances in the navigation toolbar but restyle them as floating chips so they sit naturally over the photo.
- Render emotion/domain/collections as inline pills (wrapping when they overflow), and souls as a separate inline pill row below the description.
- Keep loading/error/edit-reload behavior in `PebbleDetailSheet` unchanged.

## Non-goals

- No carousel or gallery for multiple snaps. Banner shows the first snap, same as today.
- No changes to data fetching, the RPCs feeding it, or the `PebbleDetail` model.
- No changes to `EditPebbleSheet` or `CreatePebbleSheet`.
- No web parity work.

## Layout

### With photo

Top to bottom in the scroll content:

1. **Banner zone** (`PebbleReadBanner`)
   - 16:9 photo, full content width, corner radius 24pt, `.fill` content mode, clipped.
   - **Pebble box** overlaps the bottom of the photo: 120×120pt square, 24pt corner radius, fill `Color.pebblesBackground`, no stroke. Horizontally centered. The box's vertical center sits on the photo's bottom edge — half over photo, half below.
   - Pebble shape renders inside the box, scaled by `detail.valence.sizeGroup.renderHeight` applied within the box's interior (with ~12pt inner padding so the largest valence still breathes).
2. **Title block** (`PebbleReadTitle`)
   - Title: Ysabeau-SemiBold, 24pt, centered, multi-line, `Color.pebblesForeground`.
   - Date below: `.caption`, tracking 1.2, uppercase, `Color.pebblesMutedForeground`. Format unchanged from today (`weekday/month/day/year · hour/minute`, locale-aware).
3. **Pill row** (`PebblePillFlow` of `PebbleMetaPill`)
   - Always: emotion pill (`.emotion(color:)` style — fill = emotion color, white icon + label).
   - Always: domain pill — `.neutral` when set, `.unset` (dashed) when empty with the localized "No domain" label.
   - When non-empty: collections pill, `.neutral`, comma-joined names, folder icon.
   - Wraps with `FlowLayout` when overflowing; individual labels don't truncate.
4. **Description** — body text, serif 17pt, `Color.pebblesForeground`, left-aligned, full content width. Rendered only when non-empty.
5. **Souls row** (`PebblePillFlow` of `PebbleMetaPill`) — one `.neutral` pill per soul, glyph + name. Rendered only when souls exist; wraps via `FlowLayout`.

### Without photo

`PebbleReadBanner` switches to its no-photo variant:

- No photo, no box.
- Pebble centered on the page background, sized to the same 120pt-tall footprint so vertical rhythm holds across both states.
- Pebble still scales by valence within that footprint.

Everything below the banner is identical to the with-photo case.

### Toolbar (kept, restyled)

`PebbleDetailSheet` keeps its `NavigationStack` toolbar:

- Top-leading: privacy chip (renders only once `detail` is loaded).
- Top-trailing: Edit button (disabled until `detail` is loaded — unchanged).

Visual restyle:

- Privacy chip: 36pt circular, translucent light surface fill (works on photo and on page bg), no border, lock icon centered.
- Edit chip: pill, 36pt height, ~14pt horizontal padding, same translucent fill, label `.subheadline` weight `.medium`.
- Nav bar background: transparent, so chips appear to float over the photo (and sit cleanly on the page bg in the no-photo case).

## Component breakdown

New, in `apps/ios/Pebbles/Features/Path/Read/`:

- **`PebbleReadBanner.swift`** — top zone. Inputs: optional first-snap storage path, render SVG, emotion color (hex), valence. Renders the photo+box composition or the no-photo pebble-only variant.
- **`PebbleReadTitle.swift`** — title + date. Inputs: name (`String`), happenedAt (`Date`). Owns the date formatting (moved out of today's `PebbleReadHeader`).
- **`PebblePillFlow.swift`** — `Layout`-conforming flow container. Wraps children to multiple lines with consistent horizontal + vertical gaps. Used for both the metadata row and the souls row.
- **`PebbleMetaPill.swift`** — atomic pill. Inputs: `icon` (system name or glyph view), `label` (`LocalizedStringResource`), `style` (`.emotion(color: Color)`, `.neutral`, `.unset`). Replaces `PebbleMetadataRow`. Owns its own hex-color helper (moved off `PebbleReadView`).

Modified:

- **`PebbleReadView.swift`** — slimmed to compose the four new components in the order above. Inline `metadataBlock` and the hex-color extension are removed.
- **`PebbleDetailSheet.swift`** — toolbar restyle (chip styling on the privacy badge call site + Edit button), nav bar background transparent.
- **`PebblePrivacyBadge.swift`** — adds a `.chip` style variant (translucent light surface, 36pt circular, no border) used by the toolbar in `PebbleDetailSheet`. The default style stays available for any other caller.

Deleted:

- **`PebbleReadHeader.swift`** — replaced by `PebbleReadBanner` + `PebbleReadTitle`.
- **`PebbleReadPicture.swift`** — subsumed by `PebbleReadBanner`.
- **`PebbleMetadataRow.swift`** — replaced by `PebbleMetaPill`.

`PebbleDetail`, `PebbleRenderView`, `PebbleDetailSheet`'s data load, and `EditPebbleSheet` are untouched.

## Visual specs

Concrete starting values (tunable in implementation, but committed in the spec):

**Banner**
- Photo: 16:9, full content width, corner radius 24pt, `.fill` clipped.
- Pebble box: 120×120pt, corner radius 24pt, fill `Color.pebblesBackground`, no stroke.
- Box overlap: vertical center on photo's bottom edge.
- No-photo case: 120pt-tall zone, pebble centered, no visible box.
- Pebble interior padding: ~12pt so the largest valence still breathes.

**Title block**
- Title: `Ysabeau-SemiBold`, 24pt, centered, `Color.pebblesForeground`.
- Date: `.caption`, tracking 1.2, uppercase, `Color.pebblesMutedForeground`.
- Title→date spacing: 6pt. Banner→title spacing: 20pt.

**Pills**
- Height 32pt, horizontal padding 12pt, icon→label gap 6pt, fully rounded (height/2).
- Row gap (horizontal + vertical when wrapping): 8pt.
- Label font: `.subheadline` weight `.medium`.
- `.emotion(color:)`: fill = emotion color, foreground = white.
- `.neutral`: fill = `Color.pebblesMuted` (or equivalent surface token), foreground = `Color.pebblesForeground`.
- `.unset`: same fill as `.neutral`, plus 1pt dashed stroke, muted foreground.

**Description**
- Unchanged: serif 17pt regular, `Color.pebblesForeground`, left-aligned.
- Pill row→description spacing: 20pt. Description→souls spacing: 20pt.

**Toolbar chips**
- Privacy chip: 36pt circular, translucent light surface, no border.
- Edit chip: pill, 36pt height, ~14pt horizontal padding, same fill, `.subheadline` weight `.medium`.
- Nav bar: transparent background.

**Outer**
- `PebbleReadView` keeps its current 16pt horizontal / 32pt bottom scroll padding.

## Edge cases & states

- **Loading / error**: unchanged. `PebbleDetailSheet` shows `ProgressView` while loading, error VStack with Retry on failure. Toolbar Edit stays disabled until `detail` loads. Privacy chip in toolbar renders only when `detail` is loaded.
- **No photo**: banner renders pebble-only variant; everything below identical.
- **No description**: description view isn't rendered; the surrounding `VStack` collapses naturally so the souls row (if any) sits the standard 20pt below the pill row.
- **No domain**: domain pill renders in `.unset` (dashed) style with localized "No domain" — matches today.
- **No collections**: collections pill not rendered.
- **No souls**: souls row not rendered.
- **Long title**: wraps, centered.
- **Long pill labels**: pill row wraps to a second line via `PebblePillFlow`. Labels don't truncate.
- **Many souls**: souls row wraps via `PebblePillFlow`.
- **Multiple snaps**: only the first snap is shown — gallery out of scope.
- **Edit reload**: unchanged. `EditPebbleSheet` saves → `PebbleDetailSheet` reloads detail → `PebbleReadView` re-renders → parent `onPebbleUpdated` fires.

## Accessibility

- Banner photo: decorative, `accessibilityHidden(true)`.
- Pebble shape inside the banner: also `accessibilityHidden(true)` — it's redundant with the title context.
- Pills: `accessibilityElement(children: .combine)` so VoiceOver reads each pill as one element.
- Toolbar chips: keep button semantics for Edit; privacy chip stays a status badge (non-interactive).

## Localization

- New strings: none beyond what already exists (`"No domain"` is reused).
- All `LocalizedStringResource` literals continue auto-extracting through `SWIFT_EMIT_LOC_STRINGS=YES`.
- `Localizable.xcstrings` to be checked in Xcode before PR per project rules.

## Testing & previews

No automated tests in V1 (matching project policy). Each new component ships with Xcode `#Preview` blocks covering:

- `PebbleReadBanner`: with photo, without photo, large valence, small valence.
- `PebbleReadTitle`: short title, long multi-line title.
- `PebblePillFlow`: a few pills (no wrap), many pills (wraps to 2+ lines).
- `PebbleMetaPill`: each style (`.emotion`, `.neutral`, `.unset`).
- `PebbleReadView`: combined preview with full fixture data and another with no-photo / no-description / no-souls.

## Out of scope

- Web pebble read view changes.
- Snap gallery / carousel.
- Changes to `PebbleDetail` data shape, `select(...)` query, or RPCs.
- Visual changes to `EditPebbleSheet`, `CreatePebbleSheet`, `PathView`.
- New emotion/domain/collection/soul interactions (tap targets, navigation from pills).
