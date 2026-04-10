import { NotFoundCard } from "@/components/layout/NotFoundCard"

export function SoulNotFound() {
  return (
    <NotFoundCard
      title="Soul not found"
      description="This soul doesn't exist or may have been removed."
      href="/souls"
      linkText="Back to Souls"
    />
  )
}
