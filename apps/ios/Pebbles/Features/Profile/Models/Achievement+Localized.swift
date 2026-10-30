import Foundation

/// Display copy for a badge (D7): the admin's `title_*`/`description_*`
/// overrides win by the active locale, else the family-keyed catalog strings
/// with the threshold or the localized emotion/domain name interpolated.
/// Runtime-built keys use `NSLocalizedString(key:value:)` for the same reason
/// as `Emotion.localizedName` — `String(localized:)` needs a StaticString.
extension AchievementRecord {

    /// Mirrors the web resolver: `fr` picks the French override first, any
    /// other locale the English one; either falls back to its sibling before
    /// dropping to the family strings, so a half-filled row still shows copy.
    private var isFrench: Bool {
        Locale.current.language.languageCode?.identifier == "fr"
    }

    private var overrideTitle: String? {
        isFrench ? (titleFr ?? titleEn) : (titleEn ?? titleFr)
    }

    private var overrideDescription: String? {
        isFrench ? (descriptionFr ?? descriptionEn) : (descriptionEn ?? descriptionFr)
    }

    /// `emotionName`/`domainName` are the already-localized reference names
    /// (`localizedName`, never `.name`), supplied by the caller because copy
    /// resolution has no business reaching into the service graph.
    func localizedTitle(emotionName: String?, domainName: String?) -> String {
        if let overrideTitle { return overrideTitle }
        switch family {
        case "pebble_count":
            if threshold == 1 { return catalogString("achievement.family.pebble_count.title.one", fallback: "First pebble") }
            return String(format: catalogString("achievement.family.pebble_count.title.other", fallback: "%lld pebbles"), threshold ?? 0)
        case "emotion_first":
            return String(format: catalogString("achievement.family.emotion_first.title", fallback: "First %@"), emotionName ?? slug)
        case "domain_first":
            return String(format: catalogString("achievement.family.domain_first.title", fallback: "First steps in %@"), domainName ?? slug)
        case "first_collection":
            return catalogString("achievement.family.first_collection.title", fallback: "First collection")
        case "first_glyph":
            return catalogString("achievement.family.first_glyph.title", fallback: "First glyph")
        case "first_soul":
            return catalogString("achievement.family.first_soul.title", fallback: "First soul")
        case "first_moment":
            return catalogString("achievement.family.first_moment.title", fallback: "First moment")
        case "first_pebble":
            return catalogString("achievement.family.first_pebble.title", fallback: "First pebble")
        case "first_five":
            return catalogString("achievement.family.first_five.title", fallback: "The blue pebble")
        case "glyph_count":
            return String(format: catalogString("achievement.family.glyph_count.title", fallback: "%lld glyphs"), threshold ?? 0)
        case "glyph_sales":
            if threshold == 1 { return catalogString("achievement.family.glyph_sales.title.one", fallback: "First sale") }
            return String(format: catalogString("achievement.family.glyph_sales.title.other", fallback: "%lld sales"), threshold ?? 0)
        default:
            // A family added server-side before iOS catches up: degrade to the
            // slug rather than crash or show a bare key path (web parity).
            return slug
        }
    }

    func localizedDescription(emotionName: String?, domainName: String?) -> String? {
        if let overrideDescription { return overrideDescription }
        switch family {
        case "pebble_count":
            if threshold == 1 { return catalogString("achievement.family.pebble_count.description.one", fallback: "Drop your first pebble on the path.") }
            return String(format: catalogString("achievement.family.pebble_count.description.other", fallback: "Collect %lld pebbles along your path."), threshold ?? 0)
        case "emotion_first":
            return String(format: catalogString("achievement.family.emotion_first.description", fallback: "Record your first pebble carrying %@."), emotionName ?? slug)
        case "domain_first":
            return String(format: catalogString("achievement.family.domain_first.description", fallback: "Record your first pebble in %@."), domainName ?? slug)
        case "first_collection":
            return catalogString("achievement.family.first_collection.description", fallback: "Create your first collection.")
        case "first_glyph":
            return catalogString("achievement.family.first_glyph.description", fallback: "Carve your first glyph.")
        case "first_soul":
            return catalogString("achievement.family.first_soul.description", fallback: "Add your first soul.")
        case "first_moment":
            return catalogString("achievement.family.first_moment.description", fallback: "Record your first moment.")
        case "first_pebble":
            return catalogString("achievement.family.first_pebble.description", fallback: "Complete your first pebble.")
        case "first_five":
            return catalogString("achievement.family.first_five.description", fallback: "Claim it once your first five are done.")
        case "glyph_count":
            return String(format: catalogString("achievement.family.glyph_count.description", fallback: "Carve %lld glyphs of your own."), threshold ?? 0)
        case "glyph_sales":
            if threshold == 1 { return catalogString("achievement.family.glyph_sales.description.one", fallback: "Sell your first glyph in the market.") }
            return String(format: catalogString("achievement.family.glyph_sales.description.other", fallback: "Make %lld glyph sales in the market."), threshold ?? 0)
        default:
            return nil
        }
    }

    /// Group heading for the family section; unknown families show their raw
    /// value instead of a key path.
    static func localizedGroupName(family: String) -> String {
        NSLocalizedString("achievement.group.\(family)", value: family, comment: "")
    }

    /// The family's pebbles icon (spec D7). `domain_first` and `glyph_sales`
    /// wait for the chrome wave (life domain, market) and take the family default.
    var familySymbol: PbblsIcon {
        switch family {
        case "pebble_count":     .stack
        case "emotion_first":    .heart
        case "first_collection": .collection
        case "first_glyph":      .pebbleDottedGlyph
        case "first_soul":       .people
        case "first_moment":     .pebbleFlat
        case "first_pebble":     .pebbleRound
        case "first_five":       .stackLit3
        case "glyph_count":      .pebbleDottedGlyph
        default:                 .pebbleGlowMd
        }
    }

    private func catalogString(_ key: String, fallback: String) -> String {
        NSLocalizedString(key, value: fallback, comment: "")
    }
}
