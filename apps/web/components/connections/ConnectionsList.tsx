"use client"

import { Loader2 } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Connection } from "@/lib/types"
import { ConnectionRow } from "@/components/connections/ConnectionRow"

type ConnectionsListProps = {
  connections: Connection[]
  loading: boolean
  error: Error | null
  onRemove: (id: string, block?: boolean) => Promise<void>
}

/**
 * The connections list (M49). Presentational: the page owns the single
 * `useConnections` instance (shared with `InviteSection`, so the list is
 * fetched exactly once) and hands state down.
 */
export function ConnectionsList({ connections, loading, error, onRemove }: ConnectionsListProps) {
  const t = useTranslations("connections")

  if (loading) {
    return (
      <div className="flex justify-center py-12" aria-live="polite">
        <Loader2 className="size-5 animate-spin text-muted-foreground" aria-label={t("loading")} />
      </div>
    )
  }

  if (error) {
    return (
      <p role="alert" className="py-12 text-center text-sm text-destructive">
        {t("loadError")}
      </p>
    )
  }

  if (connections.length === 0) {
    return <p className="py-12 text-center text-sm text-muted-foreground">{t("empty")}</p>
  }

  return (
    <ul className="flex flex-col gap-2">
      {connections.map((connection) => (
        <ConnectionRow key={connection.id} connection={connection} onRemove={onRemove} />
      ))}
    </ul>
  )
}
