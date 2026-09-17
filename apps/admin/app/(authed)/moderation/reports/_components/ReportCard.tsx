"use client"

import { useState, useTransition } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { REASON_LABELS, type ContentReport } from "@/lib/moderation/types"
import { releaseHandle, resolveReport, setContentHidden } from "../actions"
import { TargetDiff } from "./TargetDiff"

type Mode = "action" | "dismiss" | "unhide" | "release" | null

const KIND_LABELS: Record<ContentReport["target_kind"], string> = {
  pebble: "Pebble",
  profile: "Profile",
  glyph: "Glyph",
}

/** Whole-second UTC in, readable local out. */
function when(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

export function ReportCard({ report }: { report: ContentReport }) {
  const [mode, setMode] = useState<Mode>(null)
  const [note, setNote] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [pending, startTransition] = useTransition()

  const close = () => {
    setMode(null)
    setError(null)
    setNote("")
  }

  const run = (fn: () => Promise<{ error: string } | undefined>, successMsg: string) => {
    setError(null)
    startTransition(async () => {
      const res = await fn()
      if (res?.error) {
        setError(res.error)
        return
      }
      toast.success(successMsg)
      close()
    })
  }

  const open = report.status === "open"
  const hidden = report.target_live?.hidden_at != null
  // Releasing a handle only makes sense for a profile that still holds one.
  const canRelease =
    report.target_kind === "profile" && !report.target_missing && !!report.target_live?.handle

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-center gap-2">
        <Badge>{KIND_LABELS[report.target_kind]}</Badge>
        <Badge variant="secondary">{REASON_LABELS[report.reason] ?? report.reason}</Badge>
        {report.open_reports_against_target > 1 ? (
          <Badge variant="destructive">
            {report.open_reports_against_target} open reports on this target
          </Badge>
        ) : null}
        {hidden ? <Badge variant="outline">hidden</Badge> : null}
        {report.target_missing ? <Badge variant="outline">content deleted</Badge> : null}
        <span className="ml-auto text-xs text-muted-foreground">{when(report.created_at)}</span>
      </CardHeader>

      <CardContent className="space-y-4">
        {report.detail ? (
          <blockquote className="border-l-2 pl-3 text-sm italic text-muted-foreground">
            {report.detail}
          </blockquote>
        ) : null}

        <TargetDiff report={report} />

        {report.status !== "open" ? (
          <p className="text-xs text-muted-foreground">
            {report.status === "actioned" ? "Actioned" : "Dismissed"}
            {report.resolved_at ? ` on ${when(report.resolved_at)}` : ""}
            {report.resolution_note ? ` — ${report.resolution_note}` : ""}
          </p>
        ) : null}
      </CardContent>

      <CardFooter className="flex flex-wrap gap-2">
        {open ? (
          <>
            <Button size="sm" onClick={() => setMode("action")} disabled={pending}>
              Take down
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setMode("dismiss")}
              disabled={pending}
            >
              Dismiss
            </Button>
          </>
        ) : null}
        {hidden ? (
          <Button size="sm" variant="outline" onClick={() => setMode("unhide")} disabled={pending}>
            Restore
          </Button>
        ) : null}
        {canRelease ? (
          <Button
            size="sm"
            variant="destructive"
            onClick={() => setMode("release")}
            disabled={pending}
          >
            Release handle
          </Button>
        ) : null}
      </CardFooter>

      <Dialog open={mode !== null} onOpenChange={(v) => (v ? null : close())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {mode === "action"
                ? "Take this content down"
                : mode === "dismiss"
                  ? "Dismiss this report"
                  : mode === "unhide"
                    ? "Restore this content"
                    : "Release this handle"}
            </DialogTitle>
            <DialogDescription>
              {mode === "action"
                ? report.target_kind === "glyph"
                  ? "The glyph leaves the Market. Owners who already bought it keep it."
                  : "The content is hidden from everyone but its author. Their own visibility settings are left untouched, so this can be undone."
                : mode === "dismiss"
                  ? "The report is closed and nothing is removed."
                  : mode === "unhide"
                    ? "The content becomes visible again on every path it was visible on before."
                    : "The handle goes back to the pool and anyone can claim it. This cannot be undone. Use it for impersonation, not for ordinary abuse — hiding the profile is the reversible option."}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-2">
            <Label htmlFor={`note-${report.id}`}>Note {mode === "dismiss" ? "(optional)" : ""}</Label>
            <Textarea
              id={`note-${report.id}`}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Why, for the record"
            />
          </div>

          {error ? <p className="text-sm text-destructive">{error}</p> : null}

          <DialogFooter>
            <Button variant="outline" onClick={close} disabled={pending}>
              Cancel
            </Button>
            <Button
              variant={mode === "release" ? "destructive" : "default"}
              disabled={pending}
              onClick={() => {
                if (mode === "action") {
                  run(() => resolveReport(report.id, "actioned", note), "Content taken down.")
                } else if (mode === "dismiss") {
                  run(() => resolveReport(report.id, "dismissed", note), "Report dismissed.")
                } else if (mode === "unhide") {
                  run(
                    () => setContentHidden(report.target_kind, report.target_id, false, note),
                    "Content restored.",
                  )
                } else if (mode === "release") {
                  run(() => releaseHandle(report.target_user_id, note), "Handle released.")
                }
              }}
            >
              {pending ? "Working…" : "Confirm"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
