"use client"

import { RefreshCw } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/layout/EmptyState"

type StoreLoadErrorProps = {
  onRetry: () => void
  retrying: boolean
}

/**
 * Shown when the store failed to load. Its one job is to say the record could
 * not be *reached*, never that it is empty — the bug this replaces showed a
 * years-old account the first-run "carve your first pebble" screen.
 */
export function StoreLoadError({ onRetry, retrying }: StoreLoadErrorProps) {
  const t = useTranslations("errors.store")

  return (
    <EmptyState
      title={t("title")}
      description={t("description")}
      action={
        <Button variant="outline" onClick={onRetry} disabled={retrying}>
          <RefreshCw
            className={retrying ? "size-4 animate-spin" : "size-4"}
            data-icon="inline-start"
          />
          {retrying ? t("retrying") : t("retry")}
        </Button>
      }
    />
  )
}
