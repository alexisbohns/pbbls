package app.pbbls.android.features.profile.components

/**
 * Splits the 28-slot assiduity array into grid rows of [columns], padding the
 * last row with `false` so the grid stays rectangular — ports the
 * `chunkAssiduity` helper from iOS `AssiduityGrid.swift` (the grid itself
 * lands with the Profile stats card, sub-project C).
 */
fun chunkAssiduity(
    data: List<Boolean>,
    columns: Int,
): List<List<Boolean>> {
    if (data.isEmpty() || columns <= 0) return emptyList()
    return data.chunked(columns).map { row ->
        if (row.size < columns) row + List(columns - row.size) { false } else row
    }
}
