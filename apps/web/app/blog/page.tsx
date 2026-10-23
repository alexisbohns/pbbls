import type { Metadata } from "next"
import { BLOG_POSTS } from "@/lib/blog/posts"
import { BlogIndex } from "@/components/blog/BlogIndex"

export const metadata: Metadata = {
  title: "Pebbles blog",
  description: "Behind the scenes of Pebbles, from the maker of Pebbles.",
  alternates: { canonical: "/blog" },
}

export default function BlogPage() {
  return <BlogIndex posts={BLOG_POSTS} />
}
