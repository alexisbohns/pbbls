import SwiftUI

/// One month on Home (#1382 D13): a card. Revealed, it shows the month's
/// garland with the count inside; folded, a turned corner and the word
/// "folded" where the wheel would be; then the month and the year. Decorative
/// parts are hidden from VoiceOver; the card says the month, its count and its
/// state in one label.
struct HomeWrapCard: View {
    let month: HomeMonth
    let lookups: WrapLookups

    static let width: CGFloat = 152
    /// The garland's side on the card (its own frame is 262 pt).
    static let wheel: CGFloat = 116
    /// A card's height at the default text size (18 + 116 + 14 + month + year + 18),
    /// which the dashed promise matches.
    static let promiseHeight: CGFloat = 212
    @Environment(\.dynamicTypeSize) private var typeSize
    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Group {
                if month.isFolded {
                    folded
                } else {
                    // The garland is scaled down; the count is drawn at its own size on top.
                    ZStack {
                        Garland(wrap: month.asMonthWrap, cover: month.cover, lookups: lookups) { Color.clear }
                            .scaleEffect(Self.wheel / 262)
                            .frame(width: Self.wheel, height: Self.wheel)
                        Text(verbatim: "\(month.count)")
                            .spineType(.wrapCount)
                            .foregroundStyle(Color.pebbles.onSurface)
                            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    }
                    .accessibilityHidden(true)
                }
            }
            .padding(.bottom, 14)
            Text(verbatim: WrapCopy.monthName(month.month, locale: locale))
                .spineType(.wrapMonth)
                .foregroundStyle(Color.pebbles.onSurface)
            if !typeSize.isAccessibilitySize {
                Text(verbatim: String(month.month.year))
                    .spineType(.wrapYear)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .padding(.top, 2)
            }
        }
        .lineLimit(typeSize.isAccessibilitySize ? 2 : 1)
        .minimumScaleFactor(0.7)
        .padding(EdgeInsets(top: 18, leading: 18, bottom: 18, trailing: 16))
        .frame(width: Self.width, alignment: .leading)
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
        .pebblesCard()
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
        .frame(width: Self.wheel, height: Self.wheel)
        .accessibilityHidden(true)
    }

    private var label: String {
        let name = "\(WrapCopy.monthName(month.month, locale: locale)) \(month.month.year)"
        return SpineStrings.format(month.isFolded ? "home.wrap.a11y.folded" : "home.wrap.a11y.open",
                                   locale: locale, name, month.count)
    }
}
