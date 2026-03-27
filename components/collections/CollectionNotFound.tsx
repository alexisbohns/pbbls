import Link from "next/link"

export function CollectionNotFound() {
  return (
    <section className="flex flex-col items-center justify-center gap-4 py-20 text-center">
      <h1 className="text-2xl font-semibold">Collection not found</h1>
      <p className="text-sm text-muted-foreground">
        This collection doesn&apos;t exist or may have been removed.
      </p>
      <Link
        href="/collections"
        className="text-sm font-medium text-primary underline underline-offset-4 hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        Back to Collections
      </Link>
    </section>
  )
}
