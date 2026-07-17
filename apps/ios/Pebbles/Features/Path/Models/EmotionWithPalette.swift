import Foundation

/// A decoded row from `public.v_emotions_with_palette`.
///
/// PostgREST types every view column as nullable in `database.ts`, but the
/// underlying invariants — `emotions.category_id NOT NULL` (shipped in #367),
/// `emotions.emoji NOT NULL` (shipped in #370), and the four palette `text NOT NULL`
/// columns on `emotion_categories` — guarantee non-null values in practice. This
/// decoder enforces that invariant at the boundary: rows with any null required
/// field throw `DecodingError`, which `EmotionPaletteService` logs and skips so
/// the bad row simply isn't cached. Access sites read non-optional values from the
/// cached `EmotionPalette` and never need to handle null.
struct EmotionWithPalette: Identifiable, Decodable {
    let id: UUID
    let slug: String
    let name: String
    let emoji: String
    let categoryId: UUID
    let categorySlug: String
    let categoryName: String
    let palette: EmotionPalette

    private enum CodingKeys: String, CodingKey {
        case id, slug, name, emoji
        case categoryId    = "category_id"
        case categorySlug  = "category_slug"
        case categoryName  = "category_name"
        case primaryColor  = "primary_color"
        case secondaryColor = "secondary_color"
        case lightColor    = "light_color"
        case surfaceColor  = "surface_color"
        case shadedColor   = "shaded_color"
        case darkColor     = "dark_color"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.slug = try container.decode(String.self, forKey: .slug)
        self.name = try container.decode(String.self, forKey: .name)
        self.emoji = try container.decode(String.self, forKey: .emoji)
        self.categoryId = try container.decode(UUID.self, forKey: .categoryId)
        self.categorySlug = try container.decode(String.self, forKey: .categorySlug)
        self.categoryName = try container.decode(String.self, forKey: .categoryName)

        let primary = try container.decode(String.self, forKey: .primaryColor)
        let secondary = try container.decode(String.self, forKey: .secondaryColor)
        let light = try container.decode(String.self, forKey: .lightColor)
        let surface = try container.decode(String.self, forKey: .surfaceColor)
        let shaded = try container.decode(String.self, forKey: .shadedColor)
        let dark = try container.decode(String.self, forKey: .darkColor)

        guard let palette = EmotionPalette(
            primaryHex: primary,
            secondaryHex: secondary,
            lightHex: light,
            surfaceHex: surface,
            shadedHex: shaded,
            darkHex: dark
        ) else {
            throw DecodingError.dataCorruptedError(
                forKey: .primaryColor,
                in: container,
                debugDescription: "Palette hex strings failed to parse"
            )
        }
        self.palette = palette
    }
}
