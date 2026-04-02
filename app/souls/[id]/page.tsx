"use client"

import { use, useMemo } from "react"
import Link from "next/link"
import { useSoul } from "@/lib/data/useSoul"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { SoulDetailHeader } from "@/components/souls/SoulDetailHeader"
import { SoulPebbleList } from "@/components/souls/SoulPebbleList"
import { SoulNotFound } from "@/components/souls/SoulNotFound"

export default function SoulDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const { soul, loading: soulLoading, updateSoul } = useSoul(id)
  const { pebbles, loading: pebblesLoading } = usePebbles()
  const { souls, loading: soulsLoading } = useSouls()

  const loading = soulLoading || pebblesLoading || soulsLoading

  const relatedPebbles = useMemo(
    () => pebbles.filter((p) => p.soul_ids.includes(id)),
    [pebbles, id],
  )

  const handleUpdateName = async (name: string) => {
    await updateSoul({ name })
  }

  return (
    <section>
      <nav className="mb-6">
        <Link
          href="/souls"
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          &larr; Back to Souls
        </Link>
      </nav>

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading&hellip;</p>
      ) : soul ? (
        <>
          <SoulDetailHeader
            soul={soul}
            pebbleCount={relatedPebbles.length}
            onUpdateName={handleUpdateName}
          />
          {relatedPebbles.length === 0 ? (
            <p className="py-10 text-center text-sm text-muted-foreground">
              No pebbles linked to this soul yet.
            </p>
          ) : (
            <SoulPebbleList pebbles={relatedPebbles} souls={souls} />
          )}
        </>
      ) : (
        <SoulNotFound />
      )}
    </section>
  )
}
