"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { createClient } from "@/lib/supabase/browser"

const BUCKET = "lab-assets"
const ACCEPT = "image/png,image/jpeg,image/webp"
const MAX_BYTES = 5 * 1024 * 1024

// Use the Supabase SDK to derive the public URL — avoids hard-coding the URL pattern.
function publicUrl(supabase: ReturnType<typeof createClient>, path: string) {
  return supabase.storage.from(BUCKET).getPublicUrl(path).data.publicUrl
}

export function CoverImageInput({ defaultPath }: { defaultPath: string | null }) {
  const [supabase] = useState(() => createClient())
  const [path, setPath] = useState<string>(defaultPath ?? "")
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function handleFile(file: File) {
    setError(null)

    if (!ACCEPT.split(",").includes(file.type)) {
      setError("Use PNG, JPEG, or WEBP.")
      return
    }
    if (file.size > MAX_BYTES) {
      setError("Image must be 5 MB or smaller.")
      return
    }

    // Derive extension safely — files without a dot fall back to "bin".
    const lastDot = file.name.lastIndexOf(".")
    const ext = lastDot > 0 ? file.name.slice(lastDot + 1).toLowerCase() : "bin"
    const objectPath = `covers/${crypto.randomUUID()}.${ext}`

    setPending(true)
    try {
      const { error: upErr } = await supabase.storage
        .from(BUCKET)
        .upload(objectPath, file, { contentType: file.type, upsert: false })

      if (upErr) {
        console.error("[CoverImageInput] upload failed:", upErr.message)
        setError("Upload failed. Check the console.")
        return
      }

      setPath(objectPath)
    } finally {
      setPending(false)
    }
  }

  async function handleRemove() {
    if (!path) return
    const oldPath = path
    // Clear state immediately so UX is responsive regardless of cleanup outcome.
    setPath("")
    // Best-effort delete — don't block the UX on failure.
    const { error: rmErr } = await supabase.storage.from(BUCKET).remove([oldPath])
    if (rmErr) {
      console.warn("[CoverImageInput] storage cleanup failed for", oldPath, rmErr.message)
    }
  }

  return (
    <div className="space-y-2">
      <Label>Cover image</Label>
      <input type="hidden" name="cover_image_path" value={path} readOnly />

      {path ? (
        <div className="space-y-2">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={publicUrl(supabase, path)}
            alt="Cover preview"
            className="border-border h-40 w-full rounded-md border object-cover"
          />
          <div className="flex items-center gap-2">
            <Input value={path} readOnly className="font-mono text-xs" />
            <Button type="button" variant="outline" onClick={() => void handleRemove()}>
              Remove
            </Button>
          </div>
        </div>
      ) : (
        <Input
          type="file"
          accept={ACCEPT}
          disabled={pending}
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) void handleFile(file)
          }}
        />
      )}

      {pending ? <p className="text-muted-foreground text-sm">Uploading…</p> : null}
      {error ? (
        <p role="alert" className="text-destructive text-sm">
          {error}
        </p>
      ) : null}
    </div>
  )
}
