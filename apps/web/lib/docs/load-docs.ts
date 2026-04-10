import fs from "node:fs"
import path from "node:path"
import matter from "gray-matter"
import { unified } from "unified"
import remarkParse from "remark-parse"
import remarkRehype from "remark-rehype"
import rehypeStringify from "rehype-stringify"
import type { DocsLocale, DocsManifest, DocsPageContent } from "./types"

const docsDir = path.join(process.cwd(), "docs")

const processor = unified()
  .use(remarkParse)
  .use(remarkRehype, { allowDangerousHtml: true })
  .use(rehypeStringify, { allowDangerousHtml: true })

export function getDocsManifest(): DocsManifest {
  const raw = fs.readFileSync(path.join(docsDir, "index.json"), "utf-8")
  return JSON.parse(raw) as DocsManifest
}

export function getDocsSlugs(): string[] {
  return getDocsManifest().pages.map((p) => p.slug)
}

function stringifyDates(obj: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(obj)) {
    out[key] = value instanceof Date ? value.toISOString().split("T")[0] : value
  }
  return out
}

export function getDocPage(slug: string, locale: DocsLocale): DocsPageContent {
  const filePath = path.join(docsDir, slug, `${locale}.md`)
  const raw = fs.readFileSync(filePath, "utf-8")
  const { data, content } = matter(raw)
  const result = processor.processSync(content)

  return {
    frontmatter: stringifyDates(data) as DocsPageContent["frontmatter"],
    html: String(result),
  }
}

export function getAllLocaleContent(slug: string): Record<DocsLocale, DocsPageContent> {
  const manifest = getDocsManifest()
  const entries = manifest.locales.map((locale) => [locale, getDocPage(slug, locale)] as const)
  return Object.fromEntries(entries) as Record<DocsLocale, DocsPageContent>
}
