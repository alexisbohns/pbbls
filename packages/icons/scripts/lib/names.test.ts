import { describe, expect, it } from "vitest"
import { assetName, camelName, constantName, drawableName, pascalName, sourceFile } from "./names"

describe("names", () => {
  it.each([
    ["check-circle", "CheckCircle", "checkCircle", "CHECK_CIRCLE", "ic_pbbls_check_circle", "pbbls-check-circle"],
    ["home.active", "HomeActive", "homeActive", "HOME_ACTIVE", "ic_pbbls_home_active", "pbbls-home-active"],
    ["path.lit-2", "PathLit2", "pathLit2", "PATH_LIT_2", "ic_pbbls_path_lit_2", "pbbls-path-lit-2"],
  ])("%s", (name, pascal, camel, constant, drawable, asset) => {
    expect(pascalName(name)).toBe(pascal)
    expect(camelName(name)).toBe(camel)
    expect(constantName(name)).toBe(constant)
    expect(drawableName(name)).toBe(drawable)
    expect(assetName(name)).toBe(asset)
  })

  it("names the source file after the manifest name", () => {
    expect(sourceFile("home.active")).toBe("home.active.svg")
    expect(sourceFile("trash")).toBe("trash.svg")
  })
})
