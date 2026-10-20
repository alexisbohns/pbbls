package app.pbbls.android.core.model

import kotlinx.serialization.Serializable

/** Which face of the held card is up (pebble card spec §Behaviour). */
@Serializable
enum class CardFace { RECTO, VERSO }
