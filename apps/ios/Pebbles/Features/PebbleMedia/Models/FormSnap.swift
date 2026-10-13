import Foundation

/// In-form representation of the (at most one) photo attached to a pebble
/// being created or edited.
///
/// - `.existing` — already saved in the DB. The form renders the thumbnail
///   from `storagePath` and exposes a remove affordance that triggers the
///   detach in `EditPebbleSheet` (no RPC): the save's whole snap set deletes
///   the row (R6).
/// - `.pending` — an in-flight or just-uploaded local pick (no DB row yet).
///   Same `AttachedSnap` shape used by `CreatePebbleSheet`.
enum FormSnap: Equatable {
    case existing(id: UUID, storagePath: String)
    case pending(AttachedSnap)
}
