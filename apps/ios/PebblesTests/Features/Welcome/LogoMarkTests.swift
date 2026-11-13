import Testing
import Foundation
import CoreGraphics
@testable import Pebbles

@Suite
struct LogoMarkTests {

    @Test("The mark is bundled and parses to a non-empty path")
    func parsesToNonEmptyPath() throws {
        let parsed = try #require(LogoMark.parse())
        #expect(parsed.viewBox == CGRect(x: 0, y: 0, width: 1024, height: 1024))
        let bounds = parsed.outline.boundingBoxOfPath
        #expect(!bounds.isNull && !bounds.isEmpty)
        // A pebble outline, a spiral and the P: three subpaths at least.
        #expect(Self.closeCount(of: parsed.outline) >= 3)
    }

    @Test("Q commands parse")
    func parsesQuadraticCurves() throws {
        let parsed = try #require(LogoMark.parse(
            svg: #"<svg viewBox="0 0 10 10"><path d="M0 0Q5 10 10 0Z"/></svg>"#
        ))
        #expect(parsed.outline.boundingBoxOfPath.width == 10)
    }

    @Test("Variants differ from each other and from the outline")
    func variantsDiffer() throws {
        let model = try #require(LogoMark.build())
        #expect(model.variants.count == LogoMark.seeds.count)
        for i in 0..<model.variants.count {
            #expect(model.variants[i].boundingBoxOfPath != model.outline.boundingBoxOfPath)
            for j in (i + 1)..<model.variants.count {
                #expect(model.variants[i].boundingBoxOfPath != model.variants[j].boundingBoxOfPath)
            }
        }
    }

    @Test("Every point stays within 1.5% of the mark's size and moves at least 0.8% somewhere")
    func displacementStaysWithinAmplitude() throws {
        let parsed = try #require(LogoMark.parse())
        let bounds = parsed.outline.boundingBoxOfPath
        let size = Double(max(bounds.width, bounds.height))
        let base = Self.points(of: parsed.outline)
        var wobbles: [[CGPoint]] = []
        for seed in LogoMark.seeds {
            let moved = Self.points(of: LogoMark.wobble(parsed.outline, seed: seed))
            #expect(moved.count == base.count)
            wobbles.append(moved)
            let largest = zip(base, moved).map { Double(hypot($0.x - $1.x, $0.y - $1.y)) }.max() ?? 0
            #expect(largest <= 0.015 * size, "seed \(seed) moved \(largest / size * 100)% of the mark")
            #expect(largest >= 0.008 * size, "seed \(seed) barely moves: \(largest / size * 100)%")
        }
        // Different seeds displace the same point differently.
        #expect(wobbles[0][10] != wobbles[1][10])
    }

    @Test("Wobble keeps the outline's structure: subpaths stay closed")
    func wobblePreservesStructure() throws {
        let parsed = try #require(LogoMark.parse())
        let moved = LogoMark.wobble(parsed.outline, seed: LogoMark.seeds[0])
        #expect(Self.closeCount(of: moved) == Self.closeCount(of: parsed.outline))
        #expect(Self.closeCount(of: moved) >= 3)
    }

    /// Every anchor and control point, in path order.
    static func points(of path: CGPath) -> [CGPoint] {
        var out: [CGPoint] = []
        path.applyWithBlock { pointer in
            let element = pointer.pointee
            let count: Int
            switch element.type {
            case .moveToPoint, .addLineToPoint: count = 1
            case .addQuadCurveToPoint: count = 2
            case .addCurveToPoint: count = 3
            default: count = 0
            }
            for i in 0..<count { out.append(element.points[i]) }
        }
        return out
    }

    static func closeCount(of path: CGPath) -> Int {
        var count = 0
        path.applyWithBlock { if $0.pointee.type == .closeSubpath { count += 1 } }
        return count
    }
}

@Suite
struct LogoSettleGateTests {

    @Test("Already ready on appear: fires at once")
    func firesImmediatelyWhenReady() {
        var gate = LogoSettleGate()
        let fired = gate.shouldFire(ready: true)
        #expect(fired)
    }

    @Test("Not ready: never fires; fires the first time it becomes ready")
    func firesOnlyWhenReady() {
        var gate = LogoSettleGate()
        let early = [gate.shouldFire(ready: false), gate.shouldFire(ready: false)]
        #expect(early == [false, false])
        let fired = gate.shouldFire(ready: true)
        #expect(fired)
    }

    @Test("Fires once")
    func firesOnce() {
        var gate = LogoSettleGate()
        let results = [
            gate.shouldFire(ready: true),
            gate.shouldFire(ready: true),
            gate.shouldFire(ready: false),
            gate.shouldFire(ready: true)
        ]
        #expect(results == [true, false, false, false])
    }
}
