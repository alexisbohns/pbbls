import CoreGraphics
import Foundation
import ImageIO
import Observation
import os

/// Photo ratios for the Spine's cards (#1130 D3). The thumb is signed through
/// `SnapURLCache`, downloaded, and its header read with
/// `CGImageSourceCopyPropertiesAtIndex`, which does not decode. Ratios are kept
/// by storage path for the session; a probe that fails or passes 4 s logs a
/// warning once and is not retried in the session. Neither `path_moments` nor
/// `MomentDetail.snaps` carries dimensions. New pattern, named in the D2b PR.
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

    /// The focused week's photos and its pager neighbours' (§Data flow).
    func prefetch(_ paths: [String]) async {
        for path in Set(paths) where ratios[path] == nil && !failed.contains(path) {
            await probe(path)
        }
    }

    private func fetch(_ path: String) async -> CGFloat? {
        do {
            let url = try await urls.signedURLs(storagePath: path).thumb
            let data = try await Self.withTimeout(timeout) { [load] in try await load(url) }
            guard let ratio = Self.ratio(ofImage: data) else {
                logger.warning("snap ratio unreadable for \(path, privacy: .public)")
                return nil
            }
            return ratio
        } catch {
            logger.warning(
                "snap ratio probe failed for \(path, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
            return nil
        }
    }

    private struct Timeout: Error {}

    private nonisolated static func withTimeout(
        _ limit: Duration, _ work: @escaping @Sendable () async throws -> Data
    ) async throws -> Data {
        try await withThrowingTaskGroup(of: Data.self) { group in
            group.addTask { try await work() }
            group.addTask {
                try await Task.sleep(for: limit)
                throw Timeout()
            }
            defer { group.cancelAll() }
            guard let first = try await group.next() else { throw Timeout() }
            return first
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
