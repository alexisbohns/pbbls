export type DocsLocale = "en" | "fr"

export type DocsCategory = "legal" | "support"

export type DocsIcon =
  | "sign"
  | "scale"
  | "key"
  | "sparkles"
  | "hand"
  | "trash"
  | "broom"
  | "umbrella"

export type DocsPageMeta = {
  slug: string
  category: DocsCategory
  order: number
  icon: DocsIcon
  titles: Record<DocsLocale, string>
  /** One line under the title in the navigation. */
  summaries: Record<DocsLocale, string>
  path: string
}

/** A `##` section of a page, for the table of contents. */
export type DocsTocItem = {
  id: string
  /** The heading without its "7." numbering; the contents number it again. */
  label: string
}

export type DocsManifest = {
  $schema: string
  locales: DocsLocale[]
  pages: DocsPageMeta[]
}

export type DocsPageFrontmatter = {
  title: string
  locale: DocsLocale
  slug: string
  version?: string
  effective_date?: string
  last_updated?: string
}

export type DocsPageContent = {
  frontmatter: DocsPageFrontmatter
  /** The page's `#` heading (the frontmatter title when there is none). */
  title: string
  toc: DocsTocItem[]
  /** The body, without the `#` heading. */
  html: string
}
