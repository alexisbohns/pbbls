import Foundation

/// What a wrap's ids point at (#1226; Android: `WrapLookups`): the payloads
/// name moments, families, emotions and domains by id or slug, and the wraps
/// resolve them here. `PathView` builds it from the feed and the reference
/// services; references and tests build it from fixtures. A nil is something
/// the device does not know yet, and its row is skipped rather than shown wrong.
/// Souls resolve through the page's own names and glyphs.
struct WrapLookups {
    var moment: (String) -> MomentSummary? = { _ in nil }
    var family: (String) -> EmotionCategory? = { _ in nil }
    var domain: (String) -> Domain? = { _ in nil }
    var emotion: (String) -> EmotionWithPalette? = { _ in nil }
    var palette: (MomentSummary) -> EmotionPalette? = { _ in nil }

    /// A family's name in the app's language, falling back to its slug.
    func familyName(_ slug: String) -> String {
        family(slug)?.localizedName ?? slug
    }

    /// Each domain's name, in the payload's order, skipping unknown ids.
    func domainNames(_ stats: [DomainStat]) -> [String] {
        stats.compactMap { domain($0.domainId)?.localizedName }
    }

    /// The moments an invitation names: the week's first three, by name.
    func inviteNames(_ wrap: WeekWrap) -> [String] {
        (wrap.inviteMomentIds ?? []).compactMap { id in
            moment(id)?.name.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
        }
    }
}
