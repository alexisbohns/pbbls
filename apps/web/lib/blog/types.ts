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

/**
 * One concept of a design exploration: its number and name, its designer's
 * line, then the drawing as a strip of six frames (paper empty, middle and
 * complete, then night in the same states), set wider than the column.
 */
export type BlogPlate = {
  kind: "plate"
  /** The concept's number in the exploration ("21·2"). */
  no: string
  name: string
  /** A short note after the name: where the concept came from, its seed. */
  tag?: string
  text: string
  /**
   * SVG strip in the public `lab-assets` bucket, under `blog/<slug>/`, sized
   * {@link BLOG_PLATE_WIDTH} × {@link BLOG_PLATE_HEIGHT}. Kept out of the repo:
   * an exploration's plates weigh tens of MB.
   */
  file: string
  alt: string
  /** Print the six frames' labels above the strip, for the first plate of a section. */
  legend?: true
}

/** A plate strip's intrinsic size: six 240px frames, the night three set apart. */
export const BLOG_PLATE_WIDTH = 1516
export const BLOG_PLATE_HEIGHT = 240

export type BlogBlock =
  | { kind: "paragraph"; text: BlogRichText }
  | { kind: "heading"; text: string }
  | { kind: "subheading"; text: string }
  | BlogFigure
  | BlogPlate

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
