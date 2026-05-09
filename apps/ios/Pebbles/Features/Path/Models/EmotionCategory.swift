import Foundation

/// One emotion category derived from `EmotionWithPalette.categoryId`.
///
/// Built by the picker sheet by deduplicating the cached
/// `EmotionPaletteService.byEmotionId` rows on `categoryId` — there is no
/// separate `emotion_categories` fetch on iOS. UI reads `localizedName`
/// (extension lives in `Emotion+Localized.swift`) for display; `name` is
/// the raw English DB column kept only as a fallback.
struct EmotionCategory: Identifiable {
    let id: UUID
    let slug: String
    let name: String
    let palette: EmotionPalette
}
