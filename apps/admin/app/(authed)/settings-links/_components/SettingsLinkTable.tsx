import Link from "next/link"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import type { SettingsLinkRow } from "@/lib/settings-links/types"

export function SettingsLinkTable({ links }: { links: SettingsLinkRow[] }) {
  if (links.length === 0) return <p className="text-muted-foreground text-sm">No links yet.</p>
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Section</TableHead>
          <TableHead className="w-16">Order</TableHead>
          <TableHead>Title (EN)</TableHead>
          <TableHead>URL</TableHead>
          <TableHead>Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {links.map((l) => (
          <TableRow key={l.id}>
            <TableCell>{l.section}</TableCell>
            <TableCell className="tabular-nums">{l.sort_order}</TableCell>
            <TableCell><Link href={`/settings-links/${l.id}`} className="font-medium hover:underline">{l.title_en}</Link></TableCell>
            <TableCell className="max-w-64 truncate font-mono text-xs" title={l.url}>{l.url}</TableCell>
            <TableCell><Badge variant={l.published ? "default" : "outline"}>{l.published ? "Published" : "Draft"}</Badge></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
