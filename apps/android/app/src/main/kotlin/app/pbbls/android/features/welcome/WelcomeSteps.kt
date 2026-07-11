package app.pbbls.android.features.welcome

import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * Single welcome-carousel slide's content — the `WelcomeStep` analog. No image
 * field: the Pebbles logo in `WelcomeScreen`'s header plays that role. Copy lives
 * in `strings.xml`, so titles/descriptions are string-resource ids.
 */
data class WelcomeStep(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
)

/**
 * The three slides on the pre-login welcome carousel. Editing copy or reordering
 * is a single-file change — `WelcomeCarousel` reads `.all` opaquely and
 * `WelcomeStepsTest` enforces count, unique ids, and id order.
 */
object WelcomeSteps {
    val all: List<WelcomeStep> =
        listOf(
            WelcomeStep("record", R.string.welcome_step_record_title, R.string.welcome_step_record_description),
            WelcomeStep("enrich", R.string.welcome_step_enrich_title, R.string.welcome_step_enrich_description),
            WelcomeStep("grow", R.string.welcome_step_grow_title, R.string.welcome_step_grow_description),
        )
}
