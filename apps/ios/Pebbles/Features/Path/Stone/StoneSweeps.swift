import SwiftUI

/// Whether a selected stone plays its press (dip and glint) on appearance.
/// Reference renders only: the press is clocked from `.now`, so two recordings
/// of the same tree would differ by a few pixels and the re-baseline bot
/// refuses them. Production never sets it; `ValenceStoneView` reads it next to
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
