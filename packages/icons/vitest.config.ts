import { defineConfig } from "vitest/config"

export default defineConfig({
  test: {
    // Worker threads, not Vitest's default `forks`, for the web config's
    // reason: a forked worker outlives a parent killed ungracefully, a thread
    // cannot. Nothing here touches the DOM, `process.chdir` or a native addon.
    pool: "threads",
    environment: "node",
    include: ["scripts/**/*.test.ts"],
  },
})
