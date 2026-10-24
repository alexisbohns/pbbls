import Foundation

/// The emotion families as the wraps see them (#1226). A family is an
/// `EmotionCategory`: the wraps name it by slug, and its name and palette come
/// from `v_emotions_with_palette`, whose rows carry their category's columns.
enum EmotionFamilies {
    /// The gem's clockwise order (the method's wheel): the bright families
    /// first, the hard ones below.
    static let wheel = ["joy", "pride", "anger", "fear", "shame", "sadness", "peace"]

    /// One family per category slug, its name and palette from its first
    /// emotion, in `wheel` order, then by slug for any family the wheel does not
    /// list yet.
    static func of(_ rows: some Sequence<EmotionWithPalette>) -> [EmotionCategory] {
        var seen: Set<String> = []
        let families = rows.compactMap { row -> EmotionCategory? in
            guard seen.insert(row.categorySlug).inserted else { return nil }
            return EmotionCategory(id: row.categoryId, slug: row.categorySlug, name: row.categoryName,
                                   palette: row.palette)
        }
        return families.sorted { lhs, rhs in
            let left = wheel.firstIndex(of: lhs.slug) ?? wheel.count
            let right = wheel.firstIndex(of: rhs.slug) ?? wheel.count
            return left != right ? left < right : lhs.slug < rhs.slug
        }
    }
}
