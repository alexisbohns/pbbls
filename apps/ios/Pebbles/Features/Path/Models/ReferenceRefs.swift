import Foundation

// `EmotionRef` and `DomainRef` are the narrow shapes the read models embed:
// `{id, slug, name}`. Extra keys (`color`, `label`) are ignored, so the same
// type decodes `path_moments`, `v_moments_full` and PostgREST embeds. Colour
// comes from the palette service (`EmotionPaletteService.palette(for:)`).

struct EmotionRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let slug: String
    let name: String
}

struct DomainRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let slug: String
    let name: String
}
