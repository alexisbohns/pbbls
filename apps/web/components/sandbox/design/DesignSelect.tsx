"use client"

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

export type SelectOption<T extends string | number> = { value: T; label: string }

type DesignSelectProps<T extends string | number> = {
  label: string
  options: readonly SelectOption<T>[]
  value: T
  onChange: (next: T) => void
}

/** The menu's caret, drawn in the set's 1.75 line (the hand-drawn set has no chevron yet). */
export function Caret() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4 shrink-0 transition-transform duration-200 group-data-[popup-open]:rotate-180 motion-reduce:transition-none"
    >
      <path d="M6.5 9.6 C8.6 11.6 10.3 13.3 12 15 C13.8 13.2 15.5 11.5 17.5 9.5" />
    </svg>
  )
}

/**
 * An M3 Expressive select for the directory's chrome: a tonal field carrying
 * its label above the value, opening a menu with the large shape where the
 * chosen item sits on `secondaryContainer`. Built on the dropdown menu
 * (base-ui), so the keyboard and the roles come with it. Chrome, so it wears
 * the M3 roles (`.m3`) whatever theme the page shows; the menu is portaled,
 * so it carries the class too.
 */
export function DesignSelect<T extends string | number>({ label, options, value, onChange }: DesignSelectProps<T>) {
  const current = options.find((o) => o.value === value)
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`${label}: ${current?.label ?? ""}`}
        className="group m3 flex h-12 min-w-28 items-center gap-2 rounded-m3-large bg-m3-surface-container-high pr-3 pl-4 text-left text-m3-on-surface outline-none transition-colors hover:bg-m3-surface-container-highest focus-visible:ring-2 focus-visible:ring-m3-primary data-[popup-open]:bg-m3-secondary-container data-[popup-open]:text-m3-on-secondary-container motion-reduce:transition-none"
      >
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="text-[0.6875rem] leading-4 font-medium tracking-wide text-m3-on-surface-variant group-data-[popup-open]:text-m3-on-secondary-container">
            {label}
          </span>
          <span className="truncate text-sm leading-5 font-medium">{current?.label}</span>
        </span>
        <Caret />
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="start"
        className="m3 min-w-(--anchor-width) rounded-m3-large bg-m3-surface-container p-1.5 text-m3-on-surface ring-0 shadow-lg"
      >
        <DropdownMenuRadioGroup
          value={String(value)}
          onValueChange={(next) => {
            const option = options.find((o) => String(o.value) === String(next))
            if (option) onChange(option.value)
          }}
        >
          {options.map((option) => (
            <DropdownMenuRadioItem
              key={option.value}
              value={String(option.value)}
              className="h-11 rounded-m3-medium pr-4 pl-9 text-sm font-medium data-checked:bg-m3-secondary-container data-checked:text-m3-on-secondary-container data-highlighted:bg-m3-on-surface/8 data-highlighted:text-m3-on-surface data-checked:data-highlighted:bg-m3-secondary-container"
            >
              {option.label}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
