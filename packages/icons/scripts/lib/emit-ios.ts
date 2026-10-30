/**
 * The iOS emitter (spec §5, D9): `apps/ios/Pebbles/Resources/Icons.xcassets/`
 * with one `pbbls-<name>.imageset` per icon (the SVG sized 24×24 with
 * `currentColor` replaced by `#000000`, since a template image keeps only
 * alpha and CoreSVG does not resolve `currentColor`), and
 * `apps/ios/Pebbles/Theme/PbblsIcon.swift` (the enum and `PbblsIconImage`).
 * Every `Contents.json` is written in Xcode's own style so Xcode never
 * rewrites it.
 */
import { GENERATED_NOTICE, type EmittedIcon, type Emitter } from "./emit"
import { assetName, camelName } from "./names"
import { type ParsedPath } from "./parse"

export const CATALOG_DIR = "apps/ios/Pebbles/Resources/Icons.xcassets"
export const SWIFT_FILE = "apps/ios/Pebbles/Theme/PbblsIcon.swift"

const CATALOG_CONTENTS = `{
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
`

function imagesetContents(file: string): string {
  return `{
  "images" : [
    {
      "filename" : "${file}",
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  },
  "properties" : {
    "preserves-vector-representation" : true,
    "template-rendering-intent" : "template"
  }
}
`
}

function pathElement(path: ParsedPath): string {
  const attrs = [`d="${path.d}"`]
  if (path.filled) attrs.push('fill="#000000"')
  if (path.evenOdd) attrs.push('fill-rule="evenodd"')
  if (path.unstroked) attrs.push('stroke="none"')
  return `  <path ${attrs.join(" ")}/>`
}

export function imagesetSvg(icon: EmittedIcon): string {
  const root =
    '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#000000" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">'
  return `${root}\n${icon.paths.map(pathElement).join("\n")}\n</svg>\n`
}

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

function caseLines(icon: EmittedIcon): string {
  const twin = icon.variant === null ? "" : ` (the \`${icon.variant}\` twin)`
  return [...docLines(icon.meaning + twin), `    case ${swiftCase(icon.name)} = "${icon.name}"`].join("\n")
}

export function swiftFile(icons: EmittedIcon[]): string {
  return `// ${GENERATED_NOTICE}
// swiftlint:disable file_length
import SwiftUI

/// The hand-drawn Pebbles icon set (\`packages/icons\`): one case per emitted
/// icon and twin, the raw value being the manifest name. Each case is a
/// template image in \`Icons.xcassets\`; draw it with \`PbblsIconImage\`.
enum PbblsIcon: String, CaseIterable, Sendable {
${icons.map(caseLines).join("\n")}

    /// The image's name in \`Icons.xcassets\`: \`pbbls-<name>-<variant>\`.
    var assetName: String {
        "pbbls-" + rawValue.split(whereSeparator: { $0 == "." || $0 == "-" }).joined(separator: "-")
    }

    /// The manifest row this case belongs to: its own name, or its base's for a twin.
    var base: String {
        String(rawValue.prefix { $0 != "." })
    }

    /// The row's \`active\` twin (the lit seal), or nil when it has none.
    var activeTwin: PbblsIcon? {
        PbblsIcon(rawValue: "\\(base).active")
    }

    /// The row's \`lit-n\` twin (n stones lit, 1...3), or nil when it has none.
    func lit(_ n: Int) -> PbblsIcon? {
        PbblsIcon(rawValue: "\\(base).lit-\\(n)")
    }
}

/// A \`PbblsIcon\` drawn as a template image on a square side that follows
/// Dynamic Type through \`@ScaledMetric\` (24pt at the default size, relative to
/// \`.body\` unless told otherwise). It takes the ambient foreground style unless
/// \`style\` is given, and is hidden from accessibility unless \`label\` is.
struct PbblsIconImage: View {
    private let icon: PbblsIcon
    private let style: AnyShapeStyle?
    private let label: LocalizedStringResource?
    @ScaledMetric private var side: CGFloat

    init(_ icon: PbblsIcon, size: CGFloat = 24, relativeTo textStyle: Font.TextStyle = .body,
         style: AnyShapeStyle? = nil, label: LocalizedStringResource? = nil) {
        self.icon = icon
        self.style = style
        self.label = label
        _side = ScaledMetric(wrappedValue: size, relativeTo: textStyle)
    }

    var body: some View {
        let image = Image(icon.assetName)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: side, height: side)
        labelled(styled(image))
    }

    @ViewBuilder
    private func styled<Content: View>(_ content: Content) -> some View {
        if let style {
            content.foregroundStyle(style)
        } else {
            content
        }
    }

    @ViewBuilder
    private func labelled<Content: View>(_ content: Content) -> some View {
        if let label {
            content.accessibilityLabel(Text(label))
        } else {
            content.accessibilityHidden(true)
        }
    }
}
`
}

export const iosEmitter: Emitter = {
  name: "ios",
  owned: { dirs: [CATALOG_DIR], files: [SWIFT_FILE], patterns: [] },
  emit(icons) {
    const out = new Map<string, string>()
    out.set(`${CATALOG_DIR}/Contents.json`, CATALOG_CONTENTS)
    for (const icon of icons) {
      const asset = assetName(icon.name)
      const dir = `${CATALOG_DIR}/${asset}.imageset`
      out.set(`${dir}/Contents.json`, imagesetContents(`${asset}.svg`))
      out.set(`${dir}/${asset}.svg`, imagesetSvg(icon))
    }
    out.set(SWIFT_FILE, swiftFile(icons))
    return out
  },
}
