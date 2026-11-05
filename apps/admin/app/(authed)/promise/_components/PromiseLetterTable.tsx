import Link from "next/link"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import type { PromiseLetterRow } from "@/lib/promise-letters/types"

export function PromiseLetterTable({ letters }: { letters: PromiseLetterRow[] }) {
  if (letters.length === 0) return <p className="text-muted-foreground text-sm">No letter yet.</p>
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Opening line</TableHead>
          <TableHead>Signed by</TableHead>
          <TableHead>Updated</TableHead>
          <TableHead>Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {letters.map((l) => (
          <TableRow key={l.id}>
            <TableCell>
              <Link href={`/promise/${l.id}`} className="font-medium hover:underline">
                {l.body_md_en.split("\n")[0].slice(0, 80)}
              </Link>
            </TableCell>
            <TableCell>{l.signed_by_en}</TableCell>
            <TableCell className="tabular-nums">{new Date(l.updated_at).toISOString().slice(0, 10)}</TableCell>
            <TableCell><Badge variant={l.published ? "default" : "outline"}>{l.published ? "Published" : "Draft"}</Badge></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
