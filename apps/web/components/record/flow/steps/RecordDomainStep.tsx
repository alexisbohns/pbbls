"use client"

import { useTranslations } from "next-intl"
import { useDomains } from "@/lib/data/useDomains"
import { DomainPickerContent } from "@/components/record/DomainPickerContent"

type RecordDomainStepProps = {
  selected: string | undefined
  onSelect: (id: string) => void
}

/** Step 5 — the life domain, with its glyph and description. */
export function RecordDomainStep({ selected, onSelect }: RecordDomainStepProps) {
  const t = useTranslations("record.flow")
  const { rows, loading } = useDomains()

  if (loading && rows.length === 0) {
    return <p className="py-12 text-center text-sm text-muted-foreground">{t("loading")}</p>
  }

  return <DomainPickerContent domains={rows} selected={selected} onSelect={onSelect} />
}
