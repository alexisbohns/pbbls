/**
 * The Android glyphs emitter (spec D7, §2): the forms data as a raw resource,
 * the enum of names and the enum of state tokens. The enum
 * reads no resource ids, so its initializer stays far under the JVM's 64 KB
 * method limit that #1606 hit (950 entries of one string each).
 */
import { GENERATED_NOTICE, type Emitter } from "./emit"
import { loadGlyphsOnce, nativeGlyphData } from "./emit-glyphs"
import type { GlyphData } from "./forms"
import { constantName } from "./names"
import { loadStates, type StateTable } from "./states"

const KOTLIN_DIR = "apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem"
export const ANDROID_GLYPH_DATA = "apps/android/app/src/main/res/raw/pbbls_glyphs.json"
export const ANDROID_GLYPH_KOTLIN = `${KOTLIN_DIR}/PbblsGlyph.kt`
export const ANDROID_STATE_KOTLIN = `${KOTLIN_DIR}/PbblsGlyphState.kt`

/** ktlint's stock `max-line-length` for a generated KDoc or entry line. */
const LINE_WIDTH = 140
const INDENT = "    "
const BODY_PREFIX = `${INDENT} * `

/** A KDoc on one line when it fits, else word-wrapped under `LINE_WIDTH`. */
export function kdocLines(text: string): string[] {
  const words = text.split(/\s+/).filter((w) => w.length > 0)
  const single = `${INDENT}/** ${words.join(" ")} */`
  if (single.length <= LINE_WIDTH) return [single]
  const lines: string[] = []
  let line = ""
  for (const word of words) {
    if (line.length > 0 && BODY_PREFIX.length + line.length + 1 + word.length > LINE_WIDTH) {
      lines.push(line)
      line = word
    } else {
      line = line.length === 0 ? word : `${line} ${word}`
    }
  }
  if (line.length > 0) lines.push(line)
  return [`${INDENT}/**`, ...lines.map((l) => BODY_PREFIX + l), `${INDENT} */`]
}

function glyphKotlin(glyphs: readonly GlyphData[]): string {
  const entries = glyphs.map((g) => [...kdocLines(g.meaning), `    ${constantName(g.name)}("${g.name}"),`].join("\n"))
  return `// ${GENERATED_NOTICE}
package app.pbbls.android.core.designsystem

/**
 * The hand-drawn Pebbles glyphs (\`packages/icons\`): one entry per drawing,
 * [manifestName] being its manifest name. Its paths and forms are in
 * \`res/raw/pbbls_glyphs.json\`; draw it with [PbblsGlyphImage].
 */
enum class PbblsGlyph(
    val manifestName: String,
) {
${entries.join("\n\n")}
    ;

    companion object {
        private val byManifestName = entries.associateBy { it.manifestName }

        /** The glyph whose [manifestName] is [name], or null. */
        fun fromManifestName(name: String): PbblsGlyph? = byManifestName[name]
    }
}
`
}

function stateKotlin(states: StateTable): string {
  return `// ${GENERATED_NOTICE}
package app.pbbls.android.core.designsystem

/**
 * The interaction states of \`packages/icons/states.json\` (spec D6). Each
 * names the forms it shows, first available wins; \`PbblsGlyph.form(state, k)\`
 * resolves one, the same way on every surface. [key] is the table's name.
 */
enum class PbblsGlyphState(
    val key: String,
) {
${Object.keys(states).map((s) => `    ${constantName(s)}("${s}"),`).join("\n")}
}
`
}

/** The three Android files for these glyphs and states. */
export function androidGlyphFiles(glyphs: readonly GlyphData[], states: StateTable): Map<string, string> {
  return new Map([
    [ANDROID_GLYPH_DATA, nativeGlyphData(glyphs, states)],
    [ANDROID_GLYPH_KOTLIN, glyphKotlin(glyphs)],
    [ANDROID_STATE_KOTLIN, stateKotlin(states)],
  ])
}

export function androidGlyphsEmitter(packageRoot: string): Emitter {
  return {
    name: "android-glyphs",
    owned: { dirs: [], files: [ANDROID_GLYPH_DATA, ANDROID_GLYPH_KOTLIN, ANDROID_STATE_KOTLIN], patterns: [] },
    emit: () => androidGlyphFiles(loadGlyphsOnce(packageRoot), loadStates(packageRoot)),
  }
}
