import SwiftUI

/// One month on Home: a small card. Revealed, it shows the month's garland
/// drawn small with the count inside; folded, a turned corner and the word
/// "folded" where the wheel would be. Decorative parts are hidden from
/// VoiceOver; the card says the month, its count and its state in one label.
struct HomeWrapCard: View {
    let month: HomeMonth
    let lookups: WrapLookups

    static let side: CGFloat = 128
    private static let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(spacing: 4) {
            if month.isFolded {
                folded
            } else {
                // The garland's own frame is 262 pt: scale it down, then give the container the small frame.
                Garland(wrap: month.asMonthWrap, cover: month.cover, lookups: lookups) {
                    Text(verbatim: "\(month.count)").spineType(.dayNumeral)
                }
                .scaleEffect(92 / 262)
                .frame(width: 92, height: 92)
                .accessibilityHidden(true)
            }
            VStack(spacing: 0) {
                Text(verbatim: WrapCopy.monthName(month.month, locale: locale))
                    .spineType(.personName)
                    .foregroundStyle(Color.pebbles.onSurface)
                Text(verbatim: String(month.month.year))
                    .spineType(.kicker)
                    .foregroundStyle(Color.pebbles.outline)
            }
            .lineLimit(1)
            .minimumScaleFactor(0.7)
        }
        .frame(width: Self.side, height: Self.side)
        .background(Color.pebbles.surfaceContainer, in: Self.shape)
        .overlay(alignment: .topTrailing) {
            if month.isFolded {
                Path { p in
                    p.move(to: .zero)
                    p.addLine(to: CGPoint(x: 18, y: 0))
                    p.addLine(to: CGPoint(x: 18, y: 18))
                    p.closeSubpath()
                }
                .fill(Color.pebbles.surfaceContainerHighest)
                .frame(width: 18, height: 18)
                .accessibilityHidden(true)
            }
        }
        .clipShape(Self.shape)
        .overlay(Self.shape.strokeBorder(Color.pebbles.cardRule, lineWidth: 0.5))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: label))
    }

    private var folded: some View {
        VStack(spacing: 8) {
            Text("home.wrap.folded")
                .spineType(.sectionLabel)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            Rectangle().fill(Color.pebbles.cardRule).frame(width: 56, height: 0.5)
        }
        .frame(width: 92, height: 92)
        .accessibilityHidden(true)
    }

    private var label: String {
        let name = "\(WrapCopy.monthName(month.month, locale: locale)) \(month.month.year)"
        return SpineStrings.format(month.isFolded ? "home.wrap.a11y.folded" : "home.wrap.a11y.open",
                                   locale: locale, name, month.count)
    }
}
