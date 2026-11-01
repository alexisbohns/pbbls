// The Spine (#1052) sets small caps and lining figures with the real Ysabeau
// cuts. They load only where the Path is drawn, not app-wide.
import "@fontsource-variable/ysabeau-sc"
import "@fontsource-variable/ysabeau-office"
// The root layout loads Ysabeau upright only; without the italic face the
// browser slants the upright glyphs instead of setting the real italic.
import "@fontsource-variable/ysabeau/wght-italic.css"
import { M3Island } from "@/components/layout/M3Island"

// The Path is a tab on the M3 ground (#1384): the island makes the Spine's
// paper the M3 `surface` (globals.css, "The Spine on Material"), rendered on
// the server so the ground never flashes. It fills <main> like the Path did.
export default function PathLayout({ children }: { children: React.ReactNode }) {
  return <M3Island className="h-full">{children}</M3Island>
}
