import { createElement, type ComponentProps } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import { SignOutSection } from "@/components/settings/SignOutSection"

// Log out lives in Settings on every surface (#1384 D20).
describe("the sign-out section", () => {
  it("renders the log-out button under its title", () => {
    const intl: ComponentProps<typeof NextIntlClientProvider> = {
      locale: "en",
      messages: en,
      timeZone: "UTC",
      children: createElement(SignOutSection, { onSignOut: async () => {} }),
    }
    const markup = renderToStaticMarkup(createElement(NextIntlClientProvider, intl))
    expect(markup).toMatch(/<section aria-labelledby="settings-sign-out"[^>]*><span id="settings-sign-out"[^>]*>Session<\/span>/)
    expect(markup).toMatch(/<button type="button"[^>]*>Log out<\/button>/)
  })
})
