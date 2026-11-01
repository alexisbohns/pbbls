import { DetailTopBar } from "@/components/layout/DetailTopBar"
import type { Guide } from "@/lib/types"

/**
 * A guide as a detail (#1384, spec D14, two-area spec D5): the top bar with
 * its title, the italic summary, then the body rendered by `GuidePage`
 * (raw HTML already dropped).
 */
export function GuideArticle({ guide, html }: { guide: Guide; html: string | null }) {
  return (
    <>
      <DetailTopBar title={guide.title} />
      <article className="mx-auto flex w-full max-w-2xl flex-col px-5 pt-2 pb-12">
        <p className="pb-type-lead mb-6 text-m3-on-surface-variant">{guide.summary}</p>
        {html && <div className="prose prose-sm max-w-none text-m3-on-surface dark:prose-invert" dangerouslySetInnerHTML={{ __html: html }} />}
      </article>
    </>
  )
}
