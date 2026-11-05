import SwiftUI

/// Whether a stone sweeps its light on appearance. Reference renders only:
/// the sweep is clocked from `.now`, so two recordings of the same tree would
/// differ by a few pixels and the re-baseline bot refuses them. Production
/// never sets it; `PebbleStoneView` and `ValenceStoneView` read it next to
/// Reduce Motion.
private struct StoneSweepsKey: EnvironmentKey {
    static let defaultValue = true
}

extension EnvironmentValues {
    var stoneSweeps: Bool {
        get { self[StoneSweepsKey.self] }
        set { self[StoneSweepsKey.self] = newValue }
    }
}
