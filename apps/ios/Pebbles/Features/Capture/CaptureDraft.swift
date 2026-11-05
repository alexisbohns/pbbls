import Foundation

/// What the moment page asks (record-flow D5): when, a name, a few words. The
/// photo lives on the `SnapUploadCoordinator`, not here.
struct CaptureDraft: Equatable {
    var happenedAt = Date()
    var name = ""
    var description = ""
}
