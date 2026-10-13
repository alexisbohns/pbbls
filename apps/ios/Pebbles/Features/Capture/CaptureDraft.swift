import Foundation

/// What Capture asks (spec D3): a date, plus a name or a photo. The photo
/// lives on the `SnapUploadCoordinator`, not here.
struct CaptureDraft: Equatable {
    var happenedAt = Date()
    var name = ""
}
