import type { Metadata } from "next"
import { ColorsPage } from "@/components/sandbox/design/pages/ColorsPage"
import { loadDesignAudit } from "@/lib/design/audit-source"

export const metadata: Metadata = { title: "Colors & type · Design" }

export default function DesignColorsPage() {
  const { hex, colorFunctions, palette, arbitrary, scannedFiles } = loadDesignAudit()
  return <ColorsPage audit={{ hex, colorFunctions, palette, arbitrary, scannedFiles }} />
}
