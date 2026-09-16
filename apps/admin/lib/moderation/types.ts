// apps/admin/lib/moderation/types.ts

/** Target kinds a report can point at — mirrors the SQL CHECK on content_reports. */
export type ReportTargetKind = "pebble" | "profile" | "glyph"

export type ReportStatus = "open" | "actioned" | "dismissed"

/** The nine reason slugs. Clients localise them; the admin shows English copy. */
export type ReportReason =
  | "sexual"
  | "violence"
  | "hate"
  | "harassment"
  | "self_harm"
  | "illegal"
  | "spam"
  | "impersonation"
  | "other"

export const REASON_LABELS: Record<ReportReason, string> = {
  sexual: "Sexual content",
  violence: "Violence",
  hate: "Hate speech",
  harassment: "Harassment",
  self_harm: "Self-harm",
  illegal: "Illegal",
  spam: "Spam",
  impersonation: "Impersonation",
  other: "Other",
}

/**
 * The reported content as it read AT FILE TIME, captured server-side by
 * report_content. Shape varies by target_kind; every field is optional so a
 * future kind cannot break the render.
 */
export type TargetSnapshot = {
  name?: string | null
  description?: string | null
  handle?: string | null
  display_name?: string | null
}

/** Live content beside the snapshot. The two disagreeing is the evidence. */
export type TargetLive = TargetSnapshot & {
  visibility?: string | null
  public_profile?: boolean | null
  listed?: boolean | null
  hidden_at?: string | null
}

/**
 * A row from admin_list_content_reports.
 *
 * Note there is deliberately NO reporter email: admin_list_glyph_submissions
 * joins auth.users for the submitter address and #766 is open against exactly
 * that. A moderator judges the content, not the person who flagged it.
 */
export type ContentReport = {
  id: string
  reporter_id: string | null
  target_kind: ReportTargetKind
  target_id: string
  target_user_id: string
  reason: ReportReason
  detail: string | null
  target_snapshot: TargetSnapshot | null
  target_live: TargetLive | null
  /** The owner deleted the reported content themselves — self-remediation. */
  target_missing: boolean
  /** Separates one bad pebble from a bad account. */
  open_reports_against_target: number
  status: ReportStatus
  resolution_note: string | null
  resolved_at: string | null
  resolved_by: string | null
  created_at: string
}
