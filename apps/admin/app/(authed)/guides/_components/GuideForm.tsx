"use client"

import { useActionState, useId } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Switch } from "@/components/ui/switch"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { ILLUSTRATION_OPTIONS } from "@/lib/guides/options"
import type { GuideRow } from "@/lib/guides/types"
import { MarkdownField } from "../../logs/_components/MarkdownField"
import type { GuideFormResult } from "../actions"

export type GuideFormAction = (state: GuideFormResult, formData: FormData) => Promise<GuideFormResult>

export function GuideForm({
  guide,
  action,
  submitLabel,
  extraActions,
}: {
  guide: GuideRow | null
  action: GuideFormAction
  submitLabel: string
  extraActions?: React.ReactNode
}) {
  const [state, formAction, pending] = useActionState<GuideFormResult, FormData>(action, undefined)

  return (
    <form action={formAction} className="space-y-8">
      <div className="flex flex-wrap gap-4">
        <div className="space-y-2 text-sm">
          <Label htmlFor="slug" className="text-foreground font-medium">
            Slug
          </Label>
          <Input
            id="slug"
            name="slug"
            defaultValue={guide?.slug ?? ""}
            required
            pattern="^[a-z0-9]+(-[a-z0-9]+)*$"
          />
        </div>
        <div className="space-y-2 text-sm">
          <Label htmlFor="sort_order" className="text-foreground font-medium">
            Sort order
          </Label>
          <Input
            id="sort_order"
            name="sort_order"
            type="number"
            min={0}
            max={32767}
            defaultValue={guide?.sort_order ?? 0}
          />
        </div>
        <IllustrationSelect defaultValue={guide?.illustration ?? undefined} />
        <div className="space-y-2 text-sm">
          <Label htmlFor="published" className="text-foreground font-medium">
            Published
          </Label>
          <Switch id="published" name="published" defaultChecked={guide?.published ?? false} />
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">English</h2>
          <div className="space-y-2">
            <Label htmlFor="title_en">Title</Label>
            <Input id="title_en" name="title_en" defaultValue={guide?.title_en ?? ""} required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="summary_en">Summary</Label>
            <Textarea
              id="summary_en"
              name="summary_en"
              rows={2}
              defaultValue={guide?.summary_en ?? ""}
              required
            />
          </div>
          <div className="space-y-2">
            <Label>Body (markdown)</Label>
            <MarkdownField name="body_md_en" defaultValue={guide?.body_md_en} ariaLabel="Body, English" />
          </div>
        </div>

        <div className="space-y-4">
          <h2 className="text-lg font-semibold">Français (optional)</h2>
          <div className="space-y-2">
            <Label htmlFor="title_fr">Titre</Label>
            <Input id="title_fr" name="title_fr" defaultValue={guide?.title_fr ?? ""} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="summary_fr">Résumé</Label>
            <Textarea
              id="summary_fr"
              name="summary_fr"
              rows={2}
              defaultValue={guide?.summary_fr ?? ""}
            />
          </div>
          <div className="space-y-2">
            <Label>Corps (markdown)</Label>
            <MarkdownField name="body_md_fr" defaultValue={guide?.body_md_fr} ariaLabel="Body, French" />
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

function IllustrationSelect({ defaultValue }: { defaultValue: string | undefined }) {
  const id = useId()
  return (
    <div className="space-y-2 text-sm">
      <Label htmlFor={id} className="text-foreground font-medium">
        Illustration
      </Label>
      {/* Base UI Select renders a visually-hidden <input name={name}> automatically */}
      <Select name="illustration" defaultValue={defaultValue ?? null}>
        <SelectTrigger id={id}>
          <SelectValue placeholder="None" />
        </SelectTrigger>
        <SelectContent>
          {ILLUSTRATION_OPTIONS.map((o) => (
            <SelectItem key={o.value} value={o.value}>
              {o.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
