import Foundation

/// In-progress form state for the create-pebble sheet.
/// A value type held in `@State` on `CreatePebbleSheet`.
/// Optional fields use `nil` to mean "not yet picked"; non-optionals carry sensible defaults.
struct PebbleDraft {
    var happenedAt: Date = Date()         // mandatory, "now" by default
    var name: String = ""                 // mandatory
    var description: String = ""          // optional
    var emotionId: UUID? = nil            // mandatory
    var domainId: UUID? = nil             // mandatory
    var valence: Valence? = nil           // mandatory
    var soulId: UUID? = nil               // optional
    var collectionId: UUID? = nil         // optional
    var visibility: Visibility = .private // mandatory

    /// True when every mandatory field is set. Drives the Save button's disabled state.
    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
        && emotionId != nil
        && domainId != nil
        && valence != nil
    }
}
