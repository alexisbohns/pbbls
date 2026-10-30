import CoreGraphics
import Testing
@testable import Pebbles

/// The mended band (#1306, the board's `shardStrip`): one shard a domain, the
/// seams leaning by a stable hash, so every device mends it the same way.
@Suite("Shard band geometry")
struct ShardBandGeometryTests {
    let g = ShardBandGeometry(width: 300, height: 30, count: 18)

    @Test("the band starts and ends square, and neighbours share their seam")
    func seams() {
        #expect(nearly(g.shard(0)[0], CGPoint(x: 0, y: 0)) && nearly(g.shard(0)[3], CGPoint(x: 0, y: 30)))
        #expect(nearly(g.shard(17)[1], CGPoint(x: 300, y: 0)) && nearly(g.shard(17)[2], CGPoint(x: 300, y: 30)))
        for i in 0..<17 {
            #expect(nearly(g.shard(i)[1], g.shard(i + 1)[0]))
            #expect(nearly(g.shard(i)[2], g.shard(i + 1)[3]))
        }
    }

    @Test("a seam leans at most 35 % of a cell, the same way every time")
    func jitter() {
        for i in 0..<17 {
            #expect(abs(g.jitter(i, "t")) <= g.cell * 0.35)
            #expect(g.jitter(i, "b") == ShardBandGeometry(width: 300, height: 30, count: 18).jitter(i, "b"))
        }
        #expect(ShardBandGeometry.unit("R0t") == CGFloat(StableHash.fnv1a("R0t") % 100_000) / 100_000)
        #expect(ShardBandGeometry.unit("R3b") >= 0 && ShardBandGeometry.unit("R3b") < 1)
    }

    @Test("the band keeps the board's 300 by 40 box; a new dot, stroke and all, stays inside it")
    func box() {
        #expect(DomainBand.box == CGSize(width: 300, height: 40) && DomainBand.bandHeight == 30)
        let band = ShardBandGeometry(width: DomainBand.box.width, height: DomainBand.bandHeight, count: 17)
        for index in 0..<17 {
            #expect(band.dot(index).y + 2.4 + 0.8 <= DomainBand.box.height)
        }
    }

    @Test("a new domain's dot sits under its shard's middle")
    func dot() {
        #expect(nearly(g.dot(13), CGPoint(x: 13.5 * 300 / 18, y: 36)))
    }
}
