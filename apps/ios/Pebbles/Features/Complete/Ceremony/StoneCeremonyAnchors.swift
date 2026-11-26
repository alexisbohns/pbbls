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

/// Both landmarks together, so one overlay can resolve them: each key is re-published into this
/// one by an overlay of its own (`View.stoneCeremonyLandmarks`).
struct StoneCeremonyLandmarks {
    var centre: Anchor<CGRect>?
    var cell: Anchor<CGRect>?
}

struct StoneCeremonyLandmarksKey: PreferenceKey {
    static let defaultValue = StoneCeremonyLandmarks()

    static func reduce(value: inout StoneCeremonyLandmarks, nextValue: () -> StoneCeremonyLandmarks) {
        let next = nextValue()
        value.centre = value.centre ?? next.centre
        value.cell = value.cell ?? next.cell
    }
}

extension View {
    /// Draws `content` over this view with the centre and the cell published inside it.
    func stoneCeremonyLandmarks<Content: View>(
        @ViewBuilder _ content: @escaping (StoneCeremonyLandmarks) -> Content
    ) -> some View {
        overlayPreferenceValue(StoneCeremonyCentreKey.self) { centre in
            Color.clear.preference(key: StoneCeremonyLandmarksKey.self, value: StoneCeremonyLandmarks(centre: centre))
        }
        .overlayPreferenceValue(StoneCeremonyCellKey.self) { cell in
            Color.clear.preference(key: StoneCeremonyLandmarksKey.self, value: StoneCeremonyLandmarks(cell: cell))
        }
        .overlayPreferenceValue(StoneCeremonyLandmarksKey.self, content)
    }
}
