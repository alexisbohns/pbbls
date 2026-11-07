import type { DocsCategory, DocsPageMeta } from "./types"

/** The navigation's sections, in manifest order, each sorted by `order`. */
export function groupDocsPages(pages: readonly DocsPageMeta[]): [DocsCategory, DocsPageMeta[]][] {
  const groups = new Map<DocsCategory, DocsPageMeta[]>()
  for (const page of pages) {
    groups.set(page.category, [...(groups.get(page.category) ?? []), page])
  }
  return [...groups].map(([category, list]) => [category, [...list].sort((a, b) => a.order - b.order)])
}
