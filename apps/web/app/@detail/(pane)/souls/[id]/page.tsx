"use client"

import { use, useMemo } from "react"
import { useRouter } from "next/navigation"
import { Trash } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { useTranslations } from "next-intl"
import { useSoul } from "@/lib/data/useSoul"
import { useMoments } from "@/lib/data/useMoments"
import { momentsWithSoul } from "@/lib/data/moment-lists"
import { useSouls } from "@/lib/data/useSouls"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { SoulDetailHeader } from "@/components/souls/SoulDetailHeader"
import { SoulPebbleList } from "@/components/souls/SoulPebbleList"
import { SoulNotFound } from "@/components/souls/SoulNotFound"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"

/** A soul, in the detail area beside People (#1384, two-area spec D2, D6). */
export default function SoulDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const t = useTranslations("souls.detail")
  const tSouls = useTranslations("souls")
  const router = useRouter()
  const { soul, loading: soulLoading, updateSoul } = useSoul(id)
  const { moments, loading: momentsLoading } = useMoments()
  const { loading: soulsLoading, removeSoul } = useSouls()
  const { glyphs: marks } = useUsableGlyphs()

  const loading = soulLoading || momentsLoading || soulsLoading

  const relatedMoments = useMemo(() => momentsWithSoul(moments, id), [moments, id])

  const handleUpdateName = async (name: string) => {
    await updateSoul({ name })
  }

  const handleUpdateGlyph = async (glyph_id: string) => {
    await updateSoul({ glyph_id })
  }

  const handleDelete = async () => {
    await removeSoul(id)
    router.push("/souls")
  }

  return (
    <>
      <DetailTopBar
        title={soul?.name ?? ""}
        actions={
          soul && (
            <ConfirmDialog
              trigger={
                <Button variant="ghost" size="icon" className="size-11 rounded-full text-m3-on-surface-variant" aria-label={tSouls("deleteCta")}>
                  <Trash className={PBBLS_ICON_SIZE.toolbar} />
                </Button>
              }
              title={tSouls("deleteTitle")}
              description={tSouls("deleteDescription", { name: soul.name })}
              confirmLabel={tSouls("deleteConfirm")}
              onConfirm={handleDelete}
            />
          )
        }
      />
      <div className="mx-auto w-full max-w-2xl px-4 pt-2 pb-12">
        {loading ? (
          <p className="m3-type-body-medium text-m3-on-surface-variant">{tSouls("loading")}</p>
        ) : soul ? (
          <>
            <SoulDetailHeader
              soul={soul}
              pebbleCount={relatedMoments.length}
              marks={marks}
              onUpdateName={handleUpdateName}
              onUpdateGlyph={handleUpdateGlyph}
            />
            {relatedMoments.length === 0 ? (
              <p className="m3-type-body-medium py-10 text-center text-m3-on-surface-variant">{t("noPebbles")}</p>
            ) : (
              <SoulPebbleList moments={relatedMoments} />
            )}
          </>
        ) : (
          <SoulNotFound />
        )}
      </div>
    </>
  )
}
