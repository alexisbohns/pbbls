// The achievements page (#1308) sets small caps, lining figures and real
// italics with the Ysabeau cuts the Path ships; the root layout loads Ysabeau
// upright only. Its seals draw from the Spine's sprite, mounted once here.
import "@fontsource-variable/ysabeau-sc"
import "@fontsource-variable/ysabeau-office"
import "@fontsource-variable/ysabeau/wght-italic.css"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"

export default function AchievementsLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      {children}
      <StoneSprite />
    </>
  )
}
