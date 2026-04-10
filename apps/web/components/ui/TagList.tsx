import { Badge } from "@/components/ui/badge"

type TagListProps = {
  items: { id: string; name: string }[]
  className?: string
}

export function TagList({ items, className }: TagListProps) {
  if (items.length === 0) return null

  return (
    <ul className={className ?? "mt-2 flex flex-wrap gap-2"} role="list">
      {items.map((item) => (
        <li key={item.id}>
          <Badge variant="outline">{item.name}</Badge>
        </li>
      ))}
    </ul>
  )
}
