import UIKit

/// The variable Inclusive Sans at an exact `wght` (#1382 D1, D2). Both bundled
/// files carry one axis, 300 to 700, default 400; `UIFont(name:)` resolves only
/// the default instance, so the weight goes through the variation attribute, as
/// for Ysabeau, whose memoised builder this calls with Inclusive's names.
/// No OpenType feature is asked: figures are always Ysabeau (spec D22).
enum InclusiveFace {
    static let romanName = "InclusiveSans-Regular"
    static let italicName = "InclusiveSans-Italic"

    static func uiFont(size: CGFloat, weight: Int, italic: Bool) -> UIFont {
        YsabeauFace.uiFont(size: size, weight: weight, italic: italic, features: [],
                           faceName: italic ? italicName : romanName)
    }
}
