"use client"

import { useCallback } from "react"
import { DOMAINS } from "@/lib/config"
import { cn } from "@/lib/utils"

type DomainPickerProps = {
  value: string[]
  onChange: (ids: string[]) => void
}

export function DomainPicker({ value, onChange }: DomainPickerProps) {
  const toggle = useCallback(
    (id: string) => {
      onChange(
        value.includes(id) ? value.filter((v) => v !== id) : [...value, id],
      )
    },
    [value, onChange],
  )

  return (
    <fieldset>
      <legend className="text-sm font-medium">Domains</legend>
      <ul
        role="group"
        aria-label="Life domains"
        className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2"
      >
        {DOMAINS.map((domain) => {
          const selected = value.includes(domain.id)
          return (
            <li key={domain.id}>
              <button
                type="button"
                aria-pressed={selected}
                onClick={() => toggle(domain.id)}
                className={cn(
                  "flex w-full flex-col items-start rounded-lg px-3 py-2 text-sm transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                  selected
                    ? "bg-primary/10 text-primary ring-2 ring-primary"
                    : "bg-muted/50 hover:bg-muted",
                )}
              >
                <span className="font-medium">{domain.name}</span>
                <span
                  className={cn(
                    "text-xs",
                    selected ? "text-primary/70" : "text-muted-foreground",
                  )}
                >
                  {domain.label}
                </span>
              </button>
            </li>
          )
        })}
      </ul>
    </fieldset>
  )
}
