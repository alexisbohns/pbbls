"use client"

import Link from "next/link"
import { ChevronLeft, Loader2 } from "lucide-react"
import { useTranslations } from "next-intl"
import { useConnections } from "@/lib/data/useConnections"
import { useConnectionPebbles } from "@/lib/data/useConnectionPebbles"
import { PeerGlyphIcon } from "@/components/connections/PeerGlyphIcon"
import { ConnectionPebbleTile } from "@/components/connections/ConnectionPebbleTile"
import { useFormatRelativeTime } from "@/lib/i18n"

type ConnectionDetailProps = {
  connectionId: string
}

/**
 * The connection detail screen (M51): a connection's own header (peer glyph,
 * name, connected date — same projection as `ConnectionRow`) plus the pebbles
 * they share with the viewer. `useConnections` doubles as the peer lookup (no
 * dedicated single-connection RPC exists), `useConnectionPebbles` fetches the
 * shared list; `notFound` from the latter and "missing from the former list"
 * are indistinguishable by design (foreign vs. removed connection id).
 *
 * Deviation: the task's instructions pointed at a `components/ui/BackPath`
 * pattern for the happy-path back affordance, but no such component exists in
 * this codebase. Detail pages here (`souls/[id]`, `lab/announcements/[id]`)
 * use a plain `nav > Link` with a chevron instead — mirrored here.
 */
export function ConnectionDetail({ connectionId }: ConnectionDetailProps) {
  const t = useTranslations("connections")
  const formatRelative = useFormatRelativeTime()
  const {
    connections,
    loading: connectionsLoading,
    error: connectionsError,
  } = useConnections()
  const {
    pebbles,
    notFound: pebblesNotFound,
    loading: pebblesLoading,
    error: pebblesError,
  } = useConnectionPebbles(connectionId)

  const loading = connectionsLoading || pebblesLoading
  const error = connectionsError ?? pebblesError
  const connection = connections.find((c) => c.id === connectionId)
  const notFound = pebblesNotFound || (!connectionsLoading && !connection)

  if (loading) {
    return (
      <div className="flex justify-center py-12" aria-live="polite">
        <Loader2 className="size-5 animate-spin text-muted-foreground" aria-label={t("loading")} />
      </div>
    )
  }

  if (notFound) {
    return (
      <div className="flex flex-col items-center gap-4 py-12 text-center">
        <p className="text-sm text-muted-foreground">{t("detailNotFound")}</p>
        <Link
          href="/connections"
          className="text-sm font-medium text-primary underline underline-offset-4 hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          {t("backToConnections")}
        </Link>
      </div>
    )
  }

  if (error || !connection) {
    return (
      <p role="alert" className="py-12 text-center text-sm text-destructive">
        {t("loadError")}
      </p>
    )
  }

  const name = connection.peer.displayName
  const connectedAgo = formatRelative(connection.connectedAt)

  return (
    <section>
      <nav className="mb-6">
        <Link
          href="/connections"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          <ChevronLeft className="size-4" aria-hidden />
          {t("backToConnections")}
        </Link>
      </nav>

      <header className="mb-8 flex items-center gap-3">
        {connection.peer.glyph ? (
          <span
            aria-hidden
            className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-accent p-2"
          >
            <PeerGlyphIcon glyph={connection.peer.glyph} className="size-full text-foreground" />
          </span>
        ) : (
          <span
            aria-hidden
            className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-accent text-lg font-medium text-muted-foreground"
          >
            {name.charAt(0).toUpperCase() || "?"}
          </span>
        )}
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-xl font-semibold text-foreground">{name}</h1>
          <p className="truncate text-xs text-muted-foreground">
            {t("connectedAgo", { ago: connectedAgo })}
          </p>
        </div>
      </header>

      <h2 className="mb-4 text-sm font-medium text-foreground">{t("sharedTitle")}</h2>
      {pebbles.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">{t("sharedEmpty")}</p>
      ) : (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {pebbles.map((pebble) => (
            <ConnectionPebbleTile key={pebble.id} pebble={pebble} />
          ))}
        </ul>
      )}
    </section>
  )
}
