"use client"

import { useRouter, useSearchParams } from "next/navigation"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Button } from "@/components/ui/button"
import { SPECIES_OPTIONS, STATUS_OPTIONS } from "@/lib/logs/options"

const PUBLISHED_OPTIONS = [
  { value: "all", label: "All" },
  { value: "true", label: "Published" },
  { value: "false", label: "Drafts" },
] as const

export function LogsFilters() {
  const router = useRouter()
  const params = useSearchParams()

  function setParam(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value === "all" || value === "") {
      next.delete(key)
    } else {
      next.set(key, value)
    }
    router.push(`/logs${next.size ? `?${next.toString()}` : ""}`)
  }

  return (
    <div className="flex flex-wrap items-end gap-3">
      <FilterSelect
        label="Species"
        value={params.get("species") ?? "all"}
        onChange={(v) => setParam("species", v)}
        options={[{ value: "all", label: "All species" }, ...SPECIES_OPTIONS]}
      />
      <FilterSelect
        label="Status"
        value={params.get("status") ?? "all"}
        onChange={(v) => setParam("status", v)}
        options={[{ value: "all", label: "All statuses" }, ...STATUS_OPTIONS]}
      />
      <FilterSelect
        label="Published"
        value={params.get("published") ?? "all"}
        onChange={(v) => setParam("published", v)}
        options={PUBLISHED_OPTIONS}
      />
      {params.size > 0 ? (
        <Button variant="ghost" size="sm" onClick={() => router.push("/logs")}>
          Clear filters
        </Button>
      ) : null}
    </div>
  )
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: string
  onChange: (v: string) => void
  options: ReadonlyArray<{ value: string; label: string }>
}) {
  return (
    <label className="space-y-1 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <Select value={value} onValueChange={(v) => onChange(v ?? "all")}>
        <SelectTrigger className="w-44">
          <SelectValue />
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
