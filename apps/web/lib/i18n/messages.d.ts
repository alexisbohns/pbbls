import type messages from "./messages/en.json"

declare module "next-intl" {
  // Provide message-shape inference for `useTranslations` based on en.json.
  interface AppConfig {
    Messages: typeof messages
  }
}
