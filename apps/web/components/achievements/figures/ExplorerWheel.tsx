import type { AchievementPalettes } from "@/lib/achievements/palette"
import { WHEEL_RADII, WHEEL_SIDE, wheelModel, type SealTone, type WheelMark } from "@/lib/achievements/wheel"
import type { AchievementsDomains, AchievementsEmotions, AchievementsExplorer, ExplorerRing } from "@/lib/types"
import { FirstFiveMark } from "./FirstFiveMark"
import { FlatSeal } from "./FlatSeal"
import { Ghost } from "./Ghost"
import { HueGroup } from "./HueGroup"
import { NewMark } from "./NewMark"

type ExplorerWheelProps = {
  explorer: AchievementsExplorer
  emotions: AchievementsEmotions | null
  domains: AchievementsDomains | null
  palettes: AchievementPalettes
  /** The board's size: the wheel takes the width it is given, up to this, square. */
  size?: number
}

function paletteOf(tone: SealTone, palettes: AchievementPalettes) {
  if (tone.by === "family") return palettes.family(tone.family)
  if (tone.by === "emotion") return palettes.emotion(tone.emotionId)
  return null
}

function Mark({ mark, palettes }: { mark: WheelMark; palettes: AchievementPalettes }) {
  return (
    <>
      {mark.kind === "ghost" && <Ghost x={mark.x} y={mark.y} r={mark.r} />}
      {mark.kind === "seal" && <FlatSeal seal={mark.seal} x={mark.x} y={mark.y} width={mark.width} palette={paletteOf(mark.tone, palettes)} />}
      {mark.kind === "first-five" && <FirstFiveMark task={mark.task} x={mark.x} y={mark.y} />}
      {mark.isNew && <NewMark x={mark.x} y={mark.y} width={mark.width} />}
    </>
  )
}

/**
 * The Explorer wheel (#1308, spec D6; the boards' `wheel`): the user's first
 * pebble at the centre, then the first five, the six firsts, 25 pebbles, the
 * domains in the order they came and the emotions on the rosace's seats. What
 * each seat shows is the payload's (`wheelModel`). Decorative: its card's
 * label says what it shows. iOS `ExplorerWheel`.
 */
export function ExplorerWheel({ explorer, emotions, domains, palettes, size = WHEEL_SIDE }: ExplorerWheelProps) {
  const w = wheelModel(explorer, emotions, domains)
  const half = WHEEL_SIDE / 2
  const track = (ring: ExplorerRing) => (
    <circle r={WHEEL_RADII[ring]} fill="none" stroke={w.tracks[ring] ? "var(--spine-ink-2)" : "var(--spine-rule)"} strokeWidth={1} />
  )
  const marks = (list: WheelMark[]) => list.map((mark, i) => <Mark key={i} mark={mark} palettes={palettes} />)
  return (
    <svg
      aria-hidden
      focusable="false"
      viewBox={`${-half} ${-half} ${WHEEL_SIDE} ${WHEEL_SIDE}`}
      className="block h-auto w-full overflow-visible"
      style={{ maxWidth: size }}
    >
      {w.families.map((f, i) => {
        const palette = palettes.family(f.family)
        return (
          <HueGroup key={i} palette={palette}>
            <path d={f.arc} fill="none" stroke={palette ? "var(--spine-spot)" : "var(--spine-ink)"} strokeOpacity={0.5} strokeWidth={0.8} />
            <line {...f.tick} stroke="var(--spine-ink-2)" strokeWidth={1} strokeLinecap="round" />
          </HueGroup>
        )
      })}
      {marks(w.emotions)}
      {track("domains")}
      {marks(w.domains)}
      {track("twenty_five")}
      {marks(w.twentyFive)}
      {track("six_firsts")}
      {marks(w.sixFirsts)}
      {track("first_five")}
      {marks(w.firstFive)}
      <Mark mark={w.centre} palettes={palettes} />
    </svg>
  )
}
