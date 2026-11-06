import { Suspense } from "react"
import { notFound } from "next/navigation"
import { getDocsManifest, getAllLocaleContent } from "@/lib/docs/load-docs"
import { DocsEmbed } from "@/components/docs/DocsEmbed"
import { DocsContent } from "@/components/docs/DocsContent"

export function generateStaticParams() {
  const manifest = getDocsManifest()
  return manifest.pages.map((page) => ({ slug: page.slug }))
}

export default async function DocsSlugPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const manifest = getDocsManifest()
  const page = manifest.pages.find((p) => p.slug === slug)

  if (!page) notFound()

  const content = getAllLocaleContent(slug)

  return (
    // DocsEmbed reads the search params, so it only renders in the browser. The
    // fallback keeps the text in the served HTML, for store reviewers and crawlers.
    <Suspense
      fallback={
        <div className="mx-auto max-w-prose px-4 py-6">
          <DocsContent content={content} />
        </div>
      }
    >
      <DocsEmbed content={content} pages={manifest.pages} />
    </Suspense>
  )
}
