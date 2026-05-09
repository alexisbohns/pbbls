"use client"

import { Trash2 } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"

type DeleteSoulDialogProps = {
  soulName: string
  onConfirm: () => void
}

export function DeleteSoulDialog({ soulName, onConfirm }: DeleteSoulDialogProps) {
  const t = useTranslations("souls")
  return (
    <ConfirmDialog
      trigger={
        <Button
          variant="ghost"
          size="icon-xs"
          aria-label={t("deleteAria", { name: soulName })}
        >
          <Trash2 className="size-3.5" />
        </Button>
      }
      title={t("deleteTitle")}
      description={t("deleteDescription", { name: soulName })}
      confirmLabel={t("deleteConfirm")}
      onConfirm={onConfirm}
    />
  )
}
