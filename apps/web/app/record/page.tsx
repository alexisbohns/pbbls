"use client"

import { CaptureFlow } from "@/components/record/capture/CaptureFlow"

/**
 * Capture (#1050): photo · when · name → a moment. The one way to record on
 * web; the M58 ten-step composer and the `?composer=form` single-screen one
 * were retired with the moments split. Completing a moment into a pebble is
 * `/record/complete/[id]`.
 */
export default function RecordPage() {
  return <CaptureFlow />
}
