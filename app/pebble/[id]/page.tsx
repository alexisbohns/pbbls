"use client"

import { use } from "react"
import Link from "next/link"
import { usePebble } from "@/lib/data/usePebble"
import { useSouls } from "@/lib/data/useSouls"
import { PebbleDetail } from "@/components/pebble/PebbleDetail"
import { PebbleNotFound } from "@/components/pebble/PebbleNotFound"

export default function PebbleDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const { pebble, loading: pebbleLoading } = usePebble(id)
  const { souls, loading: soulsLoading } = useSouls()

  const loading = pebbleLoading || soulsLoading

  return (
    <section>
      <nav className="mb-6">
        <Link
          href="/path"
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          &larr; Back to Path
        </Link>
      </nav>

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : pebble ? (
        <PebbleDetail pebble={pebble} souls={souls} />
      ) : (
        <PebbleNotFound />
      )}
    </section>
  )
}
