"use client"

import { useState, useTransition } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
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
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import type { AdminSubmission } from "@/lib/pebblestore/types"
import { approveGlyph, rejectGlyph, setGlyphPrice } from "../actions"

type Mode = "approve" | "reject" | "reprice" | null

export function SubmissionCard({ submission }: { submission: AdminSubmission }) {
  const [mode, setMode] = useState<Mode>(null)
  const [price, setPrice] = useState(String(submission.price))
  const [note, setNote] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [pending, startTransition] = useTransition()

  const close = () => {
    setMode(null)
    setError(null)
    setNote("")
    setPrice(String(submission.price))
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

  const numericPrice = Number(price)
  // Karma prices are whole numbers; the RPC arg is `integer`, so block decimals
  // client-side rather than surfacing an opaque server cast error.
  const priceValid = Number.isInteger(numericPrice) && numericPrice > 0

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-2">
        <span className="truncate text-sm font-medium">{submission.name ?? "Untitled glyph"}</span>
        <Badge variant={submission.status === "approved" ? "default" : "secondary"}>
          {submission.status}
        </Badge>
      </CardHeader>
      <CardContent className="space-y-2">
        <GlyphPreview
          strokes={submission.strokes}
          viewBox={submission.view_box}
          className="aspect-square w-full rounded-md border bg-card text-foreground"
        />
        <div className="text-xs text-muted-foreground">
          {submission.submitter_email ?? submission.submitter_id} · {submission.price} karma
        </div>
        {submission.status === "rejected" && submission.review_note ? (
          <p className="text-xs text-muted-foreground">Reason: {submission.review_note}</p>
        ) : null}
      </CardContent>
      <CardFooter className="gap-2">
        {submission.status === "pending" ? (
          <>
            <Button size="sm" onClick={() => setMode("approve")}>
              Approve
            </Button>
            <Button size="sm" variant="destructive" onClick={() => setMode("reject")}>
              Reject
            </Button>
          </>
        ) : null}
        {submission.status === "approved" ? (
          <Button size="sm" variant="outline" onClick={() => setMode("reprice")}>
            Re-price
          </Button>
        ) : null}
      </CardFooter>

      {/* Approve */}
      <Dialog open={mode === "approve"} onOpenChange={(o) => (o ? setMode("approve") : close())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Approve glyph</DialogTitle>
            <DialogDescription>Publish this glyph to the community market.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="approve-price">Price (karma)</Label>
            <Input
              id="approve-price"
              type="number"
              min={1}
              step={1}
              value={price}
              onChange={(e) => setPrice(e.target.value)}
            />
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={close} disabled={pending}>
              Cancel
            </Button>
            <Button
              disabled={pending || !priceValid}
              onClick={() => run(() => approveGlyph(submission.submission_id, numericPrice), "Glyph approved")}
            >
              {pending ? "Approving…" : "Approve"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reject */}
      <Dialog open={mode === "reject"} onOpenChange={(o) => (o ? setMode("reject") : close())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject glyph</DialogTitle>
            <DialogDescription>The submitter will see this reason.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="reject-note">Reason</Label>
            <Textarea id="reject-note" value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={close} disabled={pending}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={pending || note.trim() === ""}
              onClick={() => run(() => rejectGlyph(submission.submission_id, note.trim()), "Glyph rejected")}
            >
              {pending ? "Rejecting…" : "Reject"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Re-price */}
      <Dialog open={mode === "reprice"} onOpenChange={(o) => (o ? setMode("reprice") : close())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Re-price glyph</DialogTitle>
            <DialogDescription>Existing purchases keep the price they paid.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="reprice-price">Price (karma)</Label>
            <Input
              id="reprice-price"
              type="number"
              min={1}
              step={1}
              value={price}
              onChange={(e) => setPrice(e.target.value)}
            />
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={close} disabled={pending}>
              Cancel
            </Button>
            <Button
              disabled={pending || !priceValid}
              onClick={() => run(() => setGlyphPrice(submission.submission_id, numericPrice), "Price updated")}
            >
              {pending ? "Saving…" : "Save"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
