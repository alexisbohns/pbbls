"use client"

import { useActionState, useId } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { KEY_PATTERN, SETTINGS_LINK_SECTIONS } from "@/lib/settings-links/options"
import type { SettingsLinkRow } from "@/lib/settings-links/types"
import type { SettingsLinkFormResult } from "../actions"

export type SettingsLinkFormAction = (state: SettingsLinkFormResult, formData: FormData) => Promise<SettingsLinkFormResult>

export function SettingsLinkForm({
  link,
  action,
  submitLabel,
  extraActions,
}: {
  link: SettingsLinkRow | null
  action: SettingsLinkFormAction
  submitLabel: string
  extraActions?: React.ReactNode
}) {
  const [state, formAction, pending] = useActionState<SettingsLinkFormResult, FormData>(action, undefined)

  return (
    <form action={formAction} className="space-y-8">
      <div className="flex flex-wrap gap-4">
        <div className="space-y-2 text-sm">
          <Label htmlFor="key" className="text-foreground font-medium">
            Key
          </Label>
          <Input
            id="key"
            name="key"
            defaultValue={link?.key ?? ""}
            required
            pattern={KEY_PATTERN.source}
          />
        </div>
        <SectionSelect defaultValue={link?.section ?? undefined} />
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
            defaultValue={link?.sort_order ?? 0}
          />
        </div>
        <div className="space-y-2 text-sm">
          <Label htmlFor="published" className="text-foreground font-medium">
            Published
          </Label>
          <Switch id="published" name="published" defaultChecked={link?.published ?? false} />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="url">URL</Label>
        <Input
          id="url"
          name="url"
          type="text"
          inputMode="url"
          placeholder="https://… or mailto:…"
          defaultValue={link?.url ?? ""}
          required
        />
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">English</h2>
          <div className="space-y-2">
            <Label htmlFor="title_en">Title</Label>
            <Input id="title_en" name="title_en" defaultValue={link?.title_en ?? ""} required />
          </div>
        </div>

        <div className="space-y-4">
          <h2 className="text-lg font-semibold">Français (optional)</h2>
          <div className="space-y-2">
            <Label htmlFor="title_fr">Titre</Label>
            <Input id="title_fr" name="title_fr" defaultValue={link?.title_fr ?? ""} />
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

function SectionSelect({ defaultValue }: { defaultValue: string | undefined }) {
  const id = useId()
  return (
    <div className="space-y-2 text-sm">
      <Label htmlFor={id} className="text-foreground font-medium">
        Section
      </Label>
      {/* Base UI Select renders a visually-hidden <input name={name}> automatically */}
      <Select name="section" defaultValue={defaultValue ?? null}>
        <SelectTrigger id={id}>
          <SelectValue placeholder="Pick a section" />
        </SelectTrigger>
        <SelectContent>
          {SETTINGS_LINK_SECTIONS.map((s) => (
            <SelectItem key={s} value={s}>
              {s}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
