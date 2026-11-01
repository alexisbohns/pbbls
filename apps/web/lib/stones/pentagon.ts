/**
 * The avatar's squircle pentagon (#1384, spec D7), from the approved mock: a
 * 128 × 122 box, centre (64, 66), radius 64; each corner a cubic over 28 % of
 * its sides with handles at 4 %, each side bowed outward by 2 units. Computed
 * once at module load.
 */
export const PENTAGON_BOX = { width: 128, height: 122 } as const

type P = readonly [number, number]

function pentagonPath(): string {
  const cx = 64
  const cy = 66
  const radius = 64
  const t = 0.28
  const k = 0.04
  const bow = 2
  const corners: P[] = Array.from({ length: 5 }, (_, i) => {
    const a = -Math.PI / 2 + (i * 2 * Math.PI) / 5
    return [cx + radius * Math.cos(a), cy + radius * Math.sin(a)]
  })
  const lerp = (a: P, b: P, u: number): P => [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u]
  const f = (q: P) => `${q[0].toFixed(1)} ${q[1].toFixed(1)}`
  let d = ""
  for (let i = 0; i < 5; i++) {
    const prev = corners[(i + 4) % 5]
    const c = corners[i]
    const next = corners[(i + 1) % 5]
    const a = lerp(c, prev, t)
    const b = lerp(c, next, t)
    if (i === 0) d += `M${f(a)}`
    d += `C${f(lerp(c, prev, k))} ${f(lerp(c, next, k))} ${f(b)}`
    const nextA = lerp(next, c, t)
    const mid = lerp(b, nextA, 0.5)
    const out: P = [mid[0] - cx, mid[1] - cy]
    const l = Math.hypot(out[0], out[1])
    d += `Q${f([mid[0] + (out[0] / l) * bow, mid[1] + (out[1] / l) * bow])} ${f(nextA)}`
  }
  return `${d}Z`
}

export const PENTAGON_PATH = pentagonPath()
