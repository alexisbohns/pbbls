"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { createClient } from "@/lib/supabase/browser"

const BUCKET = "lab-assets"
const ACCEPT = "image/png,image/jpeg,image/webp"
const MAX_BYTES = 5 * 1024 * 1024

function publicUrl(path: string) {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? ""
  return `${url}/storage/v1/object/public/${BUCKET}/${path}`
}

export function CoverImageInput({ defaultPath }: { defaultPath: string | null }) {
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

    setPending(true)
    try {
      const supabase = createClient()
      const ext = file.name.split(".").pop() ?? "bin"
      const objectPath = `covers/${crypto.randomUUID()}.${ext}`

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

  return (
    <div className="space-y-2">
      <Label>Cover image</Label>
      <input type="hidden" name="cover_image_path" value={path} readOnly />

      {path ? (
        <div className="space-y-2">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={publicUrl(path)}
            alt="Cover preview"
            className="border-border h-40 w-full rounded-md border object-cover"
          />
          <div className="flex items-center gap-2">
            <Input value={path} readOnly className="font-mono text-xs" />
            <Button type="button" variant="outline" onClick={() => setPath("")}>
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
