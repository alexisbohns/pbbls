import Foundation
import Observation
import os

/// Device-local autosave for a half-finished Complete flow, one file per
/// moment (A3, spec D7). Not offline support: no sync, no merge. The file
/// carries its owner; another account's file is deleted on read, and
/// `SignedOutPurge` clears them all.
@Observable
@MainActor
final class CompleteDraftStore {

    private let directory: URL
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "complete-drafts")

    convenience init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        self.init(directory: base.appendingPathComponent("complete-drafts"))
    }

    init(directory: URL) {
        self.directory = directory
    }

    func load(momentId: UUID, ownerId: UUID) -> CompleteDraft? {
        let url = fileURL(for: momentId)
        guard let data = try? Data(contentsOf: url) else { return nil }
        let snapshot: CompleteDraftSnapshot
        do {
            snapshot = try JSONDecoder().decode(CompleteDraftSnapshot.self, from: data)
        } catch {
            logger.warning("discarding unreadable draft: \(error.localizedDescription, privacy: .private)")
            clear(momentId: momentId)
            return nil
        }
        guard snapshot.ownerId == ownerId, snapshot.momentId == momentId else {
            logger.info("discarding a draft owned by another account")
            clear(momentId: momentId)
            return nil
        }
        let draft = snapshot.draft
        return draft.isEmpty ? nil : draft
    }

    func save(_ draft: CompleteDraft, momentId: UUID, ownerId: UUID) {
        guard !draft.isEmpty else {
            clear(momentId: momentId)
            return
        }
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let snapshot = CompleteDraftSnapshot(ownerId: ownerId, momentId: momentId, draft: draft)
            try JSONEncoder().encode(snapshot).write(to: fileURL(for: momentId), options: .atomic)
        } catch {
            logger.error("draft write failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    func clear(momentId: UUID) {
        try? FileManager.default.removeItem(at: fileURL(for: momentId))
    }

    func clearAll() {
        try? FileManager.default.removeItem(at: directory)
    }

    private func fileURL(for momentId: UUID) -> URL {
        directory.appendingPathComponent("\(momentId.uuidString.lowercased()).json")
    }
}

/// Debounced writes for one moment's flow; flushed on background and on ✕.
@MainActor
final class CompleteAutosave {
    private let store: CompleteDraftStore
    private let momentId: UUID
    private let debounce: Duration
    private let ownerId: () -> UUID?
    private var task: Task<Void, Never>?
    private var pending: CompleteDraft?

    init(store: CompleteDraftStore, momentId: UUID, debounce: Duration = .milliseconds(800), ownerId: @escaping () -> UUID?) {
        self.store = store
        self.momentId = momentId
        self.debounce = debounce
        self.ownerId = ownerId
    }

    func schedule(_ draft: CompleteDraft) {
        pending = draft
        task?.cancel()
        task = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(for: debounce)
            guard !Task.isCancelled else { return }
            flush()
        }
    }

    func flush() {
        task?.cancel()
        task = nil
        guard let draft = pending else { return }
        pending = nil
        guard let owner = ownerId() else { return }
        store.save(draft, momentId: momentId, ownerId: owner)
    }

    func clear() {
        task?.cancel()
        task = nil
        pending = nil
        store.clear(momentId: momentId)
    }
}

/// The file body (A3): `complete_pebble`'s key names, unset keys omitted.
struct CompleteDraftSnapshot: Codable, Equatable {
    let ownerId: UUID
    let momentId: UUID
    var intensity: Int?
    var positiveness: Int?
    var emotionId: UUID?
    var domainIds: [UUID]?
    var soulIds: [UUID]?
    var collectionIds: [UUID]?
    var visibility: String?

    private enum CodingKeys: String, CodingKey {
        case intensity, positiveness, visibility
        case ownerId = "owner_id"
        case momentId = "moment_id"
        case emotionId = "emotion_id"
        case domainIds = "domain_ids"
        case soulIds = "soul_ids"
        case collectionIds = "collection_ids"
    }

    init(ownerId: UUID, momentId: UUID, draft: CompleteDraft) {
        self.ownerId = ownerId
        self.momentId = momentId
        intensity = draft.valence?.intensity
        positiveness = draft.valence?.positiveness
        emotionId = draft.emotionId
        domainIds = draft.domainId.map { [$0] }
        soulIds = draft.soulIds.isEmpty ? nil : draft.soulIds
        collectionIds = draft.collectionId.map { [$0] }
        visibility = draft.visibility.rawValue
    }

    var draft: CompleteDraft {
        var draft = CompleteDraft()
        if let positiveness, let intensity {
            draft.valence = Valence.from(positiveness: positiveness, intensity: intensity)
        }
        draft.emotionId = emotionId
        draft.domainId = domainIds?.first
        draft.soulIds = soulIds ?? []
        draft.collectionId = collectionIds?.first
        draft.visibility = visibility.flatMap(Visibility.init(rawValue:)) ?? .secret
        return draft
    }
}
