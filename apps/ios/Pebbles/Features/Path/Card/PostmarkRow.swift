import SwiftUI

/// The verso's domains as one centred row of postmarks (#1146 §Verso): 72 pt,
/// or the row's share when four do not fit; the glyph from the reference
/// domains by id (none, the ring alone); the noise seeded by the domain's id,
/// so no two stamps share a pattern. VoiceOver reads one element, "Life
/// domains: Friends and Community"; the ring's repetitions are hidden.
struct PostmarkRow: View {
    let domains: [DomainRef]
    let references: [Domain]
    let ink: Color
    let paper: Color
    let rowWidth: CGFloat

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        if !domains.isEmpty {
            let diameter = CardLayout.postmarkDiameter(count: domains.count, rowWidth: rowWidth)
            HStack(spacing: CardLayout.postmarkGap) {
                ForEach(domains) { ref in
                    DomainPostmark(name: ref.localizedName,
                                   strokes: references.first { $0.id == ref.id }?.strokes,
                                   ink: ink, paper: paper, seed: UInt64(StableHash.seed(ref.id)), diameter: diameter)
                }
            }
            .frame(maxWidth: .infinity)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(CardStrings.domainsA11y)
                + Text(verbatim: ": " + domains.map(\.localizedName).formatted(.list(type: .and).locale(locale))))
        }
    }
}
