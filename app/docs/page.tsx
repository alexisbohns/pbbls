import { Suspense } from "react"
import { getDocsManifest } from "@/lib/docs/load-docs"
import { DocsIndex } from "@/components/docs/DocsIndex"

export default function DocsPage() {
  const manifest = getDocsManifest()
  return (
    <Suspense>
      <DocsIndex pages={manifest.pages} />
    </Suspense>
  )
}
