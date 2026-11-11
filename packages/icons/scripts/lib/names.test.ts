import { describe, expect, it } from "vitest"
import { camelName, constantName, pascalName, sourceFile } from "./names"

describe("names", () => {
  it.each([
    ["check-circle", "CheckCircle", "checkCircle", "CHECK_CIRCLE"],
    ["home.active", "HomeActive", "homeActive", "HOME_ACTIVE"],
    ["path.lit-2", "PathLit2", "pathLit2", "PATH_LIT_2"],
  ])("%s", (name, pascal, camel, constant) => {
    expect(pascalName(name)).toBe(pascal)
    expect(camelName(name)).toBe(camel)
    expect(constantName(name)).toBe(constant)
  })

  it("names the source file after the manifest name", () => {
    expect(sourceFile("home.active")).toBe("home.active.svg")
    expect(sourceFile("trash")).toBe("trash.svg")
  })
})
