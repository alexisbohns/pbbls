import { Skeleton } from "@/components/ui/skeleton"

export function LogSectionSkeleton({ title }: { title: string }) {
  return (
    <section className="space-y-3">
      <h2 className="text-base font-semibold">
        {title} <span className="text-muted-foreground font-normal">· …</span>
      </h2>
      <div className="border-border space-y-2 rounded-md border p-3">
        <Skeleton className="h-6 w-full" />
        <Skeleton className="h-6 w-full" />
        <Skeleton className="h-6 w-full" />
      </div>
    </section>
  )
}
