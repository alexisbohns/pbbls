import Link from "next/link"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import type { LogRow } from "@/lib/logs/types"

function formatDate(value: string) {
  return new Date(value).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
}

export function LogSection({
  title,
  logs,
  emptyLabel,
}: {
  title: string
  logs: LogRow[]
  emptyLabel: string
}) {
  return (
    <section className="space-y-3">
      <h2 className="text-base font-semibold">
        {title} <span className="text-muted-foreground font-normal">· {logs.length}</span>
      </h2>
      {logs.length === 0 ? (
        <div className="border-border rounded-md border border-dashed p-8 text-center">
          <p className="text-muted-foreground text-sm">{emptyLabel}</p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Title</TableHead>
              <TableHead>Platform</TableHead>
              <TableHead>Updated</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {logs.map((log) => (
              <TableRow key={log.id}>
                <TableCell className="font-medium">
                  <Link href={`/logs/${log.id}`} className="hover:underline">
                    {log.title_en}
                  </Link>
                </TableCell>
                <TableCell>{log.platform}</TableCell>
                <TableCell className="text-muted-foreground text-sm">
                  {formatDate(log.updated_at)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </section>
  )
}
