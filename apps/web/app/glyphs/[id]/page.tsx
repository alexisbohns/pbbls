"use client"

import { use } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { useTranslations } from "next-intl"
import { useMark } from "@/lib/data/useMark"
import { useMarks } from "@/lib/data/useMarks"
import { useGlyphSubmissions } from "@/lib/data/useGlyphSubmissions"
import { GlyphDetail } from "@/components/glyphs/GlyphDetail"
import { GlyphNotFound } from "@/components/glyphs/GlyphNotFound"
import { SubmitToCommunity } from "@/components/glyphs/SubmitToCommunity"
import { PageLayout } from "@/components/layout/PageLayout"

export default function GlyphDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const t = useTranslations("glyphs")
  const tDetail = useTranslations("glyphs.detail")
  const { mark, loading } = useMark(id)
  const { removeMark, updateMark } = useMarks()
  const { submissions, submit } = useGlyphSubmissions()
  const router = useRouter()

  const submission = submissions.find((s) => s.glyph_id === id)
  const locked =
    submission?.status === "pending" || submission?.status === "approved"

  const handleDelete = async () => {
    await removeMark(id)
    router.push("/glyphs")
  }

  const handleUpdateName = async (name: string | null) => {
    await updateMark(id, { name })
  }

  return (
    <PageLayout>
      <section>
      <nav className="mb-6">
        <Link
          href="/glyphs"
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          {tDetail("back")}
        </Link>
      </nav>

      {loading ? (
        <p className="text-sm text-muted-foreground">{t("loading")}</p>
      ) : mark ? (
        <GlyphDetail
          mark={mark}
          onDelete={handleDelete}
          onUpdateName={handleUpdateName}
          locked={locked}
          submitSlot={
            <SubmitToCommunity
              status={submission?.status}
              onSubmit={() => submit(id).then(() => {})}
            />
          }
        />
      ) : (
        <GlyphNotFound />
      )}
      </section>
    </PageLayout>
  )
}
