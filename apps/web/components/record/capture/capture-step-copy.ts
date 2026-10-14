"use client"

import { useTranslations } from "next-intl"
import type { CaptureStep } from "@/lib/record/capture"
import type { RecordStepCopy } from "@/components/record/flow/RecordStepScaffold"

/**
 * Capture's per-step copy. The photo and when questions are the record flow's
 * (PD-W17); the name step's subtitle is R1's. Exhaustive so a step without
 * copy fails the build.
 */
export function useCaptureStepCopy(step: CaptureStep): RecordStepCopy {
  const t = useTranslations("record.flow.step")
  const tCapture = useTranslations("record.capture")

  switch (step) {
    case "photo":
      return { title: t("photoTitle"), subtitle: t("photoSubtitle") }
    case "when":
      return { title: t("whenTitle") }
    case "name":
      return { title: t("nameTitle"), subtitle: tCapture("nameSubtitle") }
  }
}
