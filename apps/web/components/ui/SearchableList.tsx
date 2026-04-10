import type { ReactNode } from "react"
import { Search } from "lucide-react"

type SearchableListProps = {
  query: string
  onQueryChange: (query: string) => void
  placeholder?: string
  children: ReactNode
  emptyMessage?: string
  isEmpty?: boolean
  onKeyDown?: (e: React.KeyboardEvent<HTMLInputElement>) => void
}

export function SearchableList({
  query,
  onQueryChange,
  placeholder = "Search\u2026",
  children,
  emptyMessage = "No results found",
  isEmpty,
  onKeyDown,
}: SearchableListProps) {
  return (
    <>
      <div className="flex items-center gap-2 border-b border-border pb-2 mb-1">
        <Search className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        <input
          type="text"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder={placeholder}
          className="h-7 w-full min-w-0 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          onKeyDown={onKeyDown}
        />
      </div>
      <div className="max-h-[200px] overflow-y-auto">
        {children}
        {isEmpty && (
          <p className="px-2 py-4 text-center text-sm text-muted-foreground">
            {emptyMessage}
          </p>
        )}
      </div>
    </>
  )
}
