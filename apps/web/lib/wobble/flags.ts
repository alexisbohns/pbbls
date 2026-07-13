// Dev-only gate for the petroglyph wobble experiment (issue #555), the web
// analogue of iOS's `#if DEBUG` (`WobbleFlags`).
//
// The catch: the spike has to be reviewable on a Vercel *preview*/branch
// deploy, but there `NODE_ENV` is "production" exactly like the live site — so
// gating on `NODE_ENV` alone compiles the wobble out of every deploy (which is
// why it looked perfectly straight on the branch URL). Instead:
//
//   - explicit override wins: NEXT_PUBLIC_WOBBLE="1" forces on, "0" forces off;
//   - otherwise on for local dev and any non-production deploy env
//     (NEXT_PUBLIC_VERCEL_ENV — "preview"/"development"), off on the production
//     domain.
//
// Every input is a build-time-inlined `NODE_ENV` / `NEXT_PUBLIC_*` literal, so
// the value is identical on server and client (no hydration mismatch) and the
// production domain never ships the spike by accident. Delete the experiment by
// removing `lib/wobble/` and reverting the flag-gated call sites (PebbleVisual,
// StrokeRenderer, PebbleOutlineBackdrop).
const override = process.env.NEXT_PUBLIC_WOBBLE
const deployEnv = process.env.NEXT_PUBLIC_VERCEL_ENV ?? process.env.NODE_ENV

export const WOBBLE_ENABLED = override === "1" || (override !== "0" && deployEnv !== "production")
