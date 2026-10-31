// Home (#1384) sets its italic lines in Ysabeau's italic cut (the root layout
// loads it upright only) and its seals from the Spine's sprite, mounted once
// here. The page wears the brand M3 roles as an island (spec D3).
import "@fontsource-variable/ysabeau/wght-italic.css"
import { M3Island } from "@/components/layout/M3Island"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"

export default function HomeLayout({ children }: { children: React.ReactNode }) {
  return (
    <M3Island className="pb-[calc(6rem+var(--safe-area-bottom))]">
      {children}
      <StoneSprite />
    </M3Island>
  )
}
