import type { DocsTocItem } from "./types"

/** What `rehypeOutline` lifts out of a page. */
export type Outline = {
  title: string | null
  toc: DocsTocItem[]
}

// The few hast shapes the plugin reads, kept structural so the docs loader
// does not depend on hast's types directly.
type HastNode = {
  type: string
  tagName?: string
  value?: string
  properties?: Record<string, unknown>
  children?: HastNode[]
}

function textOf(node: HastNode): string {
  if (node.type === "text") return node.value ?? ""
  return (node.children ?? []).map(textOf).join("")
}

/** "7. Data Retention Periods" → "Data Retention Periods". */
export function tocLabel(heading: string): string {
  return heading.replace(/^\s*\d+(\.\d+)*\.?\s+/, "").trim()
}

/** A stable, readable anchor: accents dropped, words joined by dashes. */
export function slugify(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
}

/**
 * A rehype step that takes the first `<h1>` out of the body (the page header
 * shows it) and gives each `<h2>` an id, collecting them for the table of
 * contents. Fills `outline` in place.
 */
export function rehypeOutline(outline: Outline) {
  return (tree: HastNode) => {
    const used = new Set<string>()
    const children = tree.children ?? []

    tree.children = children.filter((node) => {
      if (node.type !== "element") return true

      if (node.tagName === "h1" && outline.title === null) {
        outline.title = textOf(node).trim()
        return false
      }

      if (node.tagName === "h2") {
        const label = tocLabel(textOf(node))
        const base = slugify(label) || "section"
        let id = base
        for (let n = 2; used.has(id); n++) id = `${base}-${n}`
        used.add(id)
        node.properties = { ...node.properties, id }
        outline.toc.push({ id, label })
      }
      return true
    })
  }
}
