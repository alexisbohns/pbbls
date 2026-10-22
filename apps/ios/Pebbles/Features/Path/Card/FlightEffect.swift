import SwiftUI

/// The card's FLIP (#1160 D1): `scaleEffect` about its top-leading corner and
/// an `offset`, from the slot's rect to the card's resting one, animatable on
/// `progress`. Layout never moves: the card lays out at rest, and a reporter
/// placed outside this effect reads that rest. Without both rects, nothing.
struct FlightEffect: ViewModifier, Animatable {
    var progress: Double
    let slot: CGRect?
    let rest: CGRect?

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let transform = slot.flatMap { slot in
            rest.map { FlightGeometry.transform(from: slot, to: $0, progress: progress) }
        } ?? (scale: 1, offset: .zero)
        content
            .scaleEffect(transform.scale, anchor: .topLeading)
            .offset(transform.offset)
    }
}
