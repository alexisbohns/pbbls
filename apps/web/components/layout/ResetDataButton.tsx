"use client"

import { RotateCcw } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"
import { useReset } from "@/lib/data/useReset"

export function ResetDataButton() {
  const { reset } = useReset()
  const t = useTranslations("profile")

  return (
    <ConfirmDialog
      trigger={
        <Button variant="ghost" size="icon" aria-label={t("resetAria")}>
          <RotateCcw className="size-4" />
        </Button>
      }
      title={t("resetTitle")}
      description={t("resetDescription")}
      confirmLabel={t("resetConfirm")}
      onConfirm={reset}
    />
  )
}
