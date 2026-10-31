import CoreGraphics
import Foundation

/// The kit's outlines, memoised by seed, cut and size (#1382). A cut casts
/// hundreds of rays, and `Shape.path(in:)` runs off the main actor, so the
/// cache is locked rather than actor-bound. Bounded: it empties past 64 entries.
enum StoneCache {
    private static let lock = NSLock()
    private static var entries: [String: [[CGPoint]]] = [:]

    static func single(size: CGSize, seed: String, options: StoneOptions) -> [CGPoint] {
        outlines(key("single", size, seed, 1, options)) {
            [StoneGeometry.single(size: size, seed: seed, options: options)]
        }.first ?? []
    }

    static func split(size: CGSize, count: Int, seed: String, options: StoneOptions) -> [[CGPoint]] {
        outlines(key("split", size, seed, count, options)) {
            StoneGeometry.split(size: size, count: count, seed: seed, options: options)
        }
    }

    private static func key(_ kind: String, _ size: CGSize, _ seed: String, _ count: Int,
                            _ options: StoneOptions) -> String {
        let quarter = { (value: CGFloat) in Int((value * 4).rounded()) }
        return "\(kind)|\(seed)|\(count)|\(quarter(size.width))x\(quarter(size.height))|\(options)"
    }

    private static func outlines(_ key: String, make: () -> [[CGPoint]]) -> [[CGPoint]] {
        lock.lock()
        if let hit = entries[key] {
            lock.unlock()
            return hit
        }
        lock.unlock()
        let made = make()
        lock.lock()
        if entries.count > 64 { entries.removeAll() }
        entries[key] = made
        lock.unlock()
        return made
    }
}
