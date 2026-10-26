import Link from "next/link"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import type { GuideRow } from "@/lib/guides/types"

export function GuideTable({ guides }: { guides: GuideRow[] }) {
  if (guides.length === 0) return <p className="text-muted-foreground text-sm">No guides yet.</p>
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-16">Order</TableHead>
          <TableHead>Title</TableHead>
          <TableHead>Slug</TableHead>
          <TableHead>Illustration</TableHead>
          <TableHead>French</TableHead>
          <TableHead>Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {guides.map((g) => (
          <TableRow key={g.id}>
            <TableCell className="tabular-nums">{g.sort_order}</TableCell>
            <TableCell><Link href={`/guides/${g.id}`} className="font-medium hover:underline">{g.title_en}</Link></TableCell>
            <TableCell className="font-mono text-xs">{g.slug}</TableCell>
            <TableCell>{g.illustration ?? "—"}</TableCell>
            <TableCell>{g.title_fr && g.summary_fr ? (g.body_md_fr ? "full" : "no body") : "missing"}</TableCell>
            <TableCell><Badge variant={g.published ? "default" : "outline"}>{g.published ? "Published" : "Draft"}</Badge></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
