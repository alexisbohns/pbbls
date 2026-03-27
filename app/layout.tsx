import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { ThemeProvider } from "@/components/layout/ThemeProvider";
import { DataProvider } from "@/components/layout/DataProvider";
import { Sidebar } from "@/components/layout/Sidebar";
import { BottomNav } from "@/components/layout/BottomNav";
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
      <body className="h-full bg-background text-foreground">
        <DataProvider>
          <ThemeProvider>
            <div className="flex h-full">
              <Sidebar />
              <main className="flex-1 overflow-y-auto px-4 py-8 pb-20 md:pb-8">
                <div className="mx-auto max-w-5xl">{children}</div>
              </main>
            </div>
            <BottomNav />
          </ThemeProvider>
        </DataProvider>
      </body>
    </html>
  );
}
