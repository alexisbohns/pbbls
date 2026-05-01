import type { BounceDistributionStats } from "../BounceDistribution"
import type { BounceDistributionDatum } from "../BounceDistributionChart"

export type BounceDistributionFixture = {
  buckets: BounceDistributionDatum[]
  totalUsers: number
  stats: BounceDistributionStats
}

const LABELS = ["0", "1-10", "11-25", "26-50", "51-100", "100+"] as const

function buckets(values: readonly number[]): BounceDistributionDatum[] {
  return LABELS.map((label, i) => ({ bucket_label: label, users: values[i] ?? 0 }))
}

export const denseBounceDistributionFixture: BounceDistributionFixture = {
  buckets: buckets([12, 38, 47, 26, 14, 5]),
  totalUsers: 12 + 38 + 47 + 26 + 14 + 5,
  stats: {
    medianScore: 19,
    pctMaintaining: 71.4,
    avgActiveDaysPerWeek: 2.8,
  },
}

export const sparseBounceDistributionFixture: BounceDistributionFixture = {
  buckets: buckets([3, 4, 1, 0, 0, 0]),
  totalUsers: 3 + 4 + 1,
  stats: {
    medianScore: 4,
    pctMaintaining: 50,
    avgActiveDaysPerWeek: 1.25,
  },
}

export const emptyBounceDistributionFixture: BounceDistributionFixture = {
  buckets: buckets([0, 0, 0, 0, 0, 0]),
  totalUsers: 0,
  stats: {
    medianScore: null,
    pctMaintaining: null,
    avgActiveDaysPerWeek: null,
  },
}
