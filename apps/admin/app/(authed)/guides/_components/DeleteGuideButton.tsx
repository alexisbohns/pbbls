"use client"

import { useState, useTransition } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { deleteGuide } from "../actions"

export function DeleteGuideButton({ id, title }: { id: string; title: string }) {
  const [open, setOpen] = useState(false)
  const [pending, startTransition] = useTransition()

  return (
    <>
      {/* Controlled trigger — Base UI Dialog.Trigger renders a <button> directly and
          does not support asChild like Radix. We keep the dialog fully controlled. */}
      <Button type="button" variant="destructive" onClick={() => setOpen(true)}>
        Delete
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete this guide?</DialogTitle>
            <DialogDescription>
              &ldquo;{title}&rdquo; will be removed from the Home tab&apos;s About section.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={pending}
              onClick={() => {
                startTransition(async () => {
                  await deleteGuide(id)
                })
              }}
            >
              {pending ? "Deleting…" : "Delete"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
