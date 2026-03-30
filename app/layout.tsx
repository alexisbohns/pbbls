import type { Metadata, Viewport } from "next";
import Script from "next/script";
import { Geist, Geist_Mono } from "next/font/google";
import { ThemeProvider } from "@/components/layout/ThemeProvider";
import { ColorWorldProvider } from "@/components/layout/ColorWorldProvider";
import { ThemeColorSync } from "@/components/layout/ThemeColorSync";
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

export const viewport: Viewport = {
  themeColor: "#F8F0F0",
  viewportFit: "cover",
};

export const metadata: Metadata = {
  title: "pbbls",
  description: "Collect meaningful moments, one pebble at a time",
  manifest: "/manifest.webmanifest",
  icons: {
    icon: [
      { url: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
      { url: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
    ],
    apple: [
      { url: "/icons/apple-touch-icon.png", sizes: "180x180", type: "image/png" },
    ],
  },
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "pbbls",
  },
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
              <ThemeColorSync />
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
