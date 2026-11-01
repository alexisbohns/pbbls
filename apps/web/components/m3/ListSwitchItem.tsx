"use client"

import type { ReactNode } from "react"
import { M3Switch } from "./M3Switch"

type ListSwitchItemProps = {
  headline: ReactNode
  supporting?: ReactNode
  checked: boolean
  onCheckedChange: (checked: boolean) => void
  disabled?: boolean
}

/**
 * A list item that is a switch (#1384, two-area spec D7, as Android #1437):
 * the row is a label around the switch, so a tap anywhere on it toggles, and
 * the switch is named by the row's words.
 */
export function ListSwitchItem({ headline, supporting, checked, onCheckedChange, disabled }: ListSwitchItemProps) {
  return (
    <li>
      <label className="flex min-h-14 w-full cursor-pointer items-center gap-4 bg-m3-surface-container px-4 py-2 has-data-disabled:cursor-not-allowed">
        <span className="min-w-0 flex-1">
          <span className="m3-type-body-large block text-m3-on-surface">{headline}</span>
          {supporting && <span className="m3-type-body-medium block text-m3-on-surface-variant">{supporting}</span>}
        </span>
        <M3Switch checked={checked} onCheckedChange={(next) => onCheckedChange(next)} disabled={disabled} />
      </label>
    </li>
  )
}
