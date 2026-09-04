// Whether the petroglyph wobble (issue #555) draws.
//
// This was a dev-only gate — on for local dev and preview deploys, off on the
// production domain — which is how the spike was reviewed without shipping it.
// It now ships. The valence fan (#729) draws real stones, and making the picker
// the one always-wobbly surface in an otherwise smooth app was the worse of the
// two inconsistencies. iOS promoted its own flag for the same reason in #727,
// and Android has baked the wobble into internal-testing releases since
// 2026-07-14, so all three surfaces now draw the same stone.
//
// Kept as a constant rather than deleted, so the look is still switchable in
// one place — the same posture iOS's `WobbleFlags` takes. Turning it off is
// this line; deleting the experiment is removing `lib/wobble/` and reverting
// the flag-gated call sites (`PebbleVisual`, `PathStone`, `StrokeRenderer`,
// `PebbleOutlineBackdrop`, `lib/valence/stone-art`).
//
// A build-time constant, so the value is identical on the server and the client
// (no hydration mismatch) and dead branches fold away.
export const WOBBLE_ENABLED = true
