import CoreGraphics

/// The domains' mended band (#1306, the board's `shardStrip`): `count` shards
/// across `width`, each seam leaning by up to 35 % of a cell at its top and its
/// bottom, by a stable hash of its index (FNV-1a, as the stones' crusts are),
/// so the band mends the same way on every device. Android: `ShardBandGeometry`.
struct ShardBandGeometry {
    let width: CGFloat
    let height: CGFloat
    let count: Int

    var cell: CGFloat { width / CGFloat(max(count, 1)) }

    /// A number in [0, 1) from `key`.
    static func unit(_ key: String) -> CGFloat {
        CGFloat(StableHash.fnv1a(key)) / 4_294_967_296
    }

    /// The lean of shard `index`'s right seam at its top (`"t"`) or bottom (`"b"`).
    func jitter(_ index: Int, _ end: String) -> CGFloat {
        (Self.unit("R\(index)\(end)") - 0.5) * cell * 0.7
    }

    /// Shard `index`'s corners: top-left, top-right, bottom-right, bottom-left.
    func shard(_ index: Int) -> [CGPoint] {
        let x = CGFloat(index) * cell
        let last = index == count - 1
        let rightTop = last ? 0 : jitter(index, "t")
        let rightBottom = last ? 0 : jitter(index, "b")
        let leftTop = index == 0 ? 0 : jitter(index - 1, "t")
        let leftBottom = index == 0 ? 0 : jitter(index - 1, "b")
        return [CGPoint(x: x + leftTop, y: 0), CGPoint(x: x + cell + rightTop, y: 0),
                CGPoint(x: x + cell + rightBottom, y: height), CGPoint(x: x + leftBottom, y: height)]
    }

    /// A new domain's dot, 6 below its shard's middle.
    func dot(_ index: Int) -> CGPoint {
        CGPoint(x: (CGFloat(index) + 0.5) * cell, y: height + 6)
    }
}
