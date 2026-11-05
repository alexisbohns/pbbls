import { OverviewPage } from "@/components/sandbox/design/pages/OverviewPage"
import { loadDesignAudit } from "@/lib/design/audit-source"

// Prerendered: the audit reads the source once, at build time.
export default function DesignOverviewPage() {
  return <OverviewPage audit={loadDesignAudit()} />
}
