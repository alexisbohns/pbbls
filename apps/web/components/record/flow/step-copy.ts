"use client"

import { useTranslations } from "next-intl"
import type { RecordStep } from "@/lib/record/steps"

export type RecordStepCopy = { title: string; subtitle?: string }

/**
 * The flow's per-step copy.
 *
 * An exhaustive switch over an eleven-case union is a dispatch table, not
 * branching logic. Written out rather than looked up by a template-literal key
 * so the message catalog stays typed and a step whose copy was never written
 * fails the build.
 */
export function useRecordStepCopy(step: RecordStep): RecordStepCopy {
  const t = useTranslations("record.flow.step")

  switch (step) {
    case "photo":
      return { title: t("photoTitle"), subtitle: t("photoSubtitle") }
    case "when":
      return { title: t("whenTitle") }
    case "name":
      return { title: t("nameTitle") }
    case "valence":
      return { title: t("valenceTitle"), subtitle: t("valenceSubtitle") }
    case "emotion":
      return { title: t("emotionTitle") }
    case "domain":
      return { title: t("domainTitle") }
    case "souls":
      return { title: t("soulsTitle") }
    case "collection":
      return { title: t("collectionTitle") }
    case "glyph":
      return { title: t("glyphTitle"), subtitle: t("glyphSubtitle") }
    case "privacy":
      return { title: t("privacyTitle") }
    case "success":
      return { title: t("successTitle") }
  }
}
