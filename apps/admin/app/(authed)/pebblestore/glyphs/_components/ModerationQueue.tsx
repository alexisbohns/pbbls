import type { AdminSubmission, PebbleShape } from "@/lib/pebblestore/types"
import { SubmissionCard } from "./SubmissionCard"

export function ModerationQueue({
  submissions,
  shapes,
}: {
  submissions: AdminSubmission[]
  shapes: PebbleShape[]
}) {
  if (submissions.length === 0) {
    return (
      <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
        No submissions in this state.
      </p>
    )
  }
  const shapeById = new Map(shapes.map((s) => [s.id, s]))
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {submissions.map((s) => (
        <SubmissionCard key={s.submission_id} submission={s} shape={shapeById.get(s.shape_id ?? "") ?? null} />
      ))}
    </div>
  )
}
