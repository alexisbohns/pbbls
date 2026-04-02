"use client"

import { Trash2 } from "lucide-react"
import { PEBBLE_SHAPES } from "@/lib/config"
import type { Mark } from "@/lib/types"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { Button } from "@/components/ui/button"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"

type GlyphDetailProps = {
  mark: Mark
  onDelete: () => void
}

export function GlyphDetail({ mark, onDelete }: GlyphDetailProps) {
  const shape = PEBBLE_SHAPES.find((s) => s.id === mark.shape_id)
  const created = new Intl.DateTimeFormat(undefined, {
    dateStyle: "long",
  }).format(new Date(mark.created_at))

  return (
    <article>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold">
          {mark.name || "Untitled glyph"}
        </h1>

        <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          {shape && <span>{shape.name}</span>}
          <span aria-label={`${mark.strokes.length} strokes`}>
            {mark.strokes.length}{" "}
            {mark.strokes.length === 1 ? "stroke" : "strokes"}
          </span>
          <time dateTime={mark.created_at}>{created}</time>
        </div>
      </header>

      <div className="flex justify-center">
        <GlyphPreview
          mark={mark}
          className="w-full max-w-[240px] aspect-square"
        />
      </div>

      <div className="mt-8 flex justify-center">
        <AlertDialog>
          <AlertDialogTrigger
            render={
              <Button variant="outline" size="sm">
                <Trash2 className="size-4" aria-hidden="true" />
                Delete glyph
              </Button>
            }
          />
          <AlertDialogContent size="sm">
            <AlertDialogHeader>
              <AlertDialogTitle>Delete this glyph?</AlertDialogTitle>
              <AlertDialogDescription>
                This action cannot be undone. The glyph will be permanently
                removed.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction variant="destructive" onClick={onDelete}>
                Delete
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>
    </article>
  )
}
