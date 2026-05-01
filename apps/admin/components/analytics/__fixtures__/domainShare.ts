import type {
  DomainShareMover,
  DomainShareSnapshot,
} from "../DomainShare"

const DOMAIN_BASE: {
  slug: string
  name: string
  label: string
  level: number
  weight: number
}[] = [
  { slug: "zoe", name: "Zoē", label: "Health & body", level: 1, weight: 24 },
  {
    slug: "asphaleia",
    name: "Asphaleia",
    label: "Security & comfort",
    level: 2,
    weight: 18,
  },
  { slug: "philia", name: "Philía", label: "Relationships", level: 3, weight: 32 },
  {
    slug: "time",
    name: "Timē",
    label: "Recognition & community",
    level: 4,
    weight: 14,
  },
  {
    slug: "eudaimonia",
    name: "Eudaimonia",
    label: "Self-actualization",
    level: 5,
    weight: 22,
  },
]

export const denseDomainShareSnapshot: DomainShareSnapshot[] = (() => {
  const total = 1820
  return DOMAIN_BASE.map((d) => {
    const share = d.weight // already in 0..100ish range; pebbles can have multiple domains
    const pebbles = Math.round((share / 100) * total)
    return {
      domain_id: d.slug,
      domain_slug: d.slug,
      domain_name: d.name,
      domain_label: d.label,
      domain_level: d.level,
      pebbles_in_domain: pebbles,
      share_pct: round2(share),
    }
  }).sort((a, b) => b.share_pct - a.share_pct)
})()

export const denseDomainShareTopMover: DomainShareMover = {
  domain_id: "philia",
  domain_name: "Philía",
  current_pct: 32,
  previous_pct: 25,
  delta_pp: 7,
}

export const denseDomainShareBottomMover: DomainShareMover = {
  domain_id: "asphaleia",
  domain_name: "Asphaleia",
  current_pct: 18,
  previous_pct: 24,
  delta_pp: -6,
}

export const denseDomainShareTotalPebbles = 1820

export const sparseDomainShareSnapshot: DomainShareSnapshot[] = (() => {
  const total = 30
  return DOMAIN_BASE.slice(0, 3).map((d, i) => ({
    domain_id: d.slug,
    domain_slug: d.slug,
    domain_name: d.name,
    domain_label: d.label,
    domain_level: d.level,
    pebbles_in_domain: 12 - i * 3,
    share_pct: round2(((12 - i * 3) / total) * 100),
  })).sort((a, b) => b.share_pct - a.share_pct)
})()

export const sparseDomainShareTotalPebbles = 30

export const emptyDomainShareSnapshot: DomainShareSnapshot[] = []
export const emptyDomainShareTotalPebbles = 0

function round2(n: number): number {
  return Math.round(n * 100) / 100
}
