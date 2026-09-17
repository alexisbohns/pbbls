// apps/admin/app/(authed)/playground/moderation/page.tsx
//
// Every state the content-report queue can render, without seeding real
// reports — filing one means filing abuse against a real account.

import { REPORT_FIXTURES } from "@/lib/moderation/__fixtures__/reports"
import { ReportQueue } from "../../moderation/reports/_components/ReportQueue"

export default function ModerationPlayground() {
  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-lg font-semibold">Content report queue</h1>
        <p className="text-sm text-muted-foreground">
          Fixture states. The action buttons call the real server actions, so they will fail on
          these fake ids — that is expected here.
        </p>
      </div>

      {REPORT_FIXTURES.map(({ label, note, report }) => (
        <section key={report.id} className="space-y-2">
          <h2 className="text-sm font-medium">{label}</h2>
          <p className="text-xs text-muted-foreground">{note}</p>
          <ReportQueue reports={[report]} />
        </section>
      ))}

      <section className="space-y-2">
        <h2 className="text-sm font-medium">Empty</h2>
        <ReportQueue reports={[]} />
      </section>
    </div>
  )
}
