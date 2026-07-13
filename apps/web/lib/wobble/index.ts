// Petroglyph wobble (issue #555, wobble only) — pure-TS, SSR-safe web port of
// the iOS spike (`apps/ios/Pebbles/Features/Path/Render/Wobble/`, PR #556).
// Dev-only experiment: see `flags.ts`. Deleting it means removing this folder
// and reverting the three flag-gated call sites.

export { WOBBLE_ENABLED } from "./flags"
export { wobblePebbleSvg, wobbleGlyphInk, wobbleBackdrop } from "./renderer"
