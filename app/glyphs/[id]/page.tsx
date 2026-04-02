"use client"

import { use } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { useMark } from "@/lib/data/useMark"
import { useMarks } from "@/lib/data/useMarks"
import { GlyphDetail } from "@/components/glyphs/GlyphDetail"
import { GlyphNotFound } from "@/components/glyphs/GlyphNotFound"

export default function GlyphDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const { mark, loading } = useMark(id)
  const { removeMark } = useMarks()
  const router = useRouter()

  const handleDelete = async () => {
    await removeMark(id)
    router.push("/glyphs")
  }

  return (
    <section>
      <nav className="mb-6">
        <Link
          href="/glyphs"
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          &larr; Back to Glyphs
        </Link>
      </nav>

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : mark ? (
        <GlyphDetail mark={mark} onDelete={handleDelete} />
      ) : (
        <GlyphNotFound />
      )}
    </section>
  )
}
