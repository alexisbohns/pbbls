"use client"

import { useState } from "react"
import { Ban, UserRoundMinus } from "lucide-react"
import { toast } from "sonner"
import { useTranslations } from "next-intl"
import type { Connection } from "@/lib/types"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"
import { PeerGlyphIcon } from "@/components/connections/PeerGlyphIcon"
import { Button } from "@/components/ui/button"
import { useFormatRelativeTime } from "@/lib/i18n"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

type ConnectionRowProps = {
  connection: Connection
  onRemove: (id: string, block?: boolean) => Promise<void>
}

/**
 * One connection: peer glyph + name + relative connected time, with two
 * clearly separated destructive affordances. Plain remove rides the sync
 * `ConfirmDialog` (the removal is optimistic). Remove-and-block uses the
 * `DeleteAccountSection` async busy-dialog shape: blocking waits on the
 * server, so the dialog locks open while in flight.
 */
export function ConnectionRow({ connection, onRemove }: ConnectionRowProps) {
  const t = useTranslations("connections")
  const formatRelative = useFormatRelativeTime()
  const [blockOpen, setBlockOpen] = useState(false)
  const [blocking, setBlocking] = useState(false)

  const name = connection.peer.displayName
  const connectedAgo = formatRelative(connection.connectedAt)

  const handleRemove = () => {
    // Optimistic in the hook (which logs failures); surface only the toast here.
    void onRemove(connection.id, false).catch(() => {
      toast.error(t("removeError"))
    })
  }

  const handleBlock = async () => {
    setBlocking(true)
    try {
      await onRemove(connection.id, true)
      setBlockOpen(false)
    } catch {
      // The hook already console.errors the failure path.
      toast.error(t("blockError"))
      setBlocking(false)
      setBlockOpen(false)
    }
  }

  return (
    <li className="flex items-center gap-3 rounded-xl border border-muted px-3 py-2.5 dark:border-accent">
      {connection.peer.glyph ? (
        <span
          aria-hidden
          className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-accent p-1.5"
        >
          <PeerGlyphIcon glyph={connection.peer.glyph} className="size-full text-foreground" />
        </span>
      ) : (
        <span
          aria-hidden
          className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-accent font-medium text-muted-foreground"
        >
          {name.charAt(0).toUpperCase() || "?"}
        </span>
      )}
      <span className="min-w-0 flex-1">
        <span className="block truncate font-medium text-foreground">{name}</span>
        <span className="block truncate text-xs text-muted-foreground">
          {t("connectedAgo", { ago: connectedAgo })}
        </span>
      </span>

      <ConfirmDialog
        trigger={
          <Button variant="ghost" size="icon" aria-label={t("removeAria", { name })}>
            <UserRoundMinus className="size-4 text-muted-foreground" aria-hidden />
          </Button>
        }
        title={t("removeTitle", { name })}
        description={t("removeDescription")}
        confirmLabel={t("removeConfirm")}
        cancelLabel={t("removeCancel")}
        onConfirm={handleRemove}
      />

      <Button
        variant="ghost"
        size="icon"
        aria-label={t("blockAria", { name })}
        onClick={() => setBlockOpen(true)}
      >
        <Ban className="size-4 text-muted-foreground" aria-hidden />
      </Button>

      <AlertDialog open={blockOpen} onOpenChange={(next) => !blocking && setBlockOpen(next)}>
        <AlertDialogContent size="sm">
          <AlertDialogHeader>
            <AlertDialogTitle>{t("blockTitle", { name })}</AlertDialogTitle>
            <AlertDialogDescription>{t("blockDescription", { name })}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={blocking}>{t("blockCancel")}</AlertDialogCancel>
            <Button variant="destructive" disabled={blocking} onClick={handleBlock}>
              {blocking ? t("blockBusy") : t("blockConfirm")}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </li>
  )
}
