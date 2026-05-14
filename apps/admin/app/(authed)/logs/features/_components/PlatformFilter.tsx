"use client"

import { useRouter, useSearchParams } from "next/navigation"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { PLATFORM_FILTER_OPTIONS } from "@/lib/logs/options"

const ANY = "any"

export function PlatformFilter() {
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
    <Tabs value={current} onValueChange={(v) => onChange(typeof v === "string" ? v : ANY)}>
      <TabsList>
        <TabsTrigger value={ANY}>Any</TabsTrigger>
        {PLATFORM_FILTER_OPTIONS.map((o) => (
          <TabsTrigger key={o.value} value={o.value}>
            {o.label}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  )
}
