import type { Metadata } from "next";
import Script from "next/script";
import { Geist, Geist_Mono } from "next/font/google";
import { ThemeProvider } from "@/components/layout/ThemeProvider";
import { ColorWorldProvider } from "@/components/layout/ColorWorldProvider";
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
        <Script
          id="color-world-init"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{
            __html: `try{var w=localStorage.getItem("pbbls-color-world");if(w&&w!=="blush-quartz"){document.documentElement.classList.add(w)}}catch(e){}`,
          }}
        />
        <DataProvider>
          <ColorWorldProvider>
            <ThemeProvider>
              <div className="flex h-full">
                <Sidebar />
                <main className="min-w-0 flex-1 overflow-x-hidden overflow-y-auto px-4 py-8 pb-20 md:pb-8">
                  <div className="mx-auto max-w-5xl">{children}</div>
                </main>
              </div>
              <BottomNav />
            </ThemeProvider>
          </ColorWorldProvider>
        </DataProvider>
      </body>
    </html>
  );
}
