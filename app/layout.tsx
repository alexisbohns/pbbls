import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import Link from "next/link";
import { ThemeProvider } from "@/components/layout/ThemeProvider";
import { ThemeToggle } from "@/components/layout/ThemeToggle";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "pbbls",
  description: "Collect meaningful moments, one pebble at a time",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="min-h-full flex flex-col bg-background text-foreground">
        <ThemeProvider>
          <header className="border-b border-border">
            <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
              <nav aria-label="Main navigation">
                <ul className="flex items-center gap-4 text-sm font-medium">
                  <li>
                    <Link href="/path" className="hover:text-primary">
                      Path
                    </Link>
                  </li>
                  <li>
                    <Link href="/record" className="hover:text-primary">
                      Record
                    </Link>
                  </li>
                  <li>
                    <Link href="/collections" className="hover:text-primary">
                      Collections
                    </Link>
                  </li>
                </ul>
              </nav>
              <ThemeToggle />
            </div>
          </header>
          <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
            {children}
          </main>
        </ThemeProvider>
      </body>
    </html>
  );
}
