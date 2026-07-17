# Android glyph studio & store — carve, market, store, tabbed picker

> Milestone **M43 · Android Glyph Studio & Store**, issues #584 (A · carve), #585 (B · market), #586 (C · store + drawer), #587 (D · tabbed picker, closes #549); design doc `docs/superpowers/specs/2026-07-16-android-glyph-studio-store-design.md` (#583, D1–D10). One plan document — the sub-projects landed as one continuous chain under the merge-on-green mandate.

## Deliverables (as landed)

- **A (#584):** `CarvePoint`/`PathSimplification` (clamped-segment RDP, strict ε, max-in-both-halves) + `SvgPathSerializer` (byte parity; whole numbers decimal-less, `%.4g` strip) — fixture vectors simulated green pre-commit; `GlyphCarveScreen` (full-screen cover, hard-white 280dp canvas, committed strokes re-rendered via the shared `GlyphImage`, raw in-progress polyline, single-tap dots, undo/clear, discard alert); `GlyphService.create`/`updateName` + `normalizedName` (JVM-tested).
- **B (#585):** `GlyphMarketService` (three verbatim iOS reads; Mine appends system glyphs — D7; Commu's server `.neq` + the view's non-exclusion documented), `buy_glyph` decoded from scalar jsonb, `glyphMarketErrorMessage` (pure, ordered substring table), `PathStatsService.applyKarmaBalance` (D5), wire-row decode tests (double `created_at` nesting, fractional seconds).
- **C (#586):** `GlyphsListScreen` pushed from the Profile Glyphs tile (last D11 reversal) with per-tab stale-while-refetch caches; `GlyphTabBar` floating pill; grid cells with price badges; rename dialog with optimistic update + rollback; `GlyphDetailDrawer`/`GlyphSwapPanel` + `SlideToConfirm` (JVM-tested `SlideMath`; haptic-only feedback per D6, success fired pre-RPC — named iOS quirk); swap → balance applied, Commu item dropped, Owned invalidated.
- **D (#587):** `GlyphPickerSheet` rebuilt on the tabbed surface with its M39 API intact (three call sites untouched); inline buy and carve as **content swaps** inside the caller's single sheet (D5 adaptation — dismiss gestures unwind to the grid first); buy → select → hand control back on first `onSwapped`; carve → fresh glyph inserted into Mine + selected; Commu client-filters `!owned`. `GlyphService.list()` is now caller-less (kept — it remains the documented attachable-set API).

## Verification

CI green (serializer/RDP/market/error-map/SlideMath suites + galleries: carve canvas, tab bar, slider states, drawer swap/owned); on device (maintainer): carve → identical render on web → appears in picker; buy round-trip with live balance; rename + clear-name; picker round-trips from pebble form, soul form, Settings; #549 closes; fr pass.

## Lessons learned

- (fill at review)
