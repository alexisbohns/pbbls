import SwiftUI

/// A live press on one stone (#1130 §Press-request state): when it began, and
/// whether it is the reduced-motion touch. A value: a view holds it as `@State`
/// and the stone's timeline reads it; `seq` tells two presses apart.
struct StonePress: Equatable {
    let seq: Int
    let start: Date
    let still: Bool

    static func next(after current: StonePress?, still: Bool, now: Date) -> StonePress {
        StonePress(seq: (current?.seq ?? 0) + 1, start: now, still: still)
    }

    func elapsed(at date: Date) -> Double {
        date.timeIntervalSince(start)
    }
}

/// Clears a press once its last element has played, so the ripple unmounts
/// (R14) and the stone's timeline pauses. The task is cancelled in two ways:
/// a newer press changes `seq`, so the guard leaves that press its own
/// lifetime; and the stone leaving the tree cancels it with `seq` unchanged,
/// so the press is cleared at once rather than left half-played.
private struct StonePressLifetime: ViewModifier {
    @Binding var press: StonePress?

    func body(content: Content) -> some View {
        content.task(id: press?.seq) {
            guard let current = press else { return }
            // Cancellation is a newer press or the stone leaving, not a failure.
            try? await Task.sleep(for: .seconds(StoneMotion.total(still: current.still)))
            if press?.seq == current.seq { press = nil }
        }
    }
}

extension View {
    func stonePressLifetime(_ press: Binding<StonePress?>) -> some View {
        modifier(StonePressLifetime(press: press))
    }
}
