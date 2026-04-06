import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono, Ysabeau } from "next/font/google";
import { ThemeProvider } from "@/components/layout/ThemeProvider";
import { ColorWorldProvider } from "@/components/layout/ColorWorldProvider";
import { ThemeColorSync } from "@/components/layout/ThemeColorSync";
import { DataProvider } from "@/components/layout/DataProvider";
import { AuthProvider } from "@/components/layout/AuthProvider";
import { SerwistRegistration } from "@/components/layout/SerwistRegistration";
import { MainContent } from "@/components/layout/MainContent";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

const ysabeau = Ysabeau({
  variable: "--font-ysabeau",
  subsets: ["latin"],
});

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#F8F0F0" },
    { media: "(prefers-color-scheme: dark)", color: "#2B1F21" },
  ],
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
    startupImage: [
      { url: "/splash/750x1334-light.png", media: "screen and (device-width: 375px) and (device-height: 667px) and (-webkit-device-pixel-ratio: 2) and (prefers-color-scheme: light)" },
      { url: "/splash/750x1334-dark.png", media: "screen and (device-width: 375px) and (device-height: 667px) and (-webkit-device-pixel-ratio: 2) and (prefers-color-scheme: dark)" },
      { url: "/splash/1242x2208-light.png", media: "screen and (device-width: 414px) and (device-height: 736px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: light)" },
      { url: "/splash/1242x2208-dark.png", media: "screen and (device-width: 414px) and (device-height: 736px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: dark)" },
      { url: "/splash/1125x2436-light.png", media: "screen and (device-width: 375px) and (device-height: 812px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: light)" },
      { url: "/splash/1125x2436-dark.png", media: "screen and (device-width: 375px) and (device-height: 812px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: dark)" },
      { url: "/splash/828x1792-light.png", media: "screen and (device-width: 414px) and (device-height: 896px) and (-webkit-device-pixel-ratio: 2) and (prefers-color-scheme: light)" },
      { url: "/splash/828x1792-dark.png", media: "screen and (device-width: 414px) and (device-height: 896px) and (-webkit-device-pixel-ratio: 2) and (prefers-color-scheme: dark)" },
      { url: "/splash/1242x2688-light.png", media: "screen and (device-width: 414px) and (device-height: 896px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: light)" },
      { url: "/splash/1242x2688-dark.png", media: "screen and (device-width: 414px) and (device-height: 896px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: dark)" },
      { url: "/splash/1080x2340-light.png", media: "screen and (device-width: 360px) and (device-height: 780px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: light)" },
      { url: "/splash/1080x2340-dark.png", media: "screen and (device-width: 360px) and (device-height: 780px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: dark)" },
      { url: "/splash/1170x2532-light.png", media: "screen and (device-width: 390px) and (device-height: 844px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: light)" },
      { url: "/splash/1170x2532-dark.png", media: "screen and (device-width: 390px) and (device-height: 844px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: dark)" },
      { url: "/splash/1284x2778-light.png", media: "screen and (device-width: 428px) and (device-height: 926px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: light)" },
      { url: "/splash/1284x2778-dark.png", media: "screen and (device-width: 428px) and (device-height: 926px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: dark)" },
      { url: "/splash/1179x2556-light.png", media: "screen and (device-width: 393px) and (device-height: 852px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: light)" },
      { url: "/splash/1179x2556-dark.png", media: "screen and (device-width: 393px) and (device-height: 852px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: dark)" },
      { url: "/splash/1290x2796-light.png", media: "screen and (device-width: 430px) and (device-height: 932px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: light)" },
      { url: "/splash/1290x2796-dark.png", media: "screen and (device-width: 430px) and (device-height: 932px) and (-webkit-device-pixel-ratio: 3) and (prefers-color-scheme: dark)" },
    ],
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
      className={`${geistSans.variable} ${geistMono.variable} ${ysabeau.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        {/* Runs before hydration to apply the saved color-world class and
            update theme-color meta tags — prevents flash of wrong theme.
            Color map must stay in sync with lib/config/color-worlds.ts */}
        <script
          dangerouslySetInnerHTML={{
            __html: `try{var w=localStorage.getItem("pbbls-color-world");if(w&&w!=="blush-quartz"){document.documentElement.classList.add(w);var m={"stoic-rock":{l:"#FFFFFF",d:"#252525"},"cave-pigment":{l:"#F5F0E8",d:"#2B2518"},"dusk-stone":{l:"#F0EEF0",d:"#211F2B"},"moss-pool":{l:"#EFF5F2",d:"#192B22"}};var c=m[w];if(c){var ml=document.querySelector('meta[name="theme-color"][media*="light"]');var md=document.querySelector('meta[name="theme-color"][media*="dark"]');if(ml)ml.setAttribute("content",c.l);if(md)md.setAttribute("content",c.d)}}}catch(e){}`,
          }}
        />
      </head>
      <body className="bg-background text-foreground">
        <SerwistRegistration>
          <DataProvider>
            <AuthProvider>
            <ColorWorldProvider>
              <ThemeProvider>
                <ThemeColorSync />
                <div className="flex h-full pl-[var(--safe-area-left)] pr-[var(--safe-area-right)]">
                  <MainContent>{children}</MainContent>
                </div>
              </ThemeProvider>
            </ColorWorldProvider>
            </AuthProvider>
          </DataProvider>
        </SerwistRegistration>
      </body>
    </html>
  );
}
