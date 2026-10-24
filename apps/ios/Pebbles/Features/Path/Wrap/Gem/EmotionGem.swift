import SwiftUI

/// The month's emotions as a gem (#1226, D10): the lead sentence over the
/// server's lead, the stone coloured by the server's facet allocation, each
/// family named at its run with its share, and a *more* to the fine emotions
/// in the month sheet. Android: `EmotionGem`.
struct EmotionGem: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let onMore: () -> Void

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        if let gem = wrap.gem {
            let words = GemWords(gem: gem, lookups: lookups, locale: locale)
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(label: WrapCopy.tile(.emotions, locale: locale),
                                 hint: WrapCopy.gemHint(locale: locale))
                SheetLead(words.lead)
                    .padding(.horizontal, MonthLayout.sectionPadX)
                GemFigure(facets: gem.facets, palette: { lookups.family($0)?.palette }, labels: words.labels,
                          count: "\(wrap.pebbles)", countLabel: WrapCopy.gemPebbles(wrap.pebbles, locale: locale),
                          description: words.description)
                MoreLink(text: WrapCopy.moreEmotions(wrap.fineEmotions?.count ?? 0, locale: locale), action: onMore)
                    .accessibilityHint(Text(verbatim: WrapCopy.tileHint(locale: locale)))
                    .padding(.horizontal, MonthLayout.sectionPadX)
            }
        }
    }
}

/// What the gem says (Android: `EmotionGem`'s locals), pure for the tests: the
/// lead over the server's order and zeros, a label per family in that order,
/// and the sentence VoiceOver reads.
struct GemWords: Equatable {
    let lead: String
    let labels: [GemLabel]
    let description: String

    init(gem: Gem, lookups: WrapLookups, locale: Locale) {
        let order = gem.lead?.order ?? []
        let names = order.map { lookups.familyName($0).lowercased(with: locale) }
        let zero = (gem.lead?.zero ?? []).map { lookups.familyName($0).lowercased(with: locale) }
        let level = order.count > 1 && abs((gem.worths[order[0]] ?? 0) - (gem.worths[order[1]] ?? 0)) < 1e-9
        lead = WrapCopy.gemLead(order: names, zero: zero, level: level, locale: locale)
        labels = zip(order, names).map { slug, name in
            GemLabel(slug: slug, name: name, percent: Int(((gem.shares[slug] ?? 0) * 100).rounded()))
        }
        description = WrapCopy.gemDescription(labels.map { ($0.name, $0.percent) }, locale: locale)
    }
}
