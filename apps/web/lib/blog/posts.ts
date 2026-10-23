import type { BlogPost } from "@/lib/blog/types"
import { redrawingTheWeek } from "@/lib/blog/posts/redrawing-the-week"

/** Every published post, newest first. */
export const BLOG_POSTS: readonly BlogPost[] = [redrawingTheWeek]

export function getBlogPost(slug: string): BlogPost | undefined {
  return BLOG_POSTS.find((post) => post.slug === slug)
}

/** The public URL of a post's page. */
export function blogPostPath(slug: string): string {
  return `/blog/${slug}`
}

/** The public URL of one of a post's images, served from `public/blog/<slug>/`. */
export function blogImagePath(slug: string, file: string): string {
  return `/blog/${slug}/${file}`
}

/**
 * A `YYYY-MM-DD` publication day as the blog prints it: "23 October 2026".
 * Formatted in UTC so the day never shifts with the reader's timezone.
 */
export function formatPublishedOn(day: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${day}T00:00:00Z`))
}
