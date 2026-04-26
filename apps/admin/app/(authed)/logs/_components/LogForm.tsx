"use client"

import { useActionState } from "react"
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { PLATFORM_OPTIONS, SPECIES_OPTIONS, STATUS_OPTIONS } from "@/lib/logs/options"
import type { LogRow } from "@/lib/logs/types"
import { MarkdownField } from "./MarkdownField"
import { CoverImageInput } from "./CoverImageInput"

export type LogFormResult = { error: string } | undefined
export type LogFormAction = (state: LogFormResult, formData: FormData) => Promise<LogFormResult>

export function LogForm({
  log,
  action,
  submitLabel,
  extraActions,
}: {
  log: LogRow | null
  action: LogFormAction
  submitLabel: string
  extraActions?: React.ReactNode
}) {
  const [state, formAction, pending] = useActionState<LogFormResult, FormData>(action, undefined)

  return (
    <form action={formAction} className="space-y-8">
      <div className="flex gap-4">
        <FieldSelect
          label="Species"
          name="species"
          defaultValue={log?.species}
          options={SPECIES_OPTIONS}
        />
        <FieldSelect
          label="Platform"
          name="platform"
          defaultValue={log?.platform}
          options={PLATFORM_OPTIONS}
        />
        <FieldSelect
          label="Status"
          name="status"
          defaultValue={log?.status}
          options={STATUS_OPTIONS}
        />
      </div>

      <Tabs defaultValue="en" className="flex flex-col space-y-4">
        <TabsList>
          <TabsTrigger value="en">English (required)</TabsTrigger>
          <TabsTrigger value="fr">Français (optional)</TabsTrigger>
        </TabsList>

        <TabsContent value="en" keepMounted className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="title_en">Title</Label>
            <Input id="title_en" name="title_en" defaultValue={log?.title_en ?? ""} required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="summary_en">Summary</Label>
            <Textarea
              id="summary_en"
              name="summary_en"
              rows={3}
              defaultValue={log?.summary_en ?? ""}
              required
            />
          </div>
          <div className="space-y-2">
            <Label>Body (markdown)</Label>
            <MarkdownField name="body_md_en" defaultValue={log?.body_md_en} ariaLabel="Body, English" />
          </div>
        </TabsContent>

        <TabsContent value="fr" keepMounted className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="title_fr">Titre</Label>
            <Input id="title_fr" name="title_fr" defaultValue={log?.title_fr ?? ""} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="summary_fr">Résumé</Label>
            <Textarea
              id="summary_fr"
              name="summary_fr"
              rows={3}
              defaultValue={log?.summary_fr ?? ""}
            />
          </div>
          <div className="space-y-2">
            <Label>Corps (markdown)</Label>
            <MarkdownField name="body_md_fr" defaultValue={log?.body_md_fr} ariaLabel="Body, French" />
          </div>
        </TabsContent>
      </Tabs>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="external_url">External URL</Label>
          <Input
            id="external_url"
            name="external_url"
            type="url"
            defaultValue={log?.external_url ?? ""}
            placeholder="https://…"
          />
        </div>
        <CoverImageInput defaultPath={log?.cover_image_path ?? null} />
      </div>

      <div className="flex items-center gap-3">
        <Switch id="published" name="published" defaultChecked={log?.published ?? false} />
        <Label htmlFor="published" className="cursor-pointer">
          Published
        </Label>
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

function FieldSelect({
  label,
  name,
  defaultValue,
  options,
}: {
  label: string
  name: string
  defaultValue: string | undefined
  options: ReadonlyArray<{ value: string; label: string }>
}) {
  return (
    <label className="space-y-2 text-sm">
      <span className="text-foreground font-medium">{label}</span>
      {/* Base UI Select renders a visually-hidden <input name={name}> automatically */}
      <Select name={name} defaultValue={defaultValue ?? null}>
        <SelectTrigger>
          <SelectValue placeholder={`Select ${label.toLowerCase()}`} />
        </SelectTrigger>
        <SelectContent>
          {options.map((o) => (
            <SelectItem key={o.value} value={o.value}>
              {o.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </label>
  )
}
