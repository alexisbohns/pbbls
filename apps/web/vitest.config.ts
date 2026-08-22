import { fileURLToPath } from "node:url"
import { defineConfig } from "vitest/config"

// Vitest covers the pure-TS modules under `lib/` (SSR-safe, no DOM) — the wobble
// golden test and the Path layout grouping. Node environment, no jsdom: nothing
// under test touches the DOM.
//
// The `@/` alias is resolved here rather than via a plugin, because the modules
// under test import sibling `lib/` code through it (path-layout → pebble-geometry)
// and Vitest does not read tsconfig paths on its own.
export default defineConfig({
  resolve: {
    alias: {
      "@": fileURLToPath(new URL(".", import.meta.url)),
    },
  },
  test: {
    environment: "node",
    include: ["lib/**/*.test.ts"],
  },
})
