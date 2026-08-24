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
    /// Handwritten (Reenie Beanie) name label, 22pt. Any user's name — soul,
    /// creator — renders in the hand font. See issue #515.
    case bodyLeadHand
    /// Handwritten (Reenie Beanie) large title, 41pt. Profile display name.
    case largeTitleHand
    /// Handwritten (Caveat) input face, 36pt. The pebble name field in the
    /// record flow — the one place the user writes on a bare page.
    case nameInputHand
    /// Handwritten (Caveat Bold) headline word naming the picked valence.
    /// Three sizes, one per `ValenceSizeGroup`, so the word grows with the
    /// event the way the stones do.
    case valenceWordSmall
    case valenceWordMedium
    case valenceWordLarge
}

// MARK: - Ink overhang

extension PebblesFont {
    /// Horizontal breathing room a token needs so its glyphs are not clipped
    /// at the text's layout width.
    ///
    /// Caveat Bold's terminal `t` flicks to the right past the glyph's advance,
    /// and `Text` is clipped to the advance — so "Moment" and "Lowlight" lose
    /// the end of their last letter to a hard vertical cut.
    ///
    /// Measured with CoreText (`CTLineGetImageBounds` against
    /// `CTLineGetTypographicBounds`): the ink runs 3–8pt past the advance on
    /// the right at 34–56pt, and never past the ascent, so the cut is only ever
    /// horizontal. These values round that up.
    ///
    /// **Padding alone does not fix it.** A frame is not what the glyphs are
    /// clipped to, so callers pad the *string* as well — see
    /// `PebblesFont.needsInkPadding`. This value is the belt to that's braces.
    ///
    /// Applied by the caller rather than by `pebblesFont`, which stays a pure
    /// type modifier (font + tracking + case) and never touches layout.
    var inkOverhang: CGFloat {
        switch self {
        case .valenceWordSmall:  return 6
        case .valenceWordMedium: return 8
        case .valenceWordLarge:  return 10
        default:                 return 0
        }
    }

    /// True for the faces whose ink escapes their advance. Callers put a space
    /// on each side of the string: a space carries real advance width, so the
    /// room travels with the text no matter what measures its bounds, and
    /// padding both sides keeps the word centred.
    var needsInkPadding: Bool { inkOverhang > 0 }
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
        case .bodyLeadHand:          return .reenieBeanie(22)
        case .largeTitleHand:        return .reenieBeanie(41)
        case .nameInputHand:         return .caveat(36)
        case .valenceWordSmall:      return .caveatBold(34)
        case .valenceWordMedium:     return .caveatBold(44)
        case .valenceWordLarge:      return .caveatBold(56)
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
        // Reenie Beanie sits loose by default; these tighten it toward
        // connected handwriting (issue #515 speced -2%, dialed in on review).
        case .bodyLeadHand:                                       return -1.0   // 22pt
        case .largeTitleHand:                                     return -2.0   // 41pt
        // Caveat is already tightly connected; no extra tracking.
        case .nameInputHand:                                      return 0
        case .valenceWordSmall, .valenceWordMedium, .valenceWordLarge: return 0
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

    /// Reenie Beanie — handwritten display face used for names (souls,
    /// creators, profile). Bundled TTF (`Resources/ReenieBeanie-Regular.ttf`,
    /// registered in Info.plist `UIAppFonts`). Falls back to the system font
    /// if the face is missing from the build.
    fileprivate static func reenieBeanie(_ size: CGFloat) -> Font {
        if let custom = UIFont(name: "ReenieBeanie", size: size) {
            return Font(custom)
        }
        return Font(UIFont.systemFont(ofSize: size))
    }

    /// Caveat — handwritten face used for the pebble name input in the record
    /// flow. Bundled variable TTF (`Resources/Caveat-VariableFont_wght.ttf`,
    /// registered in Info.plist `UIAppFonts`); `UIFont(name:)` resolves the
    /// default instance (weight 400). Falls back to the system font if the
    /// face is missing from the build.
    fileprivate static func caveat(_ size: CGFloat) -> Font {
        if let custom = UIFont(name: "Caveat-Regular", size: size) {
            return Font(custom)
        }
        return Font(UIFont.systemFont(ofSize: size))
    }

    /// Caveat at weight 700. `UIFont(name:)` only ever resolves a variable
    /// font's default instance, so the weight axis has to be set explicitly —
    /// `Caveat-Bold` is not a resolvable name for this file. Falls back to the
    /// regular instance, then to the system font.
    fileprivate static func caveatBold(_ size: CGFloat) -> Font {
        // 'wght' as a four-char code, the identifier CoreText wants for the
        // variation axis (see kCTFontVariationAxisIdentifierKey).
        let weightAxis = 0x77_67_68_74
        guard let base = UIFont(name: "Caveat-Regular", size: size) else {
            return Font(UIFont.systemFont(ofSize: size, weight: .bold))
        }
        let descriptor = base.fontDescriptor.addingAttributes([
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [
                weightAxis: 700
            ]
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
