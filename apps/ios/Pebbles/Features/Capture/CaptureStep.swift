import Foundation

/// The Capture flow's screens (spec §6, A1). No success screen: saving
/// returns to the Path, or chains into Complete.
enum CaptureStep: Int, CaseIterable, Identifiable, Hashable {
    case photo
    case when
    case name

    var id: Int { rawValue }
    var isOptional: Bool { self == .photo }
    var dotIndex: Int { rawValue }
    var next: CaptureStep? { CaptureStep(rawValue: rawValue + 1) }
    var previous: CaptureStep? { rawValue == 0 ? nil : CaptureStep(rawValue: rawValue - 1) }
}
