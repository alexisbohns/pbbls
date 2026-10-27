import { existsSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { BLOG_POSTS, blogImagePath, blogPostPath, formatPublishedOn, getBlogPost } from "@/lib/blog/posts"
import type { BlogFigure, BlogPlate } from "@/lib/blog/types"

const PUBLIC_DIR = fileURLToPath(new URL("../../public", import.meta.url))

describe("blog posts", () => {
  it("gives every post a unique slug", () => {
    const slugs = BLOG_POSTS.map((post) => post.slug)
    expect(new Set(slugs).size).toBe(slugs.length)
  })

  it("serves redrawing-the-week at /blog/redrawing-the-week, dated 23 October 2026", () => {
    const post = getBlogPost("redrawing-the-week")
    expect(post).toBeDefined()
    expect(blogPostPath("redrawing-the-week")).toBe("/blog/redrawing-the-week")
    expect(post && formatPublishedOn(post.publishedOn)).toBe("23 October 2026")
  })

  it("returns nothing for an unknown slug", () => {
    expect(getBlogPost("nope")).toBeUndefined()
  })

  it("numbers figures in order and ships every image they point at", () => {
    for (const post of BLOG_POSTS) {
      const figures = post.body.filter((block): block is BlogFigure => block.kind === "figure")
      expect(figures.map((figure) => figure.n)).toEqual(figures.map((_, i) => i + 1))
      for (const figure of figures) {
        expect(figure.alt.length, figure.file).toBeGreaterThan(0)
        expect(figure.width * figure.height, figure.file).toBeGreaterThan(0)
        expect(existsSync(PUBLIC_DIR + blogImagePath(post.slug, figure.file)), figure.file).toBe(true)
      }
    }
  })

  it("serves counting-in-stone, its finalists first and every plate an SVG strip with alt text", () => {
    const post = getBlogPost("counting-in-stone")
    expect(post?.publishedOn).toBe("2026-10-27")
    const plates = post?.body.filter((block): block is BlogPlate => block.kind === "plate") ?? []
    expect(plates.length).toBeGreaterThan(400)
    expect(plates[0]?.name).toBe("One skyline")
    for (const plate of plates) {
      expect(plate.file, plate.no).toMatch(/^c\/[\w-]+\.svg$/)
      expect(plate.alt.length, plate.no).toBeGreaterThan(0)
    }
  })
})

describe("formatPublishedOn", () => {
  it("prints the day as written, whatever the reader's timezone", () => {
    // The suite runs in Europe/Paris; midnight UTC must not slip a day.
    expect(formatPublishedOn("2026-01-01")).toBe("1 January 2026")
    expect(formatPublishedOn("2026-12-31")).toBe("31 December 2026")
  })
})
