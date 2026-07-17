import { redirect } from "next/navigation"

// Emotions is split into two pages (Palettes, Emojis); default to Palettes.
export default function EmotionsIndexPage() {
  redirect("/emotions/palettes")
}
