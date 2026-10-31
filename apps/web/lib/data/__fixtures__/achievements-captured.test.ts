import { readFileSync } from "node:fs"
import { describe, expect, it } from "vitest"
import {
  DUMPED_CHECK_ACHIEVEMENTS,
  DUMPED_GET_ACHIEVEMENTS,
  DUMPED_GET_ACHIEVEMENTS_PULLED,
  DUMPED_GET_ACHIEVEMENTS_SEEN,
  DUMPED_GET_ACHIEVEMENTS_YEAR,
} from "./achievements-captured"

const DUMPS = new URL("../../../../../packages/supabase/scripts/oracles/achievements/payloads/", import.meta.url)

describe("the dumped achievements payloads", () => {
  it.each([
    ["check-achievements.json", DUMPED_CHECK_ACHIEVEMENTS],
    ["get-achievements.json", DUMPED_GET_ACHIEVEMENTS],
    ["get-achievements-year.json", DUMPED_GET_ACHIEVEMENTS_YEAR],
    ["get-achievements-seen.json", DUMPED_GET_ACHIEVEMENTS_SEEN],
    ["get-achievements-pulled.json", DUMPED_GET_ACHIEVEMENTS_PULLED],
  ])("%s is copied byte for byte", (file, fixture) => {
    expect(fixture).toBe(readFileSync(new URL(file, DUMPS), "utf8"))
  })
})
