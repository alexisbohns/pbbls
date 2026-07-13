// Dev-only gate for the petroglyph wobble experiment (issue #555), mirroring
// iOS's `#if DEBUG` stance (`WobbleFlags`). On in dev, dead-code-eliminated to
// a constant `false` in production builds (Next.js inlines `NODE_ENV`), so the
// spike can never ship by accident. Deleting the experiment means removing this
// folder and reverting the flag-gated call sites (PebbleVisual, StrokeRenderer,
// PebbleOutlineBackdrop).
export const WOBBLE_ENABLED = process.env.NODE_ENV !== "production"
