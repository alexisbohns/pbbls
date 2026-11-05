// @vitest-environment jsdom
import { act, createElement } from "react"
import { createRoot, type Root } from "react-dom/client"
import { afterEach, describe, expect, it, vi } from "vitest"
import { PromiseLetterForm } from "@/app/(authed)/promise/_components/PromiseLetterForm"
import type { PromiseLetterFormResult } from "@/app/(authed)/promise/actions"
import type { PromiseLetterRow } from "./types"

const IS_ACT = "IS_REACT_ACT_ENVIRONMENT"
Reflect.set(globalThis, IS_ACT, true)

function letterWith(published: boolean): PromiseLetterRow {
  return {
    id: "letter-1",
    body_md_en: "Dear you",
    body_md_fr: null,
    signed_by_en: "The Pebbles team",
    signed_by_fr: null,
    published,
    created_at: "2026-11-05T00:00:00Z",
    updated_at: "2026-11-05T00:00:00Z",
  }
}

function must<T>(value: T | null | undefined): T {
  if (value === null || value === undefined) throw new Error("element not rendered")
  return value
}

describe("PromiseLetterForm", () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(async () => {
    await act(async () => root?.unmount())
    container?.remove()
  })

  async function render(published: boolean, result: PromiseLetterFormResult = undefined) {
    const sent: FormData[] = []
    const action = vi.fn(async (_state: PromiseLetterFormResult, formData: FormData) => {
      sent.push(formData)
      return result
    })
    container = document.createElement("div")
    document.body.appendChild(container)
    root = createRoot(container)
    await act(async () => {
      root?.render(createElement(PromiseLetterForm, { letter: letterWith(published), action, submitLabel: "Save" }))
    })
    const el = container
    return {
      sent,
      toggle: () => act(async () => must(el.querySelector<HTMLElement>("[role=switch]")).click()),
      save: () =>
        act(async () => {
          must(el.querySelector<HTMLElement>("button[type=submit]")).click()
          await new Promise((resolve) => setTimeout(resolve, 20))
        }),
      shown: () => must(el.querySelector("[role=switch]")).getAttribute("aria-checked"),
      hiddenChecked: () => must(el.querySelector<HTMLInputElement>("input[type=checkbox]")).checked,
      body: () => must(el.querySelector<HTMLTextAreaElement>("#body_md_en")),
    }
  }

  it.each([
    { initial: false, afterToggle: "on" },
    { initial: true, afterToggle: null },
  ])("keeps published from $initial across repeated saves", async ({ initial, afterToggle }) => {
    const form = await render(initial)
    await form.toggle()
    await form.save()
    await form.save()
    expect(form.sent.map((fd) => fd.get("published"))).toEqual([afterToggle, afterToggle])
    expect(form.shown()).toBe(String(!initial))
    expect(form.hiddenChecked()).toBe(!initial)
  })

  it("keeps the typed letter when a save fails", async () => {
    const form = await render(false, { error: "Could not save the letter." })
    form.body().value = "A long edited letter"
    await form.save()
    expect(form.sent).toHaveLength(1)
    expect(form.body().value).toBe("A long edited letter")
    expect(container?.querySelector("[role=alert]")?.textContent).toBe("Could not save the letter.")
  })
})
