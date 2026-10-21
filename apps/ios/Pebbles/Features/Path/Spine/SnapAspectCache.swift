import CoreGraphics
import Foundation
import ImageIO
import Observation
import os

/// Photo ratios for the Spine's cards (#1130). The thumb is signed through
/// `SnapURLCache`, downloaded, and its header read with
/// `CGImageSourceCopyPropertiesAtIndex`, which does not decode. Ratios are kept
/// by storage path for the session; a probe that fails or passes 4 s (signing
/// and download together, as Android's `SnapAspectCache.kt`) logs a warning
/// once and is not retried in the session. Neither `path_moments` nor
/// `MomentDetail.snaps` carries dimensions.
@Observable @MainActor
final class SnapAspectCache {
    typealias Loader = @Sendable (URL) async throws -> Data

    private(set) var ratios: [String: CGFloat] = [:]
    @ObservationIgnored private var failed: Set<String> = []
    @ObservationIgnored private var inflight: [String: Task<CGFloat?, Never>] = [:]
    @ObservationIgnored private let urls: SnapURLCache
    @ObservationIgnored private let load: Loader
    @ObservationIgnored private let timeout: Duration
    @ObservationIgnored private let logger = Logger(subsystem: "app.pbbls.ios", category: "snap-aspect")

    init(urls: SnapURLCache, load: @escaping Loader = SnapAspectCache.download, timeout: Duration = .seconds(4)) {
        self.urls = urls
        self.load = load
        self.timeout = timeout
    }

    nonisolated static let download: Loader = { url in
        try await URLSession.shared.data(from: url).0
    }

    func ratio(for path: String) -> CGFloat? {
        ratios[path]
    }

    /// References, the lab and the fixture week: a known ratio, no probe.
    func prime(_ path: String, ratio: CGFloat) {
        ratios[path] = ratio
    }

    /// `path`'s photo ratio, probed at most once per session; concurrent asks share one probe.
    @discardableResult
    func probe(_ path: String) async -> CGFloat? {
        if let known = ratios[path] { return known }
        if failed.contains(path) { return nil }
        if let running = inflight[path] { return await running.value }
        let task = Task { await self.fetch(path) }
        inflight[path] = task
        let ratio = await task.value
        inflight[path] = nil
        if let ratio { ratios[path] = ratio } else { failed.insert(path) }
        return ratio
    }

    /// The focused week's photos and its pager neighbours' (§Data flow), probed together.
    func prefetch(_ paths: [String]) async {
        let pending = Set(paths).filter { ratios[$0] == nil && !failed.contains($0) }
        await withTaskGroup(of: Void.self) { group in
            for path in pending {
                group.addTask { await self.probe(path) }
            }
        }
    }

    private func fetch(_ path: String) async -> CGFloat? {
        do {
            let data = try await Self.withTimeout(timeout) { [urls, load] in
                let url = try await urls.signedURLs(storagePath: path).thumb
                return try await load(url)
            }
            guard let ratio = Self.ratio(ofImage: data) else {
                logger.warning("snap ratio unreadable for \(path, privacy: .public)")
                return nil
            }
            return ratio
        } catch {
            let reason = error.localizedDescription
            logger.warning("snap ratio probe failed for \(path, privacy: .public): \(reason, privacy: .private)")
            return nil
        }
    }

    private struct Timeout: Error {}

    /// Resumes its continuation once: with the work's result or with `Timeout`, whichever comes first.
    private final class Race<T: Sendable>: @unchecked Sendable {
        private let lock = NSLock()
        private var continuation: CheckedContinuation<T, Error>?

        init(_ continuation: CheckedContinuation<T, Error>) {
            self.continuation = continuation
        }

        /// True when this call won the race.
        @discardableResult
        func finish(_ result: Result<T, Error>) -> Bool {
            lock.lock()
            let winner = continuation
            continuation = nil
            lock.unlock()
            winner?.resume(with: result)
            return winner != nil
        }
    }

    /// `work`'s result, or `Timeout` after `limit`. Unstructured on purpose: a task
    /// group waits for its children, so a step that ignores cancellation (a hung
    /// signer) would hold the probe past its limit.
    private nonisolated static func withTimeout<T: Sendable>(
        _ limit: Duration, _ work: @escaping @Sendable () async throws -> T
    ) async throws -> T {
        try await withCheckedThrowingContinuation { continuation in
            let race = Race(continuation)
            let worker = Task {
                do { race.finish(.success(try await work())) } catch { race.finish(.failure(error)) }
            }
            Task {
                try? await Task.sleep(for: limit)
                if race.finish(.failure(Timeout())) { worker.cancel() }
            }
        }
    }

    /// Width / height from the header, without decoding the pixels.
    nonisolated static func ratio(ofImage data: Data) -> CGFloat? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.doubleValue,
              let height = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.doubleValue
        else { return nil }
        let orientation = (properties[kCGImagePropertyOrientation] as? NSNumber)?.intValue ?? 1
        return ratio(width: width, height: height, orientation: orientation)
    }

    /// EXIF orientations 5–8 are a quarter turn: the photo shows with its sides swapped.
    nonisolated static func ratio(width: Double, height: Double, orientation: Int) -> CGFloat? {
        guard width > 0, height > 0 else { return nil }
        return (5...8).contains(orientation) ? CGFloat(height / width) : CGFloat(width / height)
    }
}
