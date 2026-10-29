/**
 * A tokenizer for exactly the source grammar (spec D7): one `<svg>` root whose
 * only children are `<path …/>` elements. Attributes are returned as they are,
 * so the lint can judge them; structure is enforced here, so anything else
 * (a `<circle>`, a `<g>`, a comment, a second root) throws with the file name.
 */

export type ParsedPath = {
  d: string
  /** `fill="currentColor"`: a lit shape. */
  filled: boolean
  /** `fill-rule="evenodd"`: a carved stone (spec D11). */
  evenOdd: boolean
  /** `stroke="none"`: a solid or carved stone keeps its drawn edge. */
  unstroked: boolean
  attrs: Record<string, string>
}

export type ParsedSvg = {
  root: Record<string, string>
  paths: ParsedPath[]
}

const ATTR = /^\s*([A-Za-z_:][\w:.-]*)="([^"]*)"/

function readAttrs(text: string, file: string, where: string): Record<string, string> {
  const attrs: Record<string, string> = {}
  let rest = text
  while (rest.trim().length > 0) {
    const m = ATTR.exec(rest)
    if (!m || m[1] === undefined || m[2] === undefined) {
      throw new Error(`${file}: ${where}: cannot read attributes at ${JSON.stringify(rest.trim().slice(0, 30))}`)
    }
    if (m[1] in attrs) throw new Error(`${file}: ${where}: duplicate attribute ${m[1]}`)
    attrs[m[1]] = m[2]
    rest = rest.slice(m[0].length)
  }
  return attrs
}

export function parseSvg(text: string, file: string): ParsedSvg {
  let pos = 0
  const skipSpace = () => {
    while (pos < text.length && /\s/.test(text.charAt(pos))) pos += 1
  }
  skipSpace()
  if (!text.startsWith("<svg", pos)) throw new Error(`${file}: expected <svg> at the start, found ${JSON.stringify(text.slice(pos, pos + 20))}`)
  const rootEnd = text.indexOf(">", pos)
  if (rootEnd < 0) throw new Error(`${file}: unterminated <svg> tag`)
  const root = readAttrs(text.slice(pos + 4, rootEnd), file, "<svg>")
  pos = rootEnd + 1
  const paths: ParsedPath[] = []
  for (;;) {
    skipSpace()
    if (pos >= text.length) throw new Error(`${file}: missing </svg>`)
    if (text.startsWith("</svg>", pos)) {
      pos += 6
      break
    }
    if (text.startsWith("<!--", pos)) throw new Error(`${file}: comments are not allowed`)
    if (text.startsWith("<path", pos)) {
      const end = text.indexOf("/>", pos)
      const close = text.indexOf(">", pos)
      if (end < 0 || close < end) throw new Error(`${file}: <path> must be self-closing`)
      const attrs = readAttrs(text.slice(pos + 5, end), file, `path ${paths.length + 1}`)
      const d = attrs.d
      if (d === undefined) throw new Error(`${file}: path ${paths.length + 1} has no d`)
      paths.push({
        d,
        filled: attrs.fill === "currentColor",
        evenOdd: attrs["fill-rule"] === "evenodd",
        unstroked: attrs.stroke === "none",
        attrs,
      })
      pos = end + 2
      continue
    }
    if (text.charAt(pos) === "<") {
      const tag = /^<\/?([A-Za-z][\w:-]*)/.exec(text.slice(pos))
      throw new Error(`${file}: only <path> children are allowed, found <${tag?.[1] ?? "?"}>`)
    }
    throw new Error(`${file}: stray text ${JSON.stringify(text.slice(pos, pos + 20))}`)
  }
  skipSpace()
  if (pos < text.length) throw new Error(`${file}: content after </svg>: ${JSON.stringify(text.slice(pos, pos + 20))}`)
  return { root, paths }
}
