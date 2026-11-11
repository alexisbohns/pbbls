/**
 * The iOS glyphs emitter (spec D7, §2): the forms data as an app resource, the
 * enum of names and the enum of state tokens.
 */
import { GENERATED_NOTICE, type Emitter } from "./emit"
import { loadGlyphsOnce, nativeGlyphData } from "./emit-glyphs"
import type { GlyphData } from "./forms"
import { camelName } from "./names"
import { loadStates, type StateTable } from "./states"

/** SwiftLint's `line_length` warning threshold, which a generated doc line stays under. */
const LINE_WIDTH = 120
const DOC_PREFIX = "    /// "

/** Word-wraps a doc comment so no line passes `LINE_WIDTH`; a single word longer than that stands alone. */
export function docLines(text: string): string[] {
  const lines: string[] = []
  let line = ""
  for (const word of text.split(/\s+/).filter((w) => w.length > 0)) {
    if (line.length > 0 && DOC_PREFIX.length + line.length + 1 + word.length > LINE_WIDTH) {
      lines.push(line)
      line = word
    } else {
      line = line.length === 0 ? word : `${line} ${word}`
    }
  }
  if (line.length > 0) lines.push(line)
  return lines.map((l) => DOC_PREFIX + l)
}

/** Swift's reserved words: a case so named is written in backticks (`case \`case\``). */
const SWIFT_KEYWORDS = new Set([
  "as", "associatedtype", "break", "case", "catch", "class", "continue", "default", "defer", "deinit", "do", "else",
  "enum", "extension", "fallthrough", "false", "fileprivate", "for", "func", "guard", "if", "import", "in", "init",
  "inout", "internal", "is", "let", "nil", "operator", "private", "protocol", "public", "repeat", "rethrows", "return",
  "self", "Self", "static", "struct", "subscript", "super", "switch", "throw", "throws", "true", "try", "typealias",
  "var", "where", "while",
])

/** `check-circle` → `checkCircle`, `case` → `` `case` ``. */
export function swiftCase(name: string): string {
  const spelled = camelName(name)
  return SWIFT_KEYWORDS.has(spelled) ? `\`${spelled}\`` : spelled
}

export const IOS_GLYPH_DATA = "apps/ios/Pebbles/Resources/pbbls-glyphs.json"
export const IOS_GLYPH_SWIFT = "apps/ios/Pebbles/Theme/PbblsGlyph.swift"
export const IOS_STATE_SWIFT = "apps/ios/Pebbles/Theme/PbblsGlyphState.swift"

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
    [IOS_GLYPH_DATA, nativeGlyphData(glyphs, states)],
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
