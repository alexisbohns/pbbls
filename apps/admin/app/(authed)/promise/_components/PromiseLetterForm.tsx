"use client"

import { startTransition, useActionState, type FormEvent } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import type { PromiseLetterRow } from "@/lib/promise-letters/types"
import type { PromiseLetterFormResult } from "../actions"

export type PromiseLetterFormAction = (state: PromiseLetterFormResult, formData: FormData) => Promise<PromiseLetterFormResult>

export function PromiseLetterForm({
  letter,
  action,
  submitLabel,
  extraActions,
}: {
  letter: PromiseLetterRow | null
  action: PromiseLetterFormAction
  submitLabel: string
  extraActions?: React.ReactNode
}) {
  const [state, formAction, pending] = useActionState<PromiseLetterFormResult, FormData>(action, undefined)

  // Dispatching by hand skips React's automatic reset after `<form action>`. That
  // reset puts the Base UI Switch's hidden checkbox out of step with what it shows,
  // so the next save could flip `published`, and it erases a letter whose save failed.
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formData = new FormData(event.currentTarget)
    startTransition(() => formAction(formData))
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-8">
      <div className="space-y-2 text-sm">
        <Label htmlFor="published" className="text-foreground font-medium">
          Published
        </Label>
        <Switch id="published" name="published" defaultChecked={letter?.published ?? false} />
        <p className="text-muted-foreground">The apps show the most recently updated published letter, so saving a published letter makes it the live one.</p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">English</h2>
          <div className="space-y-2">
            <Label htmlFor="body_md_en">Letter (Markdown, one paragraph per blank line)</Label>
            <Textarea id="body_md_en" name="body_md_en" rows={10} defaultValue={letter?.body_md_en ?? ""} required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="signed_by_en">Signed by</Label>
            <Input id="signed_by_en" name="signed_by_en" defaultValue={letter?.signed_by_en ?? "The Pebbles team"} />
          </div>
        </div>
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">Français (optional)</h2>
          <div className="space-y-2">
            <Label htmlFor="body_md_fr">Lettre</Label>
            <Textarea id="body_md_fr" name="body_md_fr" rows={10} defaultValue={letter?.body_md_fr ?? ""} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="signed_by_fr">Signée par</Label>
            <Input id="signed_by_fr" name="signed_by_fr" defaultValue={letter?.signed_by_fr ?? ""} />
          </div>
        </div>
      </div>

      {state?.error ? (
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      ) : null}

      <div className="flex items-center gap-3">
        <Button type="submit" disabled={pending}>
          {pending ? "Saving…" : submitLabel}
        </Button>
        {extraActions}
      </div>
    </form>
  )
}
