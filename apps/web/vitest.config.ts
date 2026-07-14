import { defineConfig } from "vitest/config"

// Vitest covers the pure-TS `lib/wobble` module (SSR-safe, no DOM). Kept minimal
// and node-environment: the wobble golden test needs no jsdom or path aliases.
export default defineConfig({
  test: {
    environment: "node",
    include: ["lib/**/*.test.ts"],
  },
})
