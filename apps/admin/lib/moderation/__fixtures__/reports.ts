// apps/admin/lib/moderation/__fixtures__/reports.ts
//
// States a moderator will actually meet, for the playground. Seeding real
// reports means filing abuse against a real account, so this is the only
// honest way to review the queue's rendering.

import type { ContentReport } from "../types"

const BASE = {
  reporter_id: "11111111-1111-1111-1111-111111111111",
  target_user_id: "22222222-2222-2222-2222-222222222222",
  resolution_note: null,
  resolved_at: null,
  resolved_by: null,
  target_missing: false,
  open_reports_against_target: 1,
  status: "open",
} as const

export const REPORT_FIXTURES: { label: string; note: string; report: ContentReport }[] = [
  {
    label: "Pebble, unchanged since reported",
    note: "The ordinary case. Snapshot and live agree, so the moderator judges one thing.",
    report: {
      ...BASE,
      id: "r-1",
      target_kind: "pebble",
      target_id: "p-1",
      reason: "harassment",
      detail: "This is about me and names my employer.",
      target_snapshot: { name: "A bad day", description: "Detailed rant naming a person." },
      target_live: {
        name: "A bad day",
        description: "Detailed rant naming a person.",
        visibility: "public",
        hidden_at: null,
      },
      created_at: "2026-09-14T09:12:00Z",
    },
  },
  {
    label: "Pebble, EDITED after being reported",
    note: "The case target_snapshot exists for. Without the left column the moderator sees innocent text and dismisses it.",
    report: {
      ...BASE,
      id: "r-2",
      target_kind: "pebble",
      target_id: "p-2",
      reason: "hate",
      detail: null,
      target_snapshot: { name: "Slur in the title", description: "And more of it here." },
      target_live: {
        name: "Nice day out",
        description: "Had a lovely walk.",
        visibility: "public",
        hidden_at: null,
      },
      created_at: "2026-09-14T10:30:00Z",
    },
  },
  {
    label: "Pile-on — many open reports on one target",
    note: "The count is what separates one bad pebble from a bad account.",
    report: {
      ...BASE,
      id: "r-3",
      target_kind: "pebble",
      target_id: "p-3",
      reason: "sexual",
      detail: "Explicit image described in the text.",
      open_reports_against_target: 12,
      target_snapshot: { name: "NSFW", description: "Explicit." },
      target_live: { name: "NSFW", description: "Explicit.", visibility: "public", hidden_at: null },
      created_at: "2026-09-15T08:00:00Z",
    },
  },
  {
    label: "Profile impersonation, handle still held",
    note: "Only this state offers Release handle — the destructive option, and the right one here.",
    report: {
      ...BASE,
      id: "r-4",
      target_kind: "profile",
      target_id: "22222222-2222-2222-2222-222222222222",
      reason: "impersonation",
      detail: "Pretending to be the Pebbles team.",
      target_snapshot: { handle: "pebblessupport", display_name: "Pebbles Support" },
      target_live: {
        handle: "pebblessupport",
        display_name: "Pebbles Support",
        public_profile: true,
        hidden_at: null,
      },
      created_at: "2026-09-15T11:45:00Z",
    },
  },
  {
    label: "Orphan — owner deleted the content first",
    note: "Self-remediation. Only the snapshot survives; the moderator dismisses.",
    report: {
      ...BASE,
      id: "r-5",
      target_kind: "pebble",
      target_id: "p-5",
      reason: "spam",
      detail: null,
      target_missing: true,
      target_snapshot: { name: "BUY FOLLOWERS", description: "link link link" },
      target_live: null,
      created_at: "2026-09-13T16:20:00Z",
    },
  },
  {
    label: "Already actioned, content hidden",
    note: "Offers Restore. Since #833 a takedown is reversible, so the appeal path is a button, not a migration.",
    report: {
      ...BASE,
      id: "r-6",
      target_kind: "pebble",
      target_id: "p-6",
      reason: "violence",
      detail: "Graphic threat.",
      status: "actioned",
      resolution_note: "Clear threat against a named person.",
      resolved_at: "2026-09-15T12:00:00Z",
      resolved_by: "33333333-3333-3333-3333-333333333333",
      target_snapshot: { name: "Threat", description: "Graphic." },
      target_live: {
        name: "Threat",
        description: "Graphic.",
        visibility: "public",
        hidden_at: "2026-09-15T12:00:00Z",
      },
      created_at: "2026-09-15T09:00:00Z",
    },
  },
  {
    label: "Glyph in the Market",
    note: "Taken down by delisting, not hiding — glyphs keep their own moderation vocabulary.",
    report: {
      ...BASE,
      id: "r-7",
      target_kind: "glyph",
      target_id: "g-1",
      reason: "hate",
      detail: "The shape is a hate symbol.",
      target_snapshot: { name: "symbol" },
      target_live: { name: "symbol", listed: true },
      created_at: "2026-09-16T07:30:00Z",
    },
  },
]
