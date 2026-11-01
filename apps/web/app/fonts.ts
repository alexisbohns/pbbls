import localFont from "next/font/local"

// Inclusive Sans from the maintainer's files (#1384, spec D5): the bytes iOS bundles.
// Not preloaded: only Home, You and /sandbox/design set it, and a @font-face is
// fetched when a glyph first needs it.
export const inclusiveSans = localFont({
  src: [
    { path: "./fonts/inclusive-sans/InclusiveSans-VariableFont_wght.ttf", weight: "300 700", style: "normal" },
    { path: "./fonts/inclusive-sans/InclusiveSans-Italic-VariableFont_wght.ttf", weight: "300 700", style: "italic" },
  ],
  variable: "--font-inclusive-sans",
  display: "swap",
  preload: false,
  fallback: ["system-ui", "sans-serif"],
  adjustFontFallback: "Arial",
})
