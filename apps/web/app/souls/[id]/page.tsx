"use client"

import { use, useMemo } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { Trash2 } from "lucide-react"
import { useTranslations } from "next-intl"
import { useSoul } from "@/lib/data/useSoul"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { useMarks } from "@/lib/data/useMarks"
import { SoulDetailHeader } from "@/components/souls/SoulDetailHeader"
import { SoulPebbleList } from "@/components/souls/SoulPebbleList"
import { SoulNotFound } from "@/components/souls/SoulNotFound"
import { PageLayout } from "@/components/layout/PageLayout"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"

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
  const { pebbles, loading: pebblesLoading } = usePebbles()
  const { souls, loading: soulsLoading, removeSoul } = useSouls()
  const { marks } = useMarks()

  const loading = soulLoading || pebblesLoading || soulsLoading

  const relatedPebbles = useMemo(
    () => pebbles.filter((p) => p.soul_ids.includes(id)),
    [pebbles, id],
  )

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
    <PageLayout>
      <section>
      <nav className="mb-6">
        <Link
          href="/souls"
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          {t("back")}
        </Link>
      </nav>

      {loading ? (
        <p className="text-sm text-muted-foreground">{tSouls("loading")}</p>
      ) : soul ? (
        <>
          <SoulDetailHeader
            soul={soul}
            pebbleCount={relatedPebbles.length}
            marks={marks}
            onUpdateName={handleUpdateName}
            onUpdateGlyph={handleUpdateGlyph}
          />
          {relatedPebbles.length === 0 ? (
            <p className="py-10 text-center text-sm text-muted-foreground">
              {t("noPebbles")}
            </p>
          ) : (
            <SoulPebbleList pebbles={relatedPebbles} souls={souls} />
          )}

          <div className="mt-8 flex justify-center">
            <ConfirmDialog
              trigger={
                <Button variant="outline" size="sm">
                  <Trash2 className="size-4" aria-hidden="true" />
                  {tSouls("deleteCta")}
                </Button>
              }
              title={tSouls("deleteTitle")}
              description={tSouls("deleteDescription", { name: soul.name })}
              confirmLabel={tSouls("deleteConfirm")}
              onConfirm={handleDelete}
            />
          </div>
        </>
      ) : (
        <SoulNotFound />
      )}
      </section>
    </PageLayout>
  )
}
