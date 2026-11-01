import SwiftUI
import UIKit

// MARK: - Token catalog

/// Typography tokens used across Pebbles iOS. Apply via `View.pebblesFont(_:)`
/// so that font + tracking + textCase are bundled at the call site.
enum PebblesFont: CaseIterable {
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
    case captionEmphasized
    case title
    case buttonLabel
    /// Handwritten (Reenie Beanie) name label, 22pt. Any user's name — soul,
    /// creator — renders in the hand font. See issue #515.
    case bodyLeadHand
    /// Handwritten (Reenie Beanie) large title, 60pt. You's name (#1382 D17).
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

    @ViewBuilder
    func body(content: Content) -> some View {
        if let style = token.inclusiveStyle {
            // Inclusive Sans ranks scale with Dynamic Type and thicken under Bold Text (#1382 D1).
            content.spineType(style)
        } else {
            content
                .font(token.font)
                .tracking(token.tracking)
        }
    }
}

// MARK: - The Inclusive Sans tokens

extension PebblesFont {
    /// The token's face at `dynamicTypeSize`: the Inclusive ranks through the
    /// Spine resolver, the others at their fixed size. What `FontTokensTests` reads.
    func resolvedUIFont(dynamicTypeSize: DynamicTypeSize = .large) -> UIFont {
        guard let style = inclusiveStyle else { return fixedUIFont }
        return SpineType.resolve(style, dynamicTypeSize: dynamicTypeSize, legibilityWeight: nil).uiFont
    }

    /// The Ysabeau and handwritten tokens, which keep a fixed size; the
    /// Inclusive ones go through `inclusiveStyle` (never read for them).
    var fixedUIFont: UIFont {
        switch self {
        case .title:             YsabeauFace.uiFont(size: 28, weight: 600, italic: false, features: [.lining])
        case .bodyLeadHand:      UIFont.bundled("ReenieBeanie", size: 22)
        case .largeTitleHand:    UIFont.bundled("ReenieBeanie", size: 60)
        case .nameInputHand:     UIFont.bundled("Caveat-Regular", size: 36)
        case .valenceWordSmall:  UIFont.pebblesCaveat(size: 34, weight: 700)
        case .valenceWordMedium: UIFont.pebblesCaveat(size: 44, weight: 700)
        case .valenceWordLarge:  UIFont.pebblesCaveat(size: 56, weight: 700)
        case .buttonLabel:       YsabeauFace.uiFont(size: 17, weight: 600, italic: false, features: [.lining])
        // The Inclusive tokens resolve through `inclusiveStyle` and never read this.
        default:                 YsabeauFace.uiFont(size: 17, weight: 600, italic: false, features: [.lining])
        }
    }

    /// The Inclusive Sans tokens (spec D3) as Spine ranks: scaled by the resolver,
    /// thickened by Bold Text. Nil for the Ysabeau and handwritten tokens.
    var inclusiveStyle: SpineType.Style? {
        switch self {
        case .body:                  .inclusive(17, 400)
        case .bodyEmphasized:        .inclusive(17, 600)
        case .subhead:               .inclusive(15, 400)
        case .subheadEmphasized:     .inclusive(15, 600)
        case .headline:              .inclusive(17, 600)
        case .headlineEmphasized:    .inclusive(17, 700)
        case .callout:               .inclusive(16, 500)
        case .calloutEmphasized:     .inclusive(16, 600)
        case .meta:                  .inclusive(12, 500, caps: true)
        case .metaEmphasized:        .inclusive(12, 700, caps: true)
        case .cardHeading:           .inclusive(15, 600, caps: true)
        case .cardHeadingEmphasized: .inclusive(15, 700, caps: true)
        case .captionEmphasized:     .inclusive(12, 600)
        default:                     nil
        }
    }
}

extension SpineType.Style {
    /// An Inclusive Sans rank at `size` / `weight`, scaling with its size band;
    /// capitals carry 0.08 em.
    static func inclusive(_ size: CGFloat, _ weight: Int, caps: Bool = false) -> Self {
        Self(size: size, weight: weight, trackingEm: caps ? 0.08 : 0,
             scaling: .text(SpineType.textStyle(forSize: size)), face: .inclusive, isUppercase: caps)
    }
}

// MARK: - Token → font / tracking / case mapping

private extension PebblesFont {
    var font: Font { Font(fixedUIFont) }

    /// Tracking in points (the spec is in % of font size; converted here).
    var tracking: CGFloat {
        switch self {
        case .buttonLabel:                                        return 0.34   // 2% of 17
        case .title:                                              return -0.56  // -2% of 28
        // Reenie Beanie sits loose by default; these tighten it toward
        // connected handwriting (issue #515 speced -2%, dialed in on review).
        case .bodyLeadHand:                                       return -1.0   // 22pt
        case .largeTitleHand:                                     return -4.5   // 60pt, -0.075 em
        // Caveat is already tightly connected; no extra tracking.
        default:                                                  return 0
        }
    }
}

// MARK: - Family helpers

extension Font {
    /// Ysabeau at weight 600 with lining, proportional figures (`lnum, pnum`),
    /// so digits align to the cap height. The variable roman since #1158
    /// (`YsabeauFace`); the old static SemiBold cut is gone.
    static func ysabeauSemibold(_ size: CGFloat) -> Font {
        Font(YsabeauFace.uiFont(size: size, weight: 600, italic: false, features: [.lining]))
    }

    /// Ysabeau's true italic at weight 600, beside `ysabeauSemibold` at the
    /// same colour and with the same figures.
    static func ysabeauSemiboldItalic(_ size: CGFloat) -> Font {
        Font(YsabeauFace.uiFont(size: size, weight: 600, italic: true, features: [.lining]))
    }
}

extension UIFont {
    /// A bundled face by PostScript name: Reenie Beanie (names: souls, creators,
    /// profile) and Caveat (the record flow's name input), both registered in
    /// Info.plist `UIAppFonts`. `UIFont(name:)` resolves a variable file's default
    /// instance only (Caveat's 400). Falls back to the system font if the face is
    /// missing from the build.
    static func bundled(_ name: String, size: CGFloat) -> UIFont {
        UIFont(name: name, size: size) ?? UIFont.systemFont(ofSize: size)
    }

    /// Caveat at an exact `wght`. `UIFont(name:)` only resolves the variable
    /// file's default instance (400), so the axis is set explicitly; `Caveat-Bold`
    /// is not a resolvable name. One descriptor for `PebblesFont.valenceWord*`
    /// and `SpineType.Style.lockupWord`. Falls back to the bold system font.
    /// Memoised by (size, weight), like `YsabeauFace`, so a render never
    /// rebuilds the descriptor.
    static func pebblesCaveat(size: CGFloat, weight: Int) -> UIFont {
        let key = "\(size)|\(weight)" as NSString
        if let cached = caveatCache.object(forKey: key) { return cached }
        let font: UIFont
        if let base = UIFont(name: "Caveat-Regular", size: size) {
            let descriptor = base.fontDescriptor.addingAttributes([
                UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [
                    UIFontDescriptor.weightAxis: weight
                ]
            ])
            font = UIFont(descriptor: descriptor, size: size)
        } else {
            font = UIFont.systemFont(ofSize: size, weight: .bold)
        }
        caveatCache.setObject(font, forKey: key)
        return font
    }

    /// `NSCache` is thread-safe, so no lock is needed around it.
    private static let caveatCache = NSCache<NSString, UIFont>()
}
