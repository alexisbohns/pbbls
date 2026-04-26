import Link from "next/link"
import { Badge } from "@/components/ui/badge"
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

export function LogsTable({ logs }: { logs: LogRow[] }) {
  if (logs.length === 0) {
    return (
      <div className="border-border rounded-md border border-dashed p-12 text-center">
        <p className="text-muted-foreground text-sm">No logs match these filters.</p>
      </div>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Title</TableHead>
          <TableHead>Species</TableHead>
          <TableHead>Platform</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Published</TableHead>
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
            <TableCell>{log.species}</TableCell>
            <TableCell>{log.platform}</TableCell>
            <TableCell>{log.status}</TableCell>
            <TableCell>
              {log.published ? (
                <Badge>Published</Badge>
              ) : (
                <Badge variant="outline">Draft</Badge>
              )}
            </TableCell>
            <TableCell className="text-muted-foreground text-sm">{formatDate(log.updated_at)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
