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
    // Worker threads, not Vitest 4's default `forks`. A forked worker is a
    // separate OS process, so a parent killed ungracefully (a Ctrl-C that does
    // not propagate, a closed terminal, an IDE stop) leaves children reparented
    // to launchd, burning CPU until someone notices. Threads cannot outlive
    // their process. Safe here because nothing under test touches the DOM,
    // `process.chdir` or a native addon — revisit if this suite ever gains
    // jsdom or a native dependency, which is when `forks` earns its default.
    pool: "threads",
    environment: "node",
    include: ["lib/**/*.test.ts"],
  },
})
