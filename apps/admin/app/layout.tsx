import type { Metadata } from "next"
import "./globals.css"

export const metadata: Metadata = {
  title: "Pebbles · Back-office",
  description: "Internal admin tools for Pebbles.",
  robots: { index: false, follow: false },
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="bg-background text-foreground min-h-screen">{children}</body>
    </html>
  )
}
