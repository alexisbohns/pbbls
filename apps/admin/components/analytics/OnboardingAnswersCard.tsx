import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getOnboardingAnswers } from "@/lib/analytics/fetchers"
import type { OnboardingAnswerRow } from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"
import { OnboardingAnswers } from "./OnboardingAnswers"

export async function OnboardingAnswersCard() {
  let rows: OnboardingAnswerRow[]
  try {
    rows = await getOnboardingAnswers()
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load onboarding answers"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Onboarding answers</CardTitle>
      </CardHeader>
      <CardContent>
        <OnboardingAnswers rows={rows} />
      </CardContent>
    </Card>
  )
}
