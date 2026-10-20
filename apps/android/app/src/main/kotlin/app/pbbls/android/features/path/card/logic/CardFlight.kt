package app.pbbls.android.features.path.card.logic

import app.pbbls.android.core.model.CardFace

/**
 * When a card flies between its slot on the Spine and the held card (#1135, pebble card
 * spec §Behaviour): the predicates both ends of `sharedCardBounds` enable it on, so the
 * Spine, the held card and the loading page agree on one flight.
 */
internal object CardFlight {
    /**
     * The Spine's card takes part only when the detail will hold a card: a bare moment
     * with a photo has a card on the Spine but opens as the bare page, with nothing to
     * land on.
     */
    fun spineCardFlies(
        hasPhotoCard: Boolean,
        hasPebble: Boolean,
    ): Boolean = hasPhotoCard && hasPebble

    /**
     * The held card flies, on the open and on the close, only while its recto is the face
     * in front ([showing], the committed face): a card turned to its verso fades, one turned
     * to its recto flies home. Never while it prints (it draws itself at an offset inside the
     * page), nor once its moment is deleted ([leaving]), whose slot has gone from the Path.
     */
    fun heldCardFlies(
        showing: CardFace,
        printing: Boolean,
        leaving: Boolean,
    ): Boolean = showing == CardFace.RECTO && !printing && !leaving

    /**
     * The moment the loading page holds a blank card for: only an open asking for the recto
     * flies in, the card's press on the Spine. The stone's (verso) opens in place, on the
     * spinner alone.
     */
    fun placeholderFor(
        momentId: String,
        requested: CardFace,
    ): String? = momentId.takeIf { requested == CardFace.RECTO }
}
