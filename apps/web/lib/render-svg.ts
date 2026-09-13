/**
 * render_svg — delivery guard
 *
 * `pebbles.render_svg` is server-composed, but it is composed from a jsonb
 * column its owner writes directly, and it is delivered straight into
 * `dangerouslySetInnerHTML` on four surfaces — two of them reachable by
 * someone other than the author: the anonymous `/p/[id]` share page and a
 * connection's tile inside the viewer's authenticated session (#829).
 *
 * The compositor is the fix; this is the second lock. It matters for two
 * reasons the compositor cannot cover:
 *
 *  1. Rows written BEFORE the compositor was fixed are still in the database.
 *     Nothing re-renders them until the backfill runs, and nothing stops a
 *     restore or a replayed migration from putting one back.
 *  2. `render_svg` has other writers over time (backfill, ops scripts, a
 *     future client engine). A guard at the sink holds for all of them.
 *
 * The strategy is reject-unknown, not clean-up. `render_svg` is machine-made
 * in one narrow shape, so anything the grammar below does not recognize is by
 * definition not our artwork, and the honest answer is to render none of it
 * and let the caller fall back to the plain outline. Subtracting the parts a
 * denylist happens to know about is how sanitizers get bypassed; refusing
 * everything that is not on a short allowlist does not have that failure mode.
 */

/** Elements the engine and the shape library emit. Nothing here can execute,
 *  load a subresource, or host foreign markup — which is what keeps `script`,
 *  `style`, `foreignObject`, `use`, `image`, `a` and the `animate*` family off
 *  the list, deliberately and permanently. */
const ALLOWED_ELEMENTS = new Set([
  "svg", "g", "defs", "clippath", "path",
  "rect", "circle", "ellipse", "line", "polygon", "polyline",
])

/** Attributes the engine and the shape library emit, plus the geometry
 *  attributes of the shape elements above. No `on*` handler can appear here,
 *  and no attribute that takes a URL except `clip-path`, whose value is
 *  pinned to a local fragment below. */
const ALLOWED_ATTRIBUTES = new Set([
  "xmlns", "viewbox", "width", "height", "id", "class", "transform",
  "preserveaspectratio", "aria-hidden", "focusable", "shape-rendering",
  "d", "points",
  "fill", "fill-rule", "fill-opacity",
  "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin",
  "stroke-dasharray", "stroke-dashoffset", "stroke-opacity", "stroke-miterlimit",
  "opacity", "clip-path", "clip-rule", "vector-effect",
  "x", "y", "rx", "ry", "cx", "cy", "r", "x1", "y1", "x2", "y2",
])

/** One tag, with its attribute list. Quoted values may hold `>`, so the
 *  alternation consumes whole quoted runs before it will accept a bare `>`.
 *  The attribute run also swallows the `/` of a self-closing tag, so the
 *  scanner reads that off the end of the run rather than from a separate
 *  group. */
const TAG = /<(\/?)([a-zA-Z][a-zA-Z0-9-]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>/g

/** One attribute inside a tag. Unquoted values are matched too, so they can be
 *  rejected rather than skipped. */
const ATTRIBUTE = /([a-zA-Z_:][a-zA-Z0-9_:.-]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g

const SVG_NAMESPACE = "http://www.w3.org/2000/svg"

/** `clip-path="url(#shape-clip)"` and nothing else shaped like a URL. */
const LOCAL_URL = /^url\(#[A-Za-z0-9:_.-]+\)$/

/**
 * Return `svg` if it is recognisable engine output, or `null` if it is not.
 *
 * Callers render the `null` case as their existing no-render fallback (the
 * emotion-tinted outline), so a rejected document degrades the visual and
 * never the page.
 */
export function safeRenderSvg(svg: string | null | undefined): string | null {
  if (typeof svg !== "string") return null

  const trimmed = svg.trim()
  if (trimmed === "") return null

  // Declarations, processing instructions, comments and CDATA have no place in
  // engine output, and each is a known parser-confusion vector. One check
  // before the scan, because `<!--` would otherwise hide a tag from the regex.
  if (trimmed.includes("<!") || trimmed.includes("<?")) return null

  const open: string[] = []
  let cursor = 0

  TAG.lastIndex = 0
  for (let tag = TAG.exec(trimmed); tag !== null; tag = TAG.exec(trimmed)) {
    // Text between tags. The engine emits only indentation; anything else —
    // including a stray `<` the tag pattern could not parse — rejects.
    if (trimmed.slice(cursor, tag.index).trim() !== "") return null
    cursor = tag.index + tag[0].length

    const [, closing, rawName, rawAttrs] = tag
    const name = rawName.toLowerCase()
    if (!ALLOWED_ELEMENTS.has(name)) return null

    const selfClosing = rawAttrs.trimEnd().endsWith("/")
    const attrs = selfClosing ? rawAttrs.trimEnd().slice(0, -1) : rawAttrs

    if (closing === "/") {
      if (attrs.trim() !== "" || open.pop() !== name) return null
      continue
    }

    if (!validateAttributes(attrs)) return null
    if (!selfClosing) open.push(name)
  }

  if (trimmed.slice(cursor).trim() !== "") return null
  // An unbalanced document means the scan and the browser's parser disagree
  // about the shape of the tree, and the browser's reading is the one that
  // would run. Refuse rather than guess.
  if (open.length > 0) return null
  // The outermost element has to be the `<svg>` the caller thinks it is
  // injecting; a bare `<g>` would inherit whatever context it lands in.
  if (!/^<svg[\s>]/i.test(trimmed)) return null

  return svg
}

function validateAttributes(attrs: string): boolean {
  let cursor = 0

  ATTRIBUTE.lastIndex = 0
  for (let attr = ATTRIBUTE.exec(attrs); attr !== null; attr = ATTRIBUTE.exec(attrs)) {
    if (attrs.slice(cursor, attr.index).trim() !== "") return false
    cursor = attr.index + attr[0].length

    const name = attr[1].toLowerCase()
    if (!ALLOWED_ATTRIBUTES.has(name)) return false

    // Unquoted (group 4) and valueless attributes are rejected outright: the
    // engine always quotes, so their presence already means this is not our
    // document, and unquoted values are where attribute injection hides.
    const value = attr[2] ?? attr[3]
    if (value === undefined) return false

    if (name === "xmlns" && value !== SVG_NAMESPACE) return false
    if (name === "clip-path" && value !== "none" && !LOCAL_URL.test(value)) return false
    // No allowed attribute takes a URL or a nested expression. Blocking the
    // shapes outright is cheaper than reasoning about each one.
    if (/[<>]/.test(value)) return false
    if (/(?:javascript|data|vbscript)\s*:/i.test(value)) return false
    if (name !== "clip-path" && /url\s*\(/i.test(value)) return false
  }

  return attrs.slice(cursor).trim() === ""
}
