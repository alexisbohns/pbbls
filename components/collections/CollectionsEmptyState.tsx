export function CollectionsEmptyState() {
  return (
    <section className="flex flex-col items-center justify-center gap-4 py-20 text-center">
      <h2 className="text-lg font-medium">No collections yet</h2>
      <p className="text-sm text-muted-foreground">
        Collections let you group pebbles by theme, goal, or time period.
      </p>
    </section>
  )
}
