/**
 * The iOS glyphs emitter (spec D7, §2): the forms data as an app resource, the
 * enum of names and the enum of state tokens. The old asset catalog and
 * `PbblsIcon` stay with `iosEmitter` until part 7 removes them.
 */
import { GENERATED_NOTICE, type Emitter } from "./emit"
import { loadGlyphsOnce } from "./emit-glyphs"
import { docLines, swiftCase } from "./emit-ios"
import type { GlyphData } from "./forms"
import { loadStates, type StateTable } from "./states"

export const IOS_GLYPH_DATA = "apps/ios/Pebbles/Resources/pbbls-glyphs.json"
export const IOS_GLYPH_SWIFT = "apps/ios/Pebbles/Theme/PbblsGlyph.swift"
export const IOS_STATE_SWIFT = "apps/ios/Pebbles/Theme/PbblsGlyphState.swift"

function nativeData(glyphs: readonly GlyphData[], states: StateTable): string {
  const byName: Record<string, Omit<GlyphData, "name" | "meaning" | "family" | "tags">> = {}
  for (const { name, meaning: _m, family: _f, tags: _t, ...forms } of glyphs) byName[name] = forms
  return JSON.stringify({ notice: GENERATED_NOTICE, version: 1, states, glyphs: byName }) + "\n"
}

function glyphSwift(glyphs: readonly GlyphData[]): string {
  const cases = glyphs.map((g) => [...docLines(g.meaning), `    case ${swiftCase(g.name)} = "${g.name}"`].join("\n"))
  return `// ${GENERATED_NOTICE}
// swiftlint:disable file_length type_body_length

/// The hand-drawn Pebbles glyphs (\`packages/icons\`): one case per drawing, the
/// raw value being its manifest name. Its paths and forms are in
/// \`pbbls-glyphs.json\`; draw it with \`PbblsGlyphView\`.
enum PbblsGlyph: String, CaseIterable, Sendable {
${cases.join("\n")}
}
`
}

function stateSwift(states: StateTable): string {
  return `// ${GENERATED_NOTICE}

/// The interaction states of \`packages/icons/states.json\` (spec D6). Each
/// names the forms it shows, first available wins; \`PbblsGlyph.form(for:k:)\`
/// resolves one, the same way on every surface.
enum PbblsGlyphState: String, CaseIterable, Sendable {
${Object.keys(states).map((s) => `    case ${s}`).join("\n")}
}
`
}

/** The three iOS files for these glyphs and states. */
export function iosGlyphFiles(glyphs: readonly GlyphData[], states: StateTable): Map<string, string> {
  return new Map([
    [IOS_GLYPH_DATA, nativeData(glyphs, states)],
    [IOS_GLYPH_SWIFT, glyphSwift(glyphs)],
    [IOS_STATE_SWIFT, stateSwift(states)],
  ])
}

export function iosGlyphsEmitter(packageRoot: string): Emitter {
  return {
    name: "ios-glyphs",
    owned: { dirs: [], files: [IOS_GLYPH_DATA, IOS_GLYPH_SWIFT, IOS_STATE_SWIFT], patterns: [] },
    emit: () => iosGlyphFiles(loadGlyphsOnce(packageRoot), loadStates(packageRoot)),
  }
}
