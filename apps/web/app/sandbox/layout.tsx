import type { Metadata } from "next"

// Throwaway experimentation surface (#720) — never indexed.
export const metadata: Metadata = {
  robots: { index: false, follow: false },
}

export default function SandboxLayout({ children }: { children: React.ReactNode }) {
  return children
}
