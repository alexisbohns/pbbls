import type { ReactNode } from "react"
import { cn } from "@/lib/utils"
import type { BlogBlock, BlogPost, BlogRichText } from "@/lib/blog/types"
import { BlogFigure } from "@/components/blog/BlogFigure"
import { BlogPlate } from "@/components/blog/BlogPlate"
import styles from "./blog.module.css"

function renderRichText(text: BlogRichText): ReactNode {
  if (typeof text === "string") return text
  return text.map((run, i) =>
    typeof run === "string" ? run : <em key={i} className={styles.em}>{run.em}</em>,
  )
}

function renderBlock(slug: string, block: BlogBlock, index: number): ReactNode {
  switch (block.kind) {
    case "paragraph":
      return <p key={index} className={styles.p}>{renderRichText(block.text)}</p>
    case "heading":
      return <h2 key={index} className={styles.heading}>{block.text}</h2>
    case "subheading":
      return <h3 key={index} className={styles.subheading}>{block.text}</h3>
    case "figure":
      return <BlogFigure key={index} slug={slug} figure={block} />
    case "plate":
      return <BlogPlate key={index} slug={slug} plate={block} />
  }
}

/**
 * One blog post, set as the draft sets it. The app's shell already provides
 * the <main> landmark, so the draft's <main class="page"> is a <div> here.
 * `lang` is pinned because the app may switch <html lang> to French.
 */
export function BlogArticle({ post }: { post: BlogPost }) {
  return (
    <div className={styles.page} lang="en">
      <article className={styles.col}>
        <p className={styles.kicker}>{post.kicker}</p>
        <h1 className={styles.title}>{post.title}</h1>
        <p className={styles.standfirst}>{post.standfirst}</p>
        <p className={styles.byline}>{post.byline}</p>
        {post.body.map((block, i) => renderBlock(post.slug, block, i))}
        <p className={cn(styles.p, styles.end)} aria-hidden="true">· · ·</p>
      </article>
    </div>
  )
}
