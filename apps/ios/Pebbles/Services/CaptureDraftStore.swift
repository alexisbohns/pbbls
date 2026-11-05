import Foundation
import Observation
import os

/// Device-local autosave for the unsaved moment page (record-flow D2): name,
/// words, when, whether the user set the date, and the uploaded snap's
/// reference so the page survives a process kill. One file per owner, since
/// there is one Capture at a time; `SignedOutPurge` clears them all.
@Observable
@MainActor
final class CaptureDraftStore {
    private let directory: URL
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "capture-draft")

    convenience init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        self.init(directory: base.appendingPathComponent("capture-draft"))
    }

    init(directory: URL) { self.directory = directory }

    func load(ownerId: UUID) -> CaptureDraftSnapshot.Saved? {
        guard let data = try? Data(contentsOf: fileURL(for: ownerId)) else { return nil }
        do {
            let snapshot = try JSONDecoder().decode(CaptureDraftSnapshot.self, from: data)
            guard snapshot.ownerId == ownerId, !snapshot.saved.isEmpty else {
                clear(ownerId: ownerId)
                return nil
            }
            return snapshot.saved
        } catch {
            logger.warning("discarding unreadable capture draft: \(error.localizedDescription, privacy: .private)")
            clear(ownerId: ownerId)
            return nil
        }
    }

    func save(_ saved: CaptureDraftSnapshot.Saved, ownerId: UUID) {
        guard !saved.isEmpty else {
            clear(ownerId: ownerId)
            return
        }
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let snapshot = CaptureDraftSnapshot(ownerId: ownerId, saved: saved)
            try JSONEncoder().encode(snapshot).write(to: fileURL(for: ownerId), options: .atomic)
        } catch {
            logger.error("capture draft write failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    func clear(ownerId: UUID) { try? FileManager.default.removeItem(at: fileURL(for: ownerId)) }
    func clearAll() { try? FileManager.default.removeItem(at: directory) }

    private func fileURL(for ownerId: UUID) -> URL {
        directory.appendingPathComponent("\(ownerId.uuidString.lowercased()).json")
    }
}

/// The file body. `happened_at` at whole seconds (the standing timestamp rule).
struct CaptureDraftSnapshot: Codable, Equatable {
    typealias Snap = CaptureDraftSnap

    struct Saved: Equatable {
        var draft: CaptureDraft
        var hasEditedDate: Bool
        var snap: Snap?

        var isEmpty: Bool {
            draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                && draft.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                && snap == nil
        }
    }

    let ownerId: UUID
    var name: String?
    var description: String?
    var happenedAt: String
    var hasEditedDate: Bool
    var snap: Snap?

    private enum CodingKeys: String, CodingKey {
        case name, description, snap
        case ownerId = "owner_id"
        case happenedAt = "happened_at"
        case hasEditedDate = "has_edited_date"
    }

    init(ownerId: UUID, saved: Saved) {
        self.ownerId = ownerId
        name = saved.draft.name.isEmpty ? nil : saved.draft.name
        description = saved.draft.description.isEmpty ? nil : saved.draft.description
        happenedAt = ISO8601Flexible.string(from: saved.draft.happenedAt)
        hasEditedDate = saved.hasEditedDate
        snap = saved.snap
    }

    var saved: Saved {
        var draft = CaptureDraft()
        draft.name = name ?? ""
        draft.description = description ?? ""
        draft.happenedAt = ISO8601Flexible.parse(happenedAt) ?? Date()
        return Saved(draft: draft, hasEditedDate: hasEditedDate, snap: snap)
    }
}

/// The uploaded snap the page was holding: enough to send it with the save.
struct CaptureDraftSnap: Codable, Equatable {
    let id: UUID
    let storagePath: String
    private enum CodingKeys: String, CodingKey { case id; case storagePath = "storage_path" }
}
