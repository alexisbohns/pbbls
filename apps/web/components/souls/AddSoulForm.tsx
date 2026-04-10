"use client"

import { useState, type FormEvent } from "react"
import { Plus } from "lucide-react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"

type AddSoulFormProps = {
  onAdd: (name: string) => Promise<void>
}

export function AddSoulForm({ onAdd }: AddSoulFormProps) {
  const [name, setName] = useState("")
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) return

    setSubmitting(true)
    try {
      await onAdd(trimmed)
      setName("")
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mb-6 flex gap-2">
      <Input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="New soul name…"
        aria-label="New soul name"
        disabled={submitting}
        className="flex-1"
      />
      <Button
        type="submit"
        size="sm"
        disabled={!name.trim() || submitting}
        aria-label="Add soul"
      >
        <Plus className="size-4" />
        Add
      </Button>
    </form>
  )
}
