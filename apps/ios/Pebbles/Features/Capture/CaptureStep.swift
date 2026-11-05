import Foundation

/// The Capture flow's screens (record-flow D1): the photo, then the moment
/// page. No success screen: saving returns to the origin or chains into the
/// pebble stage.
enum CaptureStep: Int, CaseIterable, Identifiable, Hashable {
    case photo
    case moment

    var id: Int { rawValue }
    var dotIndex: Int { rawValue }
    var next: CaptureStep? { CaptureStep(rawValue: rawValue + 1) }
    var previous: CaptureStep? { rawValue == 0 ? nil : CaptureStep(rawValue: rawValue - 1) }
}
