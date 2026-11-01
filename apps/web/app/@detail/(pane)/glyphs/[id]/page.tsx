"use client"

import { use } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { useMark } from "@/lib/data/useMark"
import { useMarks } from "@/lib/data/useMarks"
import { useGlyphSubmissions } from "@/lib/data/useGlyphSubmissions"
import { GlyphDetail } from "@/components/glyphs/GlyphDetail"
import { GlyphNotFound } from "@/components/glyphs/GlyphNotFound"
import { SubmitToCommunity } from "@/components/glyphs/SubmitToCommunity"
import { DetailTopBar } from "@/components/layout/DetailTopBar"

/** A glyph, nested under Glyphs in the detail area (#1384, two-area spec D2). */
export default function GlyphDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const t = useTranslations("glyphs")
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
    <>
      <DetailTopBar title={mark ? mark.name || t("untitled") : ""} backHref="/glyphs" />
      <section className="mx-auto w-full max-w-2xl px-4 pt-2 pb-12">
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
              reviewNote={submission?.review_note}
              onSubmit={() => submit(id).then(() => {})}
            />
          }
        />
      ) : (
        <GlyphNotFound />
      )}
      </section>
    </>
  )
}
