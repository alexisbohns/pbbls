import { NotFoundCard } from "@/components/layout/NotFoundCard"

export function PebbleNotFound() {
  return (
    <NotFoundCard
      title="Pebble not found"
      description="This pebble doesn't exist or may have been removed."
      href="/path"
      linkText="Back to Path"
    />
  )
}
