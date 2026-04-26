import Foundation

/// One photo attached to an in-progress pebble, including upload state.
/// Held inside `PebbleDraft.attachedSnap`. Value type — immutable updates.
struct AttachedSnap: Equatable {

    enum UploadState: Equatable {
        case uploading
        case uploaded
        case failed
    }

    /// UUID generated client-side. Becomes both the Storage folder name and
    /// `snaps.id` in Postgres.
    let id: UUID

    /// JPEG bytes for the 420 px thumbnail, kept in memory so the form can
    /// render an instant preview without a Storage round-trip.
    let localThumb: Data

    var state: UploadState

    /// Storage folder shared by both files: `{user_id}/{id}`. Lowercase
    /// because Postgres' `auth.uid()::text` is lowercase, and the bucket
    /// RLS policy compares the first folder segment to it as text.
    func storagePrefix(userId: UUID) -> String {
        "\(userId.uuidString.lowercased())/\(id.uuidString.lowercased())"
    }

    /// Full Storage path of the 1024 px JPEG.
    func originalPath(userId: UUID) -> String {
        "\(storagePrefix(userId: userId))/original.jpg"
    }

    /// Full Storage path of the 420 px JPEG.
    func thumbPath(userId: UUID) -> String {
        "\(storagePrefix(userId: userId))/thumb.jpg"
    }
}
