import CoreText
import Foundation
import os
import UIKit

/// The OpenType features a Ysabeau rank may ask for. The tag strings are the
/// ones Android's `SpineTypography` passes, so both surfaces shape alike.
enum YsabeauFeature: Hashable, CaseIterable {
    /// `SC`: small caps from lower case and from capitals.
    case smallCaps
    /// `LIN`: lining, proportional figures.
    case lining
    /// `TAB`: lining, tabular figures.
    case tabular

    var tags: [String] {
        switch self {
        case .smallCaps: return ["smcp", "c2sc"]
        case .lining:    return ["lnum", "pnum"]
        case .tabular:   return ["lnum", "tnum"]
        }
    }
}

/// The variable Ysabeau at an exact axis value (#1158).
///
/// Both bundled files (`Ysabeau.ttf`, `Ysabeau Italic.ttf`, byte-identical to
/// Android's, checked by `ios.yml`) are variable fonts with one `wght` axis,
/// 1 to 1000, default 700. `UIFont(name:)` only ever resolves that default
/// instance (`Ysabeau-Bold`, `Ysabeau-BoldItalic`), so the weight is set
/// through `kCTFontVariationAttribute`, the path Caveat Bold already ships on.
/// Features go in as OpenType tags.
///
/// The italic has no Lower Case feature, so it has no `smcp`: never pair
/// `italic` with `.smallCaps` (`SpineTypeTests` holds the rank table to that).
///
/// New pattern (raised in the B1 PR): one memoised `UIFont` per
/// (face, size, weight, features), bridged to SwiftUI as `Font(UIFont)`.
enum YsabeauFace {
    static let romanName = "Ysabeau-Bold"
    static let italicName = "Ysabeau-BoldItalic"
    /// `'wght'` as a four-char code (`kCTFontVariationAxisIdentifierKey`).
    static let weightAxis = 0x77_67_68_74

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "fonts")
    private static let lock = NSLock()
    private static let cache = NSCache<NSString, UIFont>()
    private static var loggedMissing: Set<String> = []

    private static let variationAttribute =
        UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String)
    private static let featureTagKey = UIFontDescriptor.FeatureKey(rawValue: kCTFontOpenTypeFeatureTag as String)
    private static let featureValueKey = UIFontDescriptor.FeatureKey(rawValue: kCTFontOpenTypeFeatureValue as String)

    /// One `UIFont` at an exact axis value. Memoised by (size, weight, italic, features).
    static func uiFont(size: CGFloat, weight: Int, italic: Bool, features: Set<YsabeauFeature>) -> UIFont {
        uiFont(size: size, weight: weight, italic: italic, features: features,
               faceName: italic ? italicName : romanName)
    }

    /// `faceName` is injectable so a test can prove the missing-face fallback.
    static func uiFont(size: CGFloat, weight: Int, italic: Bool, features: Set<YsabeauFeature>,
                       faceName: String) -> UIFont {
        let tags = featureTags(features)
        let key = "\(faceName)|\(size)|\(weight)|\(italic)|\(tags.joined(separator: ","))" as NSString
        lock.lock()
        defer { lock.unlock() }
        if let cached = cache.object(forKey: key) { return cached }
        let font = build(size: size, weight: weight, italic: italic, tags: tags, faceName: faceName)
        cache.setObject(font, forKey: key)
        return font
    }

    /// The de-duplicated, sorted tag list for a feature set.
    static func featureTags(_ features: Set<YsabeauFeature>) -> [String] {
        Array(Set(features.flatMap(\.tags))).sorted()
    }

    /// The `UIFont.Weight` nearest an axis value, for the system fallback.
    static func nearestSystemWeight(_ weight: Int) -> UIFont.Weight {
        let table: [(Int, UIFont.Weight)] = [
            (100, .ultraLight), (200, .thin), (300, .light), (400, .regular), (500, .medium),
            (600, .semibold), (700, .bold), (800, .heavy), (900, .black)
        ]
        return table.min { abs($0.0 - weight) < abs($1.0 - weight) }?.1 ?? .regular
    }

    // Called under `lock`.
    private static func build(size: CGFloat, weight: Int, italic: Bool, tags: [String], faceName: String) -> UIFont {
        guard let base = UIFont(name: faceName, size: size) else {
            if loggedMissing.insert(faceName).inserted {
                logger.error("\(faceName, privacy: .public) failed to load; falling back to the system font")
            }
            let system = UIFont.systemFont(ofSize: size, weight: nearestSystemWeight(weight))
            guard italic, let descriptor = system.fontDescriptor.withSymbolicTraits(.traitItalic) else { return system }
            return UIFont(descriptor: descriptor, size: size)
        }
        var attributes: [UIFontDescriptor.AttributeName: Any] = [variationAttribute: [weightAxis: weight]]
        if !tags.isEmpty {
            attributes[.featureSettings] = tags.map { [featureTagKey: $0, featureValueKey: 1] }
        }
        return UIFont(descriptor: base.fontDescriptor.addingAttributes(attributes), size: size)
    }
}
