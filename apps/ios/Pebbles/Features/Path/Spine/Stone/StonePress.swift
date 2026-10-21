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
/// (R14) and the stone's timeline pauses. A newer press keeps its own lifetime:
/// its new `seq` cancels this task, and the guard leaves it alone.
private struct StonePressLifetime: ViewModifier {
    @Binding var press: StonePress?

    func body(content: Content) -> some View {
        content.task(id: press?.seq) {
            guard let current = press else { return }
            // Cancellation is the next press taking over, not a failure.
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
