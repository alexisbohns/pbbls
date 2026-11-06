import Foundation

/// The debounced device-local autosave of the unsaved moment page (record-flow
/// D2), flushed on background. Disabled until the restore has been attempted,
/// and again after a discard or a successful save: both clear the file, and a
/// late `onChange` (the coordinator nils its snap before its own await) must
/// not write the discarded page back.
@MainActor
final class CaptureDraftAutosaver {
    private let store: CaptureDraftStore
    private let debounce: Duration
    private let ownerId: () -> UUID?
    private var task: Task<Void, Never>?
    private var pending: CaptureDraftSnapshot.Saved?
    /// The account the pending edit was made under, captured at `schedule()`.
    private var pendingOwner: UUID?

    var enabled = false

    init(store: CaptureDraftStore, debounce: Duration = .milliseconds(800), ownerId: @escaping () -> UUID?) {
        self.store = store
        self.debounce = debounce
        self.ownerId = ownerId
    }

    /// The snap reference worth keeping across a kill: one that already lives
    /// in Storage. A pick still uploading is re-picked after a restore.
    static func savedSnap(from snap: FormSnap?, userId: UUID) -> CaptureDraftSnapshot.Snap? {
        switch snap {
        case .existing(let id, let path):
            return .init(id: id, storagePath: path)
        case .pending(let attached) where attached.state == .uploaded:
            return .init(id: attached.id, storagePath: attached.storagePrefix(userId: userId))
        default:
            return nil
        }
    }

    func schedule(_ saved: CaptureDraftSnapshot.Saved) {
        guard enabled else { return }
        pending = saved
        pendingOwner = ownerId()
        task?.cancel()
        // Holds `self` strongly on purpose: the debounce keeps the autosaver
        // alive until it fires, so a cover dismissed mid-debounce still
        // writes its last edit.
        task = Task {
            try? await Task.sleep(for: self.debounce)
            guard !Task.isCancelled else { return }
            self.flush()
        }
    }

    func flush() {
        task?.cancel()
        task = nil
        guard enabled, let saved = pending else { return }
        let owner = pendingOwner
        pending = nil
        pendingOwner = nil
        // An edit made under one account never lands in another's file.
        guard let owner, ownerId() == owner else { return }
        store.save(saved, ownerId: owner)
    }

    /// Stops the autosave for good (until re-enabled) and deletes the file.
    func clear() {
        enabled = false
        task?.cancel()
        task = nil
        pending = nil
        pendingOwner = nil
        if let owner = ownerId() { store.clear(ownerId: owner) }
    }
}
