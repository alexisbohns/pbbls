import Image from "next/image"
import { BLOG_PLATE_HEIGHT, BLOG_PLATE_WIDTH, type BlogPlate as BlogPlateBlock } from "@/lib/blog/types"
import { labAssetUrl } from "@/lib/utils/lab-asset-url"
import styles from "./blog.module.css"

const FRAMES = ["Paper · empty", "middle", "complete", "Night · empty", "middle", "complete"]

type BlogPlateProps = {
  slug: string
  plate: BlogPlateBlock
}

/**
 * One concept: its name and line in the column, then its six frames wider
 * than the column. On a phone the strip keeps its size and scrolls sideways.
 */
export function BlogPlate({ slug, plate }: BlogPlateProps) {
  const src = labAssetUrl(`blog/${slug}/${plate.file}`)
  return (
    <section className={styles.plate} aria-label={`${plate.no} ${plate.name}`}>
      <p className={styles.plateHead}>
        <span className={styles.n}>{plate.no}</span>
        <span className={styles.plateName}>{plate.name}</span>
        {plate.tag ? <span className={styles.plateTag}>{plate.tag}</span> : null}
      </p>
      <p className={styles.plateText}>{plate.text}</p>
      {src ? (
        <div className={styles.strip}>
          <div className={styles.stripInner}>
            {plate.legend ? (
              <p className={styles.legend} aria-hidden="true">
                {FRAMES.map((label, i) => <span key={i}>{label}</span>)}
              </p>
            ) : null}
            {/* Served as drawn from the bucket: there is nothing for the optimiser to do. */}
            <Image
              className={styles.stripImg}
              src={src}
              alt={plate.alt}
              width={BLOG_PLATE_WIDTH}
              height={BLOG_PLATE_HEIGHT}
              loading="lazy"
              unoptimized
            />
          </div>
        </div>
      ) : null}
    </section>
  )
}
