"use client"

import { MagnifyingMd, XSign } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"

type DesignSearchFieldProps = {
  label: string
  placeholder: string
  value: string
  onChange: (next: string) => void
  className?: string
}

/**
 * An M3 search bar for the directory's chrome: a full pill on
 * `surfaceContainerHigh`, the glass leading and a clear button trailing once
 * there is text. It sits beside the `DesignSelect`s at their 48 px height;
 * the parent sets its width.
 * Chrome, so it wears the M3 roles (`.m3`) whatever theme the page shows.
 */
export function DesignSearchField({ label, placeholder, value, onChange, className }: DesignSearchFieldProps) {
  return (
    <div
      role="search"
      className={cn(
        "m3 flex h-12 w-full min-w-0 items-center gap-3 rounded-full bg-m3-surface-container-high pr-1.5 pl-4 text-m3-on-surface transition-colors focus-within:ring-2 focus-within:ring-m3-primary hover:bg-m3-surface-container-highest motion-reduce:transition-none",
        className,
      )}
    >
      <MagnifyingMd className={cn(PBBLS_ICON_SIZE.row, "shrink-0 text-m3-on-surface-variant")} />
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-label={label}
        placeholder={placeholder}
        className="h-full min-w-0 flex-1 bg-transparent text-sm font-medium outline-none placeholder:font-normal placeholder:text-m3-on-surface-variant [&::-webkit-search-cancel-button]:appearance-none"
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange("")}
          aria-label="Clear the search"
          className="flex size-9 shrink-0 items-center justify-center rounded-full text-m3-on-surface-variant outline-none transition-colors hover:bg-m3-on-surface/8 focus-visible:ring-2 focus-visible:ring-m3-primary motion-reduce:transition-none"
        >
          <XSign className={PBBLS_ICON_SIZE.row} />
        </button>
      )}
    </div>
  )
}
