import { createElement, type ComponentProps } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import { SignOutSection } from "@/components/settings/SignOutSection"

// Log out lives in Settings on every surface (#1384 D20).
describe("the sign-out section", () => {
  it("renders log out as its own M3 list group under its header", () => {
    const intl: ComponentProps<typeof NextIntlClientProvider> = {
      locale: "en",
      messages: en,
      timeZone: "UTC",
      children: createElement(SignOutSection, { onSignOut: async () => {} }),
    }
    const markup = renderToStaticMarkup(createElement(NextIntlClientProvider, intl))
    expect(markup).toMatch(/<section aria-labelledby="settings-sign-out"[^>]*><h3 id="settings-sign-out"[^>]*>Session<\/h3>/)
    expect(markup).toMatch(/<ul aria-labelledby="settings-sign-out" class="m3-list-group"><li><button type="button"[^>]*><span[^>]*>Log out<\/span><\/button><\/li><\/ul>/)
  })
})
