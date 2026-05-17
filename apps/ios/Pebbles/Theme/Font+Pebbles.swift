import SwiftUI
import UIKit

// MARK: - Token catalog

/// Typography tokens used across Pebbles iOS. Apply via `View.pebblesFont(_:)`
/// so that font + tracking + textCase are bundled at the call site.
enum PebblesFont {
    case body
    case bodyEmphasized
    case subhead
    case subheadEmphasized
    case headline
    case headlineEmphasized
    case callout
    case calloutEmphasized
    case meta
    case metaEmphasized
    case cardHeading
    case cardHeadingEmphasized
    case counterLg
    case captionEmphasized
    case title
    case buttonLabel
}

// MARK: - View modifier

extension View {
    /// Apply a Pebbles typography token: sets `.font`, `.tracking`, and
    /// `.textCase` together so callers cannot forget one half of the pair
    /// (e.g. uppercase + letter-spacing on meta).
    func pebblesFont(_ token: PebblesFont) -> some View {
        modifier(PebblesFontModifier(token: token))
    }
}

private struct PebblesFontModifier: ViewModifier {
    let token: PebblesFont

    func body(content: Content) -> some View {
        content
            .font(token.font)
            .tracking(token.tracking)
            .textCase(token.isUppercase ? .uppercase : nil)
    }
}

// MARK: - Token → font / tracking / case mapping

private extension PebblesFont {
    var font: Font {
        switch self {
        case .body:                  return .sfProRounded(17, .regular)
        case .bodyEmphasized:        return .sfProRounded(17, .semibold)
        case .subhead:               return .sfProRounded(15, .regular)
        case .subheadEmphasized:     return .sfProRounded(15, .semibold)
        case .headline:              return .sfProRounded(17, .semibold)
        case .headlineEmphasized:    return .sfProRounded(17, .bold)
        case .callout:               return .sfProRounded(16, .medium)
        case .calloutEmphasized:     return .sfProRounded(16, .semibold)
        case .meta:                  return .sfCompactRounded(12, .medium)
        case .metaEmphasized:        return .sfCompactRounded(12, .bold)
        case .cardHeading:           return .sfCompactRounded(15, .semibold)
        case .cardHeadingEmphasized: return .sfCompactRounded(15, .bold)
        case .counterLg:             return .sfProRounded(17, .semibold)
        case .captionEmphasized:     return .sfProRounded(12, .semibold)
        case .title:                 return .ysabeauSemibold(28)
        case .buttonLabel:           return .ysabeauSemibold(17)
        }
    }

    /// Tracking in points (the spec is in % of font size; converted here).
    var tracking: CGFloat {
        switch self {
        case .body, .bodyEmphasized, .headline, .headlineEmphasized,
             .buttonLabel, .counterLg:                            return 0.34   // 2% of 17
        case .subhead, .subheadEmphasized:                        return 0.30   // 2% of 15
        case .callout, .calloutEmphasized:                        return 0.32   // 2% of 16
        case .meta, .metaEmphasized:                              return 1.20   // 10% of 12
        case .cardHeading, .cardHeadingEmphasized:                return 1.50   // 10% of 15
        case .captionEmphasized:                                  return 0.24   // 2% of 12
        case .title:                                              return -0.56  // -2% of 28
        }
    }

    var isUppercase: Bool {
        switch self {
        case .meta, .metaEmphasized, .cardHeading, .cardHeadingEmphasized:
            return true
        default:
            return false
        }
    }
}

// MARK: - Family helpers

extension Font {
    /// Ysabeau-SemiBold with OpenType proportional + lining figures
    /// (numbers align to cap height, proportional widths). Used everywhere
    /// Ysabeau renders mixed text + numbers so digits look right.
    ///
    /// Feature constants from `CoreText/SFNTLayoutTypes.h`:
    ///   - Number Spacing (type 6) → Proportional Numbers (selector 1)
    ///   - Number Case  (type 21) → Upper Case Numbers / lining (selector 1)
    static func ysabeauSemibold(_ size: CGFloat) -> Font {
        let descriptor = UIFontDescriptor(name: "Ysabeau-SemiBold", size: size)
            .addingAttributes([
                .featureSettings: [
                    [UIFontDescriptor.FeatureKey.type: 6,  UIFontDescriptor.FeatureKey.selector: 1],
                    [UIFontDescriptor.FeatureKey.type: 21, UIFontDescriptor.FeatureKey.selector: 1],
                ],
            ])
        return Font(UIFont(descriptor: descriptor, size: size))
    }

    /// SF Pro Rounded — system rounded design.
    fileprivate static func sfProRounded(_ size: CGFloat, _ weight: UIFont.Weight) -> Font {
        let base = UIFont.systemFont(ofSize: size, weight: weight)
        if let descriptor = base.fontDescriptor.withDesign(.rounded) {
            return Font(UIFont(descriptor: descriptor, size: size))
        }
        return Font(base)
    }

    /// SF Compact Rounded — bundled OTFs (see Resources/Fonts/).
    /// Falls back to SF Pro Rounded if the named font is missing (e.g. the
    /// OTFs were not bundled in a given build).
    fileprivate static func sfCompactRounded(_ size: CGFloat, _ weight: UIFont.Weight) -> Font {
        let name: String
        switch weight {
        case .medium:   name = "SFCompactRounded-Medium"
        case .semibold: name = "SFCompactRounded-Semibold"
        case .bold:     name = "SFCompactRounded-Bold"
        default:        name = "SFCompactRounded-Medium"
        }
        if let custom = UIFont(name: name, size: size) {
            return Font(custom)
        }
        return sfProRounded(size, weight)
    }
}
