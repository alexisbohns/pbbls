"use client"

import Link from "next/link"
import { useMarks } from "@/lib/data/useMarks"
import { GlyphList } from "@/components/glyphs/GlyphList"
import { GlyphsEmptyState } from "@/components/glyphs/GlyphsEmptyState"
import { PageLayout } from "@/components/layout/PageLayout"
import { PathProfileCard } from "@/components/path/PathProfileCard"
import { BackPath } from "@/components/ui/BackPath"

export default function GlyphsPage() {
  const { marks, loading } = useMarks()

  return (
    <PageLayout sidebar={<><BackPath /><PathProfileCard /></>}>
      <section>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Glyphs</h1>
        {marks.length > 0 && (
          <Link
            href="/carve"
            className="text-sm font-medium text-primary underline underline-offset-4 hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          >
            Carve new glyph
          </Link>
        )}
      </div>

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : marks.length === 0 ? (
        <GlyphsEmptyState />
      ) : (
        <GlyphList marks={marks} />
      )}
      </section>
    </PageLayout>
  )
}
