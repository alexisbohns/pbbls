import { Suspense } from "react"
import { notFound } from "next/navigation"
import { getDocsManifest, getAllLocaleContent } from "@/lib/docs/load-docs"
import { DocsEmbed } from "@/components/docs/DocsEmbed"

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
    <Suspense>
      <DocsEmbed content={content} pages={manifest.pages} />
    </Suspense>
  )
}
