import { NotFoundCard } from "@/components/layout/NotFoundCard"

export function CollectionNotFound() {
  return (
    <NotFoundCard
      title="Collection not found"
      description="This collection doesn't exist or may have been removed."
      href="/collections"
      linkText="Back to Collections"
    />
  )
}
