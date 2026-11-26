import SwiftUI

/// Where the stone reveal ceremony's two landmarks are on the success step
/// (#1691): the centre of the stage, where the hero stone is held, and the
/// week row's marked cell, where it lands. Each publishes its bounds as an
/// anchor so the ceremony can resolve both in one coordinate space.
///
/// The first anchor published wins: a landmark is drawn once.
struct StoneCeremonyCentreKey: PreferenceKey {
    static let defaultValue: Anchor<CGRect>? = nil

    static func reduce(value: inout Anchor<CGRect>?, nextValue: () -> Anchor<CGRect>?) {
        value = value ?? nextValue()
    }
}

struct StoneCeremonyCellKey: PreferenceKey {
    static let defaultValue: Anchor<CGRect>? = nil

    static func reduce(value: inout Anchor<CGRect>?, nextValue: () -> Anchor<CGRect>?) {
        value = value ?? nextValue()
    }
}
