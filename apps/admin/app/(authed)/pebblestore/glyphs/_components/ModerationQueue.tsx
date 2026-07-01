import type { AdminSubmission } from "@/lib/pebblestore/types"
import { SubmissionCard } from "./SubmissionCard"

export function ModerationQueue({ submissions }: { submissions: AdminSubmission[] }) {
  if (submissions.length === 0) {
    return (
      <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
        No submissions in this state.
      </p>
    )
  }
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {submissions.map((s) => (
        <SubmissionCard key={s.submission_id} submission={s} />
      ))}
    </div>
  )
}
