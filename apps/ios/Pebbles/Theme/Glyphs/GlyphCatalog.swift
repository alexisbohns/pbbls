import Foundation
import os

/// `pbbls-glyphs.json`, loaded once (spec §2): off the main thread by
/// `prewarm()` at launch, or synchronously under the lock by the first read
/// that comes before it.
final class GlyphCatalog: @unchecked Sendable {
    static let shared = GlyphCatalog(bundle: .main)
    static let resource = "pbbls-glyphs"
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "glyphs")

    private let bundle: Bundle
    private let lock = NSLock()
    private var file: GlyphFile?

    init(bundle: Bundle) {
        self.bundle = bundle
    }

    /// Loads the data in the background so the first glyph on screen does not wait for it.
    func prewarm() {
        DispatchQueue.global(qos: .userInitiated).async { _ = self.loaded() }
    }

    var states: [String: [String]] { loaded().states }

    func glyph(_ glyph: PbblsGlyph) -> GlyphData {
        guard let data = loaded().glyphs[glyph.rawValue] else {
            Self.logger.error("no data for glyph \(glyph.rawValue, privacy: .public)")
            return .empty
        }
        return data
    }

    private func loaded() -> GlyphFile {
        lock.lock()
        defer { lock.unlock() }
        if let file { return file }
        let read = Self.read(bundle)
        file = read
        return read
    }

    static func read(_ bundle: Bundle) -> GlyphFile {
        guard let url = bundle.url(forResource: resource, withExtension: "json") else {
            logger.error("\(resource, privacy: .public).json is not in the bundle")
            return .empty
        }
        do {
            return try JSONDecoder().decode(GlyphFile.self, from: Data(contentsOf: url))
        } catch {
            let reason = error.localizedDescription
            logger.error("\(resource, privacy: .public).json did not decode: \(reason, privacy: .public)")
            return .empty
        }
    }
}
