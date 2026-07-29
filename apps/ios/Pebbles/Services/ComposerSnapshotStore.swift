import Foundation
import Observation
import os

/// Crash insurance for the open composer (M47) — a single JSON snapshot of the
/// in-progress draft on disk.
///
/// This is the first on-disk persistence in the app: `SnapURLCache` is
/// per-session and the only `@AppStorage` key is a boolean. Deliberately
/// **not** offline support — see the 2026-07-29 "Offline is a non-goal on every
/// surface" decision-log entry. No merge logic, no cross-device sync, one
/// composer at a time, cleared on publish or server-draft save.
///
/// Media is excluded (D3): an intentional "save as draft" earns durable media,
/// an accidental crash recovery does not.
///
/// Encoding/decoding is pure and injectable so the debounce and round-trip are
/// testable without touching the real caches directory.
@Observable
@MainActor
final class ComposerSnapshotStore {

    private let fileURL: URL
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "composer-snapshot")

    /// Production initializer: a file in the caches directory. Caches is right —
    /// losing a snapshot to a purge costs the user an unsaved composer, which is
    /// exactly the risk they already accept by not saving a draft.
    convenience init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        self.init(fileURL: base.appendingPathComponent("composer-snapshot.json"))
    }

    /// Test initializer.
    init(fileURL: URL) {
        self.fileURL = fileURL
    }

    /// The stored snapshot, or nil when there is nothing to restore. A payload
    /// written by an older build must never crash the composer, so a decode
    /// failure discards the file rather than propagating.
    func load() -> PebbleDraftPayload? {
        guard let data = try? Data(contentsOf: fileURL) else { return nil }
        do {
            let payload = try JSONDecoder().decode(PebbleDraftPayload.self, from: data)
            return payload.isEmpty ? nil : payload
        } catch {
            logger.warning("discarding unreadable snapshot: \(error.localizedDescription, privacy: .private)")
            clear()
            return nil
        }
    }

    /// Overwrite the snapshot. Callers debounce; see `ComposerAutosave`.
    func save(_ payload: PebbleDraftPayload) {
        guard !payload.isEmpty else {
            clear()
            return
        }
        do {
            try JSONEncoder().encode(payload).write(to: fileURL, options: .atomic)
        } catch {
            logger.error("snapshot write failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    func clear() {
        try? FileManager.default.removeItem(at: fileURL)
    }
}

/// Debounces snapshot writes so every keystroke does not hit the disk, and
/// flushes on demand when the scene backgrounds.
///
/// Separate from the store so the store stays a dumb, testable file wrapper and
/// the timing policy lives in one place.
@MainActor
final class ComposerAutosave {

    private let store: ComposerSnapshotStore
    private let debounce: Duration
    private var task: Task<Void, Never>?
    private var pending: PebbleDraftPayload?

    init(store: ComposerSnapshotStore, debounce: Duration = .milliseconds(800)) {
        self.store = store
        self.debounce = debounce
    }

    /// Queue a write. Supersedes any pending one.
    func schedule(_ payload: PebbleDraftPayload) {
        pending = payload
        task?.cancel()
        task = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(for: debounce)
            guard !Task.isCancelled else { return }
            flush()
        }
    }

    /// Write the pending snapshot now. Called when the scene leaves the
    /// foreground, which is the last reliable moment before a process kill.
    func flush() {
        task?.cancel()
        task = nil
        guard let payload = pending else { return }
        pending = nil
        store.save(payload)
    }

    /// The composer published or saved a server draft — the snapshot is
    /// redundant, and leaving it would re-offer work the user already committed.
    func clear() {
        task?.cancel()
        task = nil
        pending = nil
        store.clear()
    }
}
