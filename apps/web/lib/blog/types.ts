/**
 * The in-house blog (#1209), a proof of concept: posts are typed content
 * modules under `lib/blog/posts/`, rendered by `components/blog/`. No CMS.
 */

/** A run of text inside a paragraph: plain, or set in italics. */
export type BlogInline = string | { em: string }

/** A paragraph's text. A plain string covers the common case. */
export type BlogRichText = string | readonly BlogInline[]

/** How wide a figure's image may grow: the full column, or capped narrower. */
export type BlogFigureSize = "full" | "mid" | "narrow"

export type BlogFigure = {
  kind: "figure"
  /** The figure number printed before the caption ("fig. 1"). */
  n: number
  /** File name under `public/blog/<slug>/`. */
  file: string
  alt: string
  /** The image's intrinsic size in pixels. */
  width: number
  height: number
  size: BlogFigureSize
  caption: string
}

export type BlogBlock =
  | { kind: "paragraph"; text: BlogRichText }
  | { kind: "heading"; text: string }
  | BlogFigure

export type BlogPost = {
  slug: string
  /** The document title, for the browser tab and link previews. */
  documentTitle: string
  /** The headline set on the page. */
  title: string
  kicker: string
  standfirst: string
  byline: string
  /** Publication day, `YYYY-MM-DD`. */
  publishedOn: string
  body: readonly BlogBlock[]
}
