import Link from "next/link"
import { blogPostPath, formatPublishedOn } from "@/lib/blog/posts"
import type { BlogPost } from "@/lib/blog/types"
import styles from "./blog.module.css"

/** The blog's front page: every post, newest first, set in the article's type. */
export function BlogIndex({ posts }: { posts: readonly BlogPost[] }) {
  return (
    <div className={styles.page} lang="en">
      <div className={styles.col}>
        <p className={styles.kicker}>Pebbles · behind the scenes</p>
        <h1 className={styles.title}>Blog</h1>
        <ul className={styles.entries}>
          {posts.map((post) => (
            <li key={post.slug} className={styles.entry}>
              <article>
                <p className={styles.byline}>
                  <time dateTime={post.publishedOn}>{formatPublishedOn(post.publishedOn)}</time>
                </p>
                <h2 className={styles.entryTitle}>
                  <Link href={blogPostPath(post.slug)}>{post.title}</Link>
                </h2>
                <p className={styles.standfirst}>{post.standfirst}</p>
              </article>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
