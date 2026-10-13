import Foundation

/// A moment's name is optional since the split (spec D3: a name *or* a photo).
/// One rule for every surface that shows or sends it.
enum MomentName {
    /// The name to display or send, or nil when there is none worth showing.
    static func display(_ raw: String?) -> String? {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty else { return nil }
        return trimmed
    }
}
