import type { Metadata } from "next"
// The Spine specimens on /sandbox/design (#1052) need the same cuts as /path.
import "@fontsource-variable/ysabeau-sc"
import "@fontsource-variable/ysabeau-office"
// The achievements specimen sets real italics; the root layout loads Ysabeau upright only.
import "@fontsource-variable/ysabeau/wght-italic.css"

// Throwaway experimentation surface (#720) — never indexed.
export const metadata: Metadata = {
  robots: { index: false, follow: false },
}

export default function SandboxLayout({ children }: { children: React.ReactNode }) {
  return children
}
