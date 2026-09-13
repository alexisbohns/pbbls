import { fileURLToPath } from "node:url"
import { defineConfig } from "vitest/config"

// Vitest covers the pure-TS modules under `lib/` — the Lab Note parser, the SVG
// path tokenizer and the affine transforms whose output is baked into stored
// glyph geometry. Mirrors apps/web's config; see its comments for the reasoning
// behind `pool: "threads"` (a forked worker can outlive an ungraceful parent and
// keep burning CPU; a thread cannot).
//
// The `@/` alias is resolved here rather than via a plugin: Vitest does not read
// tsconfig paths on its own.
//
// Environment is `node`. `svg-to-strokes` is the one module that needs a DOM
// (`DOMParser`), and it opts in per-file with `@vitest-environment jsdom` rather
// than paying jsdom's startup cost across the whole suite.
//
// jsdom is held at ^26 deliberately: 27+ requires Node >= 22, and CI runs Node
// 20 (the version every workflow in .github/workflows pins). The failure is not
// a version warning — the worker dies with `webidl.util.markAsUncloneable is
// not a function` from jsdom's bundled undici, which reads as a Vitest bug.
export default defineConfig({
  resolve: {
    alias: {
      "@": fileURLToPath(new URL(".", import.meta.url)),
    },
  },
  test: {
    pool: "threads",
    environment: "node",
    include: ["lib/**/*.test.ts"],
  },
})
