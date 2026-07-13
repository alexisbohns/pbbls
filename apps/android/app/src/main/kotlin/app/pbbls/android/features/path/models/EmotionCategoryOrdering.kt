package app.pbbls.android.features.path.models

object EmotionCategoryOrdering {
    val byValence: Map<Pair<ValenceSizeGroup, ValencePolarity>, List<String>> =
        mapOf(
            Pair(ValenceSizeGroup.LARGE, ValencePolarity.HIGHLIGHT) to
                listOf("pride", "joy", "peace", "fear", "anger", "shame", "sadness"),
            Pair(ValenceSizeGroup.MEDIUM, ValencePolarity.HIGHLIGHT) to
                listOf("joy", "pride", "peace", "fear", "anger", "shame", "sadness"),
            Pair(ValenceSizeGroup.SMALL, ValencePolarity.HIGHLIGHT) to
                listOf("peace", "joy", "pride", "shame", "sadness", "fear", "anger"),
            Pair(ValenceSizeGroup.LARGE, ValencePolarity.NEUTRAL) to
                listOf("peace", "joy", "pride", "fear", "anger", "shame", "sadness"),
            Pair(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL) to
                listOf("peace", "fear", "joy", "anger", "pride", "shame", "sadness"),
            Pair(ValenceSizeGroup.SMALL, ValencePolarity.NEUTRAL) to
                listOf("peace", "anger", "joy", "fear", "pride", "sadness", "shame"),
            Pair(ValenceSizeGroup.LARGE, ValencePolarity.LOWLIGHT) to
                listOf("sadness", "fear", "anger", "shame", "peace", "joy", "pride"),
            Pair(ValenceSizeGroup.MEDIUM, ValencePolarity.LOWLIGHT) to
                listOf("anger", "fear", "shame", "sadness", "peace", "pride", "joy"),
            Pair(ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT) to
                listOf("shame", "sadness", "fear", "anger", "peace", "pride", "joy"),
        )

    /** Used when no valence is selected yet. Equal to MEDIUM · NEUTRAL. */
    val default: List<String> = listOf("peace", "fear", "joy", "anger", "pride", "shame", "sadness")

    fun order(valence: Valence?): List<String> {
        valence ?: return default
        return byValence[Pair(valence.sizeGroup, valence.polarity)] ?: default
    }
}
