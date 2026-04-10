export type OnboardingStepConfig = {
  id: string
  heading: string
  body: string
}

export const ONBOARDING_STEPS: OnboardingStepConfig[] = [
  {
    id: "path",
    heading: "Your life is a path",
    body: "Every moment matters \u2014 the big ones and the quiet ones. But most of them slip away before you even notice. Pebbles helps you collect them, one by one.",
  },
  {
    id: "pebble",
    heading: "Drop a pebble, keep the moment",
    body: "A coffee with a friend. A concert that gave you chills. A tough conversation. Record it in seconds \u2014 no blank page, no pressure, no audience.",
  },
  {
    id: "ritual",
    heading: "Build your path, at your own pace",
    body: "No streak to protect, no feed to scroll. Just a calm ritual that grows with you.",
  },
]
