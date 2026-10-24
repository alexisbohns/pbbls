import Foundation
import os

/// The wraps kept on the device (#1226, spec §7.1; Android: `WrapDiskCache`), so
/// a cold start draws the last strip and wraps before the network answers. One
/// file per page, `{root}/v{schema}/{owner}/{zone}/{page}.json`, holding the
/// server's body as it came.
///
/// - Another owner, zone or format never reads a file: each is a path part, and
///   bumping `schemaVersion` drops the old tree on the next write.
/// - Writes are atomic, so a crash never leaves half a body; path parts are
///   sanitised, so nothing climbs out of the root.
/// - Display only: the revalidation always runs, the server stays the truth,
///   and an IO failure is logged and read as a miss.
/// - Private journal content at rest: it lives in Caches, which is never backed
///   up, and `SignedOutPurge` wipes it on sign-out and account deletion. A
///   fetch sent before the purge and landing after it is not kept: a writer
///   takes the `epoch` when it asks, and `clear()` moves it on.
///
/// A class over the file system, whose calls are thread-safe; its little state
/// (the epoch, the one cleanup) is under a lock. Callers on the main actor
/// reach it through `PathWrapService`'s nonisolated methods.
final class WrapDiskCache: @unchecked Sendable {
    static let schemaVersion = 1

    static let standard = WrapDiskCache(root: FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)
        .first?.appendingPathComponent("path-wraps") ?? FileManager.default.temporaryDirectory
        .appendingPathComponent("path-wraps"))

    let root: URL

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "path-wraps")
    private let lock = NSLock()
    private var currentEpoch = 0
    private var prunedOldVersions = false

    init(root: URL) {
        self.root = root
    }

    /// The purge count: take it before a request, hand it to `write`.
    var epoch: Int {
        lock.withLock { currentEpoch }
    }

    func read(_ page: String, owner: String, zone: String) -> Data? {
        let url = file(page, owner: owner, zone: zone)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        do {
            return try Data(contentsOf: url)
        } catch {
            Self.logger.error("kept wrap unreadable: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    /// Keeps `data`, unless a purge ran since `epoch` was taken (nil: no check).
    func write(_ data: Data, page: String, owner: String, zone: String, epoch: Int? = nil) {
        let url = file(page, owner: owner, zone: zone)
        lock.withLock {
            if let epoch, epoch != currentEpoch { return }
            pruneOldVersionsOnce()
            do {
                try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                        withIntermediateDirectories: true)
                try data.write(to: url, options: .atomic)
            } catch {
                Self.logger.error("keeping a wrap failed: \(error.localizedDescription, privacy: .private)")
            }
        }
    }

    /// Everything kept, for every owner: the sign-out purge. A write already
    /// asked for with an older epoch is dropped.
    func clear() {
        lock.withLock {
            currentEpoch += 1
            guard FileManager.default.fileExists(atPath: root.path) else { return }
            do {
                try FileManager.default.removeItem(at: root)
            } catch {
                Self.logger.error("clearing the kept wraps failed: \(error.localizedDescription, privacy: .private)")
            }
        }
    }

    func file(_ page: String, owner: String, zone: String) -> URL {
        root.appendingPathComponent("v\(Self.schemaVersion)", isDirectory: true)
            .appendingPathComponent(Self.component(owner), isDirectory: true)
            .appendingPathComponent(Self.component(zone), isDirectory: true)
            .appendingPathComponent(Self.component(page) + ".json")
    }

    /// Letters, digits, `-` and `_` only; anything else (a `/`, a `.`) becomes `_`.
    static func component(_ raw: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_"))
        let safe = String(raw.unicodeScalars.map { allowed.contains($0) && $0.isASCII ? Character($0) : "_" })
        return safe.isEmpty ? "_" : safe
    }

    /// Drops an older format's tree, once per process (under the lock).
    private func pruneOldVersionsOnce() {
        guard !prunedOldVersions else { return }
        prunedOldVersions = true
        let current = "v\(Self.schemaVersion)"
        let names = (try? FileManager.default.contentsOfDirectory(atPath: root.path)) ?? []
        for name in names where name != current {
            try? FileManager.default.removeItem(at: root.appendingPathComponent(name))
        }
    }
}
