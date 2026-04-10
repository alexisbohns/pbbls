export type DocsLocale = "en" | "fr"

export type DocsPageMeta = {
  slug: string
  category: string
  order: number
  titles: Record<DocsLocale, string>
  path: string
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
  html: string
}
