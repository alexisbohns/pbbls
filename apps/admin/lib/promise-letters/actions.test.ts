import { beforeEach, describe, expect, it, vi } from "vitest"
import { deletePromiseLetter, updatePromiseLetter } from "@/app/(authed)/promise/actions"

const written = vi.hoisted(() => ({ result: { data: [] as { id: string }[], error: null as { message: string } | null } }))

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }))
vi.mock("next/navigation", () => ({ redirect: vi.fn() }))
vi.mock("@/lib/supabase/server", () => {
  const eq = () => ({ select: async () => written.result })
  return {
    createServerSupabaseClient: async () => ({
      from: () => ({ update: () => ({ eq }), delete: () => ({ eq }) }),
    }),
  }
})

function letterForm(): FormData {
  const formData = new FormData()
  formData.set("body_md_en", "Dear you")
  return formData
}

describe("promise letter writes", () => {
  beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    written.result = { data: [], error: null }
  })

  it("reports an update RLS filtered down to zero rows", async () => {
    await expect(updatePromiseLetter("id", undefined, letterForm())).resolves.toEqual({ error: "Could not save the letter." })
  })

  it("reports success when the update touched the row", async () => {
    written.result = { data: [{ id: "id" }], error: null }
    await expect(updatePromiseLetter("id", undefined, letterForm())).resolves.toBeUndefined()
  })

  it("throws on a delete RLS filtered down to zero rows", async () => {
    await expect(deletePromiseLetter("id")).rejects.toThrow("Could not delete the letter.")
  })
})
