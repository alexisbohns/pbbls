"use client"

import { useId } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Label } from "@/components/ui/label"
import { PLATFORM_FILTER_OPTIONS } from "@/lib/logs/options"

const ANY = "any"

export function PlatformFilter() {
  const id = useId()
  const router = useRouter()
  const params = useSearchParams()
  const current = params.get("platform") ?? ANY

  function onChange(value: string) {
    const next = new URLSearchParams(params)
    if (value === ANY) {
      next.delete("platform")
    } else {
      next.set("platform", value)
    }
    router.push(`/logs/features${next.size ? `?${next.toString()}` : ""}`)
  }

  return (
    <div className="space-y-1 text-sm">
      <Label htmlFor={id} className="text-muted-foreground">
        Platform
      </Label>
      <Select value={current} onValueChange={(v) => onChange(v ?? ANY)}>
        <SelectTrigger id={id} className="w-44">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ANY}>Any platform</SelectItem>
          {PLATFORM_FILTER_OPTIONS.map((o) => (
            <SelectItem key={o.value} value={o.value}>
              {o.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
