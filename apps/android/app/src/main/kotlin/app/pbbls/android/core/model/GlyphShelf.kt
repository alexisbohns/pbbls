package app.pbbls.android.core.model

import kotlinx.serialization.Serializable

/**
 * The three shelves of the Glyphs screen (#1383, spec D17): the glyphs you carved, the ones you
 * own from others, and the community's. In `core/model` because a navigation key opens Glyphs on
 * one of them, and a key may not name a `features/` type. The store's tab labels stay Mine /
 * Owned / Commu.
 */
@Serializable
enum class GlyphShelf { MINE, OWNED, COMMU }
