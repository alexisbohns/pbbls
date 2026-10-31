import Image from "next/image"
import { cn } from "@/lib/utils"
import { blogImagePath } from "@/lib/blog/posts"
import type { BlogFigure as BlogFigureBlock, BlogFigureSize } from "@/lib/blog/types"
import { labAssetUrl } from "@/lib/utils/lab-asset-url"
import styles from "./blog.module.css"

/** The widest the image renders at, per size: the column, or the draft's caps. */
const MAX_WIDTH: Record<BlogFigureSize, number> = { full: 680, mid: 560, narrow: 440 }

/** The page's side gutters (16px each) at widths where the image fills the column. */
const GUTTERS = 32

const SIZE_CLASS: Record<BlogFigureSize, string | undefined> = {
  full: undefined,
  mid: styles.mid,
  narrow: styles.narrow,
}

type BlogFigureProps = {
  slug: string
  figure: BlogFigureBlock
}

export function BlogFigure({ slug, figure }: BlogFigureProps) {
  const max = MAX_WIDTH[figure.size]
  const bucket = figure.stored === "bucket"
  const src = bucket ? labAssetUrl(`blog/${slug}/${figure.file}`) : blogImagePath(slug, figure.file)
  if (!src) return null
  return (
    <figure className={cn(styles.figure, SIZE_CLASS[figure.size])}>
      <Image
        className={styles.img}
        src={src}
        alt={figure.alt}
        width={figure.width}
        height={figure.height}
        sizes={`(max-width: ${max + GUTTERS}px) calc(100vw - ${GUTTERS}px), ${max}px`}
        loading="lazy"
        // A drawing from the bucket is served as drawn: there is nothing for the optimiser to do.
        unoptimized={bucket}
        // Pin the box to the original's ratio: an optimised rendition's height
        // is rounded (1077×1400 served at 384 wide is 384×499), and the browser
        // would otherwise size the box from it, nudging everything below.
        style={{ aspectRatio: `${figure.width} / ${figure.height}` }}
      />
      <figcaption className={styles.caption}>
        <span className={styles.n}>fig. {figure.n}</span>
        {figure.caption}
      </figcaption>
    </figure>
  )
}
