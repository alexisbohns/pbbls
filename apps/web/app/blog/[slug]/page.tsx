import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { BLOG_POSTS, blogImagePath, blogPostPath, getBlogPost } from "@/lib/blog/posts"
import { BlogArticle } from "@/components/blog/BlogArticle"
import type { BlogFigure } from "@/lib/blog/types"

type BlogPostPageProps = { params: Promise<{ slug: string }> }

// Every post is known at build time; any other slug is a 404.
export const dynamicParams = false

export function generateStaticParams() {
  return BLOG_POSTS.map((post) => ({ slug: post.slug }))
}

export async function generateMetadata({ params }: BlogPostPageProps): Promise<Metadata> {
  const { slug } = await params
  const post = getBlogPost(slug)
  if (!post) return {}
  // The link preview is the first figure kept in the repo: a raster, never a bucket drawing.
  const cover = post.body.find((block): block is BlogFigure => block.kind === "figure" && block.stored !== "bucket")
  return {
    title: post.documentTitle,
    description: post.standfirst,
    alternates: { canonical: blogPostPath(post.slug) },
    openGraph: {
      type: "article",
      title: post.documentTitle,
      description: post.standfirst,
      url: blogPostPath(post.slug),
      publishedTime: post.publishedOn,
      locale: "en",
      images: cover
        ? [{ url: blogImagePath(post.slug, cover.file), width: cover.width, height: cover.height, alt: cover.alt }]
        : undefined,
    },
    twitter: {
      card: "summary_large_image",
      title: post.documentTitle,
      description: post.standfirst,
      images: cover ? [blogImagePath(post.slug, cover.file)] : undefined,
    },
  }
}

export default async function BlogPostPage({ params }: BlogPostPageProps) {
  const { slug } = await params
  const post = getBlogPost(slug)
  if (!post) notFound()
  return <BlogArticle post={post} />
}
