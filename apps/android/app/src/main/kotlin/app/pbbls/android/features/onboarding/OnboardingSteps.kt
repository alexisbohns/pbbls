package app.pbbls.android.features.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * Source of an onboarding illustration — the `OnboardingImage` analog. The iOS
 * asset-catalog illustrations are not yet exported to Android drawable densities
 * (milestone risk 6 — needs the maintainer's design sources), so every step ships
 * [Placeholder] for now; swapping to [Asset] later is a data-only change.
 */
sealed interface OnboardingImage {
    data class Asset(
        @DrawableRes val resId: Int,
    ) : OnboardingImage

    data object Placeholder : OnboardingImage
}

/**
 * Single onboarding screen's content — the `OnboardingStep` analog. The view
 * never branches on [id]; copy lives in `strings.xml`.
 */
data class OnboardingStep(
    val id: String,
    val image: OnboardingImage,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
)

/**
 * The four onboarding steps shown to new users on sign-up. Editing copy or
 * reordering is a single-file change — `OnboardingScreen` reads `.all` opaquely
 * and `OnboardingStepsTest` enforces count, unique ids, and id order.
 */
object OnboardingSteps {
    val all: List<OnboardingStep> =
        listOf(
            OnboardingStep(
                id = "intro",
                image = OnboardingImage.Placeholder,
                titleRes = R.string.onboarding_intro_title,
                descriptionRes = R.string.onboarding_intro_description,
            ),
            OnboardingStep(
                id = "concept",
                image = OnboardingImage.Placeholder,
                titleRes = R.string.onboarding_concept_title,
                descriptionRes = R.string.onboarding_concept_description,
            ),
            OnboardingStep(
                id = "qualify",
                image = OnboardingImage.Placeholder,
                titleRes = R.string.onboarding_qualify_title,
                descriptionRes = R.string.onboarding_qualify_description,
            ),
            OnboardingStep(
                id = "carving",
                image = OnboardingImage.Placeholder,
                titleRes = R.string.onboarding_carving_title,
                descriptionRes = R.string.onboarding_carving_description,
            ),
        )
}
