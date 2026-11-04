package app.pbbls.android.features.onboarding

/** How a step can be passed without answering (#1510 D1). */
enum class OnboardingSkip { NONE, SKIP, LATER }

/** One step of the flow. The screen branches on [id] to pick its content; copy lives in `strings.xml`. */
data class OnboardingStep(
    val id: String,
    val skip: OnboardingSkip,
)

/**
 * The seven steps (#1510 D1), in order. A config so M69 can insert a reminders step after `pace`
 * without rework; `OnboardingStepsTest` pins count, ids, order and skips. `handoff` has no screen
 * of its own (D9): it is the `complete_onboarding` call and Home.
 */
object OnboardingSteps {
    val all: List<OnboardingStep> =
        listOf(
            OnboardingStep("promise", OnboardingSkip.NONE),
            OnboardingStep("consent", OnboardingSkip.NONE),
            OnboardingStep("reasons", OnboardingSkip.SKIP),
            OnboardingStep("you", OnboardingSkip.SKIP),
            OnboardingStep("people", OnboardingSkip.LATER),
            OnboardingStep("pace", OnboardingSkip.SKIP),
            OnboardingStep("handoff", OnboardingSkip.NONE),
        )

    /** The steps with a screen: every one but the hand-off. The progress row counts these. */
    val visible: List<OnboardingStep> = all.filter { it.id != "handoff" }
}
