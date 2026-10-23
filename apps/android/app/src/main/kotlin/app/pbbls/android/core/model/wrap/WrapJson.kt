package app.pbbls.android.core.model.wrap

import kotlinx.serialization.json.Json

/**
 * The wraps' decoder (#1207). The payloads grow additively across clients, so unknown keys are
 * ignored and an unexpected null takes the field's default; production and the decoding tests use
 * this one instance, so the tests read the payloads exactly as the app does.
 */
val WrapJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
