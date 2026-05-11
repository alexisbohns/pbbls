"use client"

import { useTranslations } from "next-intl"

/**
 * Loading / empty placeholder shown while the emotion palette cache hydrates,
 * or when the view returns no rows at all (data bug — visible to surface the
 * problem rather than silently render an empty sheet).
 */
export function EmotionPickerEmpty() {
  const t = useTranslations("record.emotionPicker")
  return (
    <div className="py-12 text-center text-sm text-muted-foreground">
      {t("loading")}
    </div>
  )
}
