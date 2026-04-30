import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

export function KpiStripSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
      {Array.from({ length: 6 }).map((_, i) => (
        <Card key={i} aria-hidden>
          <CardHeader className="pb-2">
            <Skeleton className="h-3 w-16" />
          </CardHeader>
          <CardContent className="flex items-end justify-between gap-2">
            <div className="space-y-2">
              <Skeleton className="h-7 w-20" />
              <Skeleton className="h-3 w-24" />
            </div>
            <Skeleton className="h-6 w-20" />
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
