import Foundation

/// How far the phone leans from its reference pose, per axis, in −1…1. The
/// unit every stone effect reads; ported from the Femfolk native card POC.
struct Lean: Equatable {
    var x: Double
    var y: Double
    static let zero = Lean(x: 0, y: 0)

    /// True when either axis has moved by at least `eps`: the dead band the
    /// motion source publishes through, so a still phone stops redrawing.
    func moved(from other: Lean, atLeast eps: Double) -> Bool {
        abs(x - other.x) >= eps || abs(y - other.y) >= eps
    }
}
