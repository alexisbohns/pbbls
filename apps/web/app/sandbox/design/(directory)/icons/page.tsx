import type { Metadata } from "next"
import { IconsPage } from "@/components/sandbox/design/pages/IconsPage"
import { loadDesignAudit } from "@/lib/design/audit-source"

export const metadata: Metadata = { title: "Icons · Design" }

export default function DesignIconsPage() {
  return <IconsPage lucide={loadDesignAudit().lucide} />
}
