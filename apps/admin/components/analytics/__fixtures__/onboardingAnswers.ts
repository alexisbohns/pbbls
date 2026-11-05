import type { OnboardingAnswerRow } from "@/lib/analytics/types"

export const onboardingAnswersDense: OnboardingAnswerRow[] = [
  { kind: "completed", value: "no", users: 14 },
  { kind: "completed", value: "yes", users: 86 },
  { kind: "pace", value: "daily", users: 21 },
  { kind: "pace", value: "few-a-week", users: 30 },
  { kind: "pace", value: "weekly-wrap", users: 19 },
  { kind: "pace", value: "when-it-matters", users: 10 },
  { kind: "reason", value: "good-days", users: 40 },
  { kind: "reason", value: "hard-season", users: 25 },
  { kind: "reason", value: "keep-people", users: 61 },
  { kind: "reason", value: "keep-record", users: 18 },
  { kind: "reason", value: "notice-feelings", users: 70 },
  { kind: "reason", value: "own-patterns", users: 33 },
]
export const onboardingAnswersSparse: OnboardingAnswerRow[] = [
  { kind: "completed", value: "yes", users: 2 },
  { kind: "reason", value: "keep-people", users: 1 },
]
export const onboardingAnswersEmpty: OnboardingAnswerRow[] = []
