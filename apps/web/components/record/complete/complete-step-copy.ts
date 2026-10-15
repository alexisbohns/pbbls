"use client"

import { useTranslations } from "next-intl"
import type { CompleteStep } from "@/lib/record/steps"
import type { RecordStepCopy } from "@/components/record/flow/step-copy"

/**
 * The Complete flow's per-step copy. Reuses the record flow's step titles
 * (PD-W17): the questions are the same ones, asked of a moment that exists.
 * Exhaustive so a step without copy fails the build.
 */
export function useCompleteStepCopy(step: CompleteStep): RecordStepCopy {
  const t = useTranslations("record.flow.step")

  switch (step) {
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
    case "privacy":
      return { title: t("privacyTitle") }
    case "success":
      return { title: t("successTitle") }
  }
}
