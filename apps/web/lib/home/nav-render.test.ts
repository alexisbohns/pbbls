import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { createElement, type ComponentProps } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it, vi } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import { AppNavigation } from "@/components/layout/AppNavigation"
import { mainClass } from "@/components/layout/main-class"
import { routeShell } from "@/lib/config/app-routes"

// next/link resolves the root workspace's React (see home-render.test.ts); a
// plain <a> stands in for it.
type Props = { children?: React.ReactNode } & Record<string, unknown>
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: Props & { href: string }) => createElement("a", { href, ...rest }, children),
}))
vi.mock("next/navigation", () => ({ usePathname: () => "/sandbox/design" }))

function html(at: string): string {
  const props: ComponentProps<typeof AppNavigation> = { at }
  const intl: ComponentProps<typeof NextIntlClientProvider> = {
    locale: "en",
    messages: en,
    timeZone: "UTC",
    children: createElement(AppNavigation, props),
  }
  return renderToStaticMarkup(createElement(NextIntlClientProvider, intl))
}

describe("the app navigation (#1384 D2)", () => {
  it("is one landmark of five labelled links, in the iOS and Android order", () => {
    const markup = html("/home")
    expect((markup.match(/<nav /g) ?? []).length).toBe(1)
    expect(markup).toMatch(/^<nav aria-label="Main navigation"/)
    const links = [...markup.matchAll(/<a href="([^"]+)"[^>]*>.*?<\/span><span[^>]*>([^<]+)<\/span><\/a>/g)].map((m) => [m[1], m[2]])
    expect(links).toEqual([
      ["/home", "Home"],
      ["/path", "Path"],
      ["/souls", "People"],
      ["/collections", "Collections"],
      ["/profile", "You"],
    ])
  })

  it("marks the current tab, and only it", () => {
    for (const at of ["/home", "/path", "/souls", "/collections", "/profile"]) {
      const markup = html(at)
      expect((markup.match(/aria-current="page"/g) ?? []).length, at).toBe(1)
      expect(markup, at).toContain(`<a href="${at}" aria-current="page"`)
    }
  })

  it("is the same component on every tab: one markup up to the current mark, always on the M3 roles", () => {
    const strip = (m: string) => m.replace(/ aria-current="page"/g, "")
    expect(strip(html("/path"))).toBe(strip(html("/home")))
    expect(html("/souls")).toMatch(/^<nav aria-label="Main navigation" class="m3 /)
  })

  it("is a bar below the rail breakpoint and a rail from it, by CSS alone", () => {
    const markup = html("/home")
    expect(markup).toContain("fixed inset-x-0 bottom-0")
    expect(markup).toContain("rail:static")
    expect(markup).toContain("rail:w-24")
    expect(markup).toContain("rail:flex-col")
  })
})

describe("the shell's room for it", () => {
  it("comes from one utility on every tab root, the Path included", () => {
    for (const at of ["/home", "/path", "/souls", "/collections", "/profile"]) {
      expect(mainClass(routeShell(at), true).split(" "), at).toContain("app-nav-room")
    }
    expect(mainClass(routeShell("/settings"), false).split(" ")).not.toContain("app-nav-room")
  })

  it("is defined once in globals.css, at Android's Medium width", () => {
    const webRoot = fileURLToPath(new URL("../..", import.meta.url))
    const css = readFileSync(path.join(webRoot, "app/globals.css"), "utf8")
    expect(css).toContain("--breakpoint-rail: 600px;")
    expect(css.match(/@utility app-nav-room \{/g) ?? []).toHaveLength(1)
  })
})
