import Foundation

/// Faithful Swift port of the SVG 1.1 §15.19 `feTurbulence type="fractalNoise"`
/// reference implementation, as transcribed by the issue #555 design playground.
/// The wobble look was tuned against this exact noise — substituting another
/// noise (simplex, GameplayKit, hash-based) changes the approved look.
///
/// Two structure quirks are deliberate and load-bearing:
/// - Gradients are generated for all four RGBA channels in the spec's loop
///   order even though the wobble only reads channels 0 (R) and 1 (G) — the
///   seeded LCG stream position depends on it.
/// - Integer math follows the spec's Schrage decomposition exactly, so the raw
///   LCG sequence golden-tests against the reference values in
///   `PebblesTests/Wobble/WobbleGolden.json`.
struct SVGTurbulence {

    private static let bSize = 0x100
    private static let bMask = 0xff
    private static let perlinN = 0x1000

    // Spec LCG constants (§15.19, "RandomNumberSetup").
    private static let randM = 2147483647   // 2**31 − 1
    private static let randA = 16807        // 7**5, primitive root of m
    private static let randQ = 127773       // m / a
    private static let randR = 2836         // m % a

    /// Lattice selector, length 2·bSize + 2.
    private let lattice: [Int]
    /// Normalized 2-D gradients per channel: [4][2·bSize + 2].
    private let gradient: [[SIMD2<Double>]]

    init(seed: Int) {
        let bSize = Self.bSize
        var lattice = [Int](repeating: 0, count: bSize + bSize + 2)
        var gradient = [[SIMD2<Double>]](
            repeating: [SIMD2<Double>](repeating: .zero, count: bSize + bSize + 2),
            count: 4
        )
        var seed = Self.normalizedSeed(seed)

        for channel in 0..<4 {
            for i in 0..<bSize {
                lattice[i] = i
                var vector = SIMD2<Double>.zero
                for j in 0..<2 {
                    seed = Self.nextRandom(seed)
                    vector[j] = Double((seed % (bSize + bSize)) - bSize) / Double(bSize)
                }
                // No zero-length guard, mirroring the reference: 0/0 → NaN on
                // both sides of the golden test, identically.
                let length = hypot(vector.x, vector.y)
                gradient[channel][i] = vector / length
            }
        }

        // Fisher–Yates-style lattice shuffle, consuming the same LCG stream.
        var i = bSize - 1
        while i > 0 {
            let swapped = lattice[i]
            seed = Self.nextRandom(seed)
            let j = seed % bSize
            lattice[i] = lattice[j]
            lattice[j] = swapped
            i -= 1
        }

        // Wrap-around duplication so `lattice[i + by]` never overruns.
        for i in 0..<(bSize + 2) {
            lattice[bSize + i] = lattice[i]
            for channel in 0..<4 {
                gradient[channel][bSize + i] = gradient[channel][i]
            }
        }

        self.lattice = lattice
        self.gradient = gradient
    }

    // MARK: - Noise

    /// 2-D gradient noise for one channel at noise-space coordinates, in [−1, 1].
    func noise2(channel: Int, x: Double, y: Double) -> Double {
        var t = x + Double(Self.perlinN)
        let bx0 = Int(t) & Self.bMask   // (t | 0) truncation, as in the reference
        let bx1 = (bx0 + 1) & Self.bMask
        let rx0 = t - Double(Int(t))
        let rx1 = rx0 - 1
        t = y + Double(Self.perlinN)
        let by0 = Int(t) & Self.bMask
        let by1 = (by0 + 1) & Self.bMask
        let ry0 = t - Double(Int(t))
        let ry1 = ry0 - 1

        let latticeX0 = lattice[bx0]
        let latticeX1 = lattice[bx1]
        let b00 = lattice[latticeX0 + by0]
        let b10 = lattice[latticeX1 + by0]
        let b01 = lattice[latticeX0 + by1]
        let b11 = lattice[latticeX1 + by1]

        let sx = Self.sCurve(rx0)
        let sy = Self.sCurve(ry0)
        let grad = gradient[channel]

        var q = grad[b00]
        let u0 = rx0 * q.x + ry0 * q.y
        q = grad[b10]
        let v0 = rx1 * q.x + ry0 * q.y
        let a = Self.lerp(sx, u0, v0)
        q = grad[b01]
        let u1 = rx0 * q.x + ry1 * q.y
        q = grad[b11]
        let v1 = rx1 * q.x + ry1 * q.y
        let b = Self.lerp(sx, u1, v1)
        return Self.lerp(sy, a, b)
    }

    /// Stitchless fractalNoise sum over `octaves` — signed, unclamped.
    func turbulence(channel: Int, x: Double, y: Double, baseFrequency: Double, octaves: Int) -> Double {
        var vx = x * baseFrequency
        var vy = y * baseFrequency
        var sum = 0.0
        var ratio = 1.0
        for _ in 0..<octaves {
            sum += noise2(channel: channel, x: vx, y: vy) / ratio
            vx *= 2
            vy *= 2
            ratio *= 2
        }
        return sum
    }

    // MARK: - Seeded LCG (spec §15.19)

    static func nextRandom(_ seed: Int) -> Int {
        var result = randA * (seed % randQ) - randR * (seed / randQ)
        if result <= 0 { result += randM }
        return result
    }

    static func normalizedSeed(_ seed: Int) -> Int {
        var seed = seed
        if seed <= 0 { seed = -(seed % (randM - 1)) + 1 }
        if seed > randM - 1 { seed = randM - 1 }
        return seed
    }

    /// First `count` raw LCG values for `seed` — consumed by the golden test.
    static func rawSequence(seed: Int, count: Int) -> [Int] {
        var values: [Int] = []
        values.reserveCapacity(count)
        var state = normalizedSeed(seed)
        for _ in 0..<count {
            state = nextRandom(state)
            values.append(state)
        }
        return values
    }

    // MARK: - Test hooks (internal; consumed via @testable)

    var latticePrefix16: [Int] { Array(lattice[0..<16]) }

    func gradientSample(channel: Int, index: Int) -> SIMD2<Double> {
        gradient[channel][index]
    }

    // MARK: - Helpers

    private static func sCurve(_ t: Double) -> Double { t * t * (3 - 2 * t) }
    private static func lerp(_ t: Double, _ a: Double, _ b: Double) -> Double { a + t * (b - a) }
}
