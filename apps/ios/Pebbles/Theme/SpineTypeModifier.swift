import SwiftUI
import UIKit

extension SpineType {
    /// A rank resolved for one environment.
    struct Resolved {
        let size: CGFloat
        let weight: Int
        let tracking: CGFloat
        /// Added between lines; negative when the rank asks for less than the
        /// font's natural line height.
        let lineSpacing: CGFloat
        let uiFont: UIFont
    }

    /// Text ranks stop growing here (≈ 194 % at body size), the iOS twin of
    /// Android's font-scale 2.0 ceiling.
    static let dynamicTypeCeiling: DynamicTypeSize = .accessibility2

    /// Pure, so tests pin Dynamic Type and Bold Text without a view.
    static func resolve(_ style: Style, dynamicTypeSize: DynamicTypeSize, legibilityWeight: LegibilityWeight?) -> Resolved {
        let traits = UITraitCollection(
            preferredContentSizeCategory: contentSizeCategory(min(dynamicTypeSize, dynamicTypeCeiling))
        )
        let size: CGFloat
        switch style.scaling {
        case .text(let textStyle):
            size = UIFontMetrics(forTextStyle: uiTextStyle(textStyle)).scaledValue(for: style.size, compatibleWith: traits)
        case .card(let cap):
            size = style.size * min(cap, UIFontMetrics(forTextStyle: .body).scaledValue(for: 1, compatibleWith: traits))
        case .none:
            size = style.size
        }
        // A custom face gets no Bold Text from the system, and the Spine is text-heavy.
        let weight = legibilityWeight == .bold ? min(style.weight + 100, 800) : style.weight
        let uiFont: UIFont
        switch style.face {
        case .ysabeau:
            uiFont = YsabeauFace.uiFont(size: size, weight: weight, italic: style.italic, features: style.features)
        case .caveat:
            uiFont = UIFont.pebblesCaveat(size: size, weight: weight)
        }
        let factor = style.size > 0 ? size / style.size : 1
        let lineSpacing = style.lineHeight.map { $0 * factor - uiFont.lineHeight } ?? 0
        return Resolved(size: size, weight: weight, tracking: style.trackingEm * size, lineSpacing: lineSpacing, uiFont: uiFont)
    }

    static func contentSizeCategory(_ size: DynamicTypeSize) -> UIContentSizeCategory {
        switch size {
        case .xSmall:         return .extraSmall
        case .small:          return .small
        case .medium:         return .medium
        case .large:          return .large
        case .xLarge:         return .extraLarge
        case .xxLarge:        return .extraExtraLarge
        case .xxxLarge:       return .extraExtraExtraLarge
        case .accessibility1: return .accessibilityMedium
        case .accessibility2: return .accessibilityLarge
        case .accessibility3: return .accessibilityExtraLarge
        case .accessibility4: return .accessibilityExtraExtraLarge
        case .accessibility5: return .accessibilityExtraExtraExtraLarge
        @unknown default:     return .large
        }
    }

    static func uiTextStyle(_ style: Font.TextStyle) -> UIFont.TextStyle {
        switch style {
        case .largeTitle:  return .largeTitle
        case .title:       return .title1
        case .title2:      return .title2
        case .title3:      return .title3
        case .headline:    return .headline
        case .subheadline: return .subheadline
        case .body:        return .body
        case .callout:     return .callout
        case .footnote:    return .footnote
        case .caption:     return .caption1
        case .caption2:    return .caption2
        default:           return .body
        }
    }
}

extension View {
    /// Apply a Spine rank: font, tracking, line spacing and case, resolved from
    /// the SwiftUI environment (not `UITraitCollection.current`), so it reacts to
    /// Dynamic Type and a test can pin it with `.environment(\.dynamicTypeSize, …)`.
    /// Never add `.bold()` or `.fontWeight()` after it.
    func spineType(_ style: SpineType.Style) -> some View {
        modifier(SpineTypeModifier(style: style))
    }
}

private struct SpineTypeModifier: ViewModifier {
    let style: SpineType.Style
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight

    func body(content: Content) -> some View {
        let resolved = SpineType.resolve(style, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
        content
            .font(Font(resolved.uiFont))
            .tracking(resolved.tracking)
            .lineSpacing(resolved.lineSpacing)
            .textCase(style.isLowercase ? .lowercase : nil)
    }
}
