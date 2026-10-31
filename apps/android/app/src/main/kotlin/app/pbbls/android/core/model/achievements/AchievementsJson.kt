package app.pbbls.android.core.model.achievements

import kotlinx.serialization.json.Json

/**
 * The achievements decoder (#1307), production and tests alike, so the tests read the server's
 * payloads exactly as the app does.
 *
 * Unknown keys are ignored because the payload grows additively across clients. An explicit null
 * takes the field's default: the server omits absent keys (spec D17), but a client must survive a
 * null all the same (the standing tolerance rule).
 */
val AchievementsJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
