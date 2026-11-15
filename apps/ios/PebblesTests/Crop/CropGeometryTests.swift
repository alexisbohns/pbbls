import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("CropGeometry")
struct CropGeometryTests {
    private static let portrait = CGSize(width: 1536, height: 2048)   // 3:4
    private static let landscape = CGSize(width: 2048, height: 1152)  // 16:9

    /// Every window corner, taken back into the image, lies inside it.
    private func covers(_ g: CropGeometry, tolerance: CGFloat = 0.01) -> Bool {
        g.windowCornersInImage().allSatisfy { p in
            abs(p.x) <= g.imageSize.width / 2 + tolerance && abs(p.y) <= g.imageSize.height / 2 + tolerance
        }
    }

    @Test("opens on the closest format, centred, at the smallest covering scale")
    func initial() {
        let g = CropGeometry.initial(imageSize: Self.portrait)
        #expect(g.ratio == .threeFour)
        #expect(g.focus == .zero)
        #expect(g.angle == 0 && g.quarterTurns == 0)
        #expect(abs(g.scale - g.minScale) < 1e-9)
        #expect(abs(g.scale - 1.0 / 2048) < 1e-9)   // window height 1 == image height
        #expect(covers(g))
    }

    @Test("min scale covers exactly: a hair less leaves a corner out", arguments: [-45.0, -17.0, 0.0, 9.5, 33.0, 45.0])
    func minScaleIsExact(angle: Double) {
        for size in [Self.portrait, Self.landscape] {
            for ratio in CropRatio.allCases {
                for turns in 0..<4 {
                    var g = CropGeometry.initial(imageSize: size)
                    g.ratio = ratio
                    g.quarterTurns = turns
                    g.angle = angle
                    g.scale = g.minScale
                    #expect(covers(g), "\(size) \(ratio) \(turns) \(angle)")
                    g.scale = g.minScale * 0.999
                    #expect(!covers(g), "\(size) \(ratio) \(turns) \(angle)")
                }
            }
        }
    }

    @Test("normalising always covers, whatever the pan, zoom, turn and format")
    func normalisedAlwaysCovers() {
        let angles: [Double] = [-45, -30, -7.5, 0, 3, 22, 45]
        let focuses: [CGPoint] = [.zero, CGPoint(x: 5000, y: 0), CGPoint(x: -900, y: 1400), CGPoint(x: 300, y: -300)]
        let scales: [CGFloat] = [0.00001, 0.0004, 0.002, 1]
        for size in [Self.portrait, Self.landscape] {
            for ratio in CropRatio.allCases {
                for turns in 0..<4 {
                    for angle in angles {
                        for focus in focuses {
                            for scale in scales {
                                let g = CropGeometry(imageSize: size, ratio: ratio, quarterTurns: turns,
                                                     angle: angle, scale: scale, focus: focus).normalized()
                                #expect(covers(g), "\(size) \(ratio) \(turns) \(angle) \(focus) \(scale)")
                                #expect(g.scale <= g.maxScale + 1e-12)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test("a focus already inside the bounds is left where it is")
    func clampKeepsValidFocus() {
        var g = CropGeometry.initial(imageSize: Self.landscape)
        g.ratio = .square
        g.scale = g.minScale
        g.focus = CGPoint(x: 120, y: 0)
        #expect(g.normalized().focus == CGPoint(x: 120, y: 0))
    }

    @Test("the angle is clamped to ±45 and the turns wrap")
    func clampsAngleAndTurns() {
        let g = CropGeometry(imageSize: Self.portrait, ratio: .square, quarterTurns: 5,
                             angle: 80, scale: 1, focus: .zero).normalized()
        #expect(g.angle == 45)
        #expect(g.quarterTurns == 1)
    }

    @Test("turning a quarter flips the format to its pair and keeps the focus")
    func quarterTurn() {
        var g = CropGeometry.initial(imageSize: Self.portrait)
        g.focus = CGPoint(x: 0, y: 100)
        let turned = g.turnedQuarter()
        #expect(turned.quarterTurns == 1)
        #expect(turned.ratio == .fourThree)
        // Here the turned window spans the whole photo, so the focus is pulled to the centre.
        #expect(turned.focus == .zero)
        #expect(turned == turned.normalized())
    }

    @Test("turning a quarter on a zoomed-in photo leaves the focus where it was")
    func quarterTurnKeepsRoomyFocus() {
        var g = CropGeometry.initial(imageSize: Self.portrait)
        g.scale = g.minScale * 3
        g.focus = CGPoint(x: 40, y: 60)
        let turned = g.turnedQuarter()
        #expect(turned.focus == CGPoint(x: 40, y: 60))
        #expect(turned.quarterTurns == 1)
    }

    @Test("changing the angle raises the scale only as far as coverage needs")
    func angleRaisesScaleMinimally() {
        var g = CropGeometry.initial(imageSize: Self.portrait)
        g.scale = g.minScale * 3
        let tilted = g.withAngle(10)
        #expect(tilted.scale == g.scale)          // already covering: untouched
        let fromMin = CropGeometry.initial(imageSize: Self.portrait).withAngle(10)
        #expect(abs(fromMin.scale - fromMin.minScale) < 1e-12)
    }

    @Test("dragging right moves the photo right: the focus moves left in the image")
    func panDirection() {
        let g = CropGeometry.initial(imageSize: Self.landscape)   // 16:9 → 4:3, room to pan sideways
        let moved = g.panned(by: CGSize(width: 0.1, height: 0))
        #expect(moved.focus.x < 0)
        #expect(abs(moved.focus.y) < 1e-9)
    }

    @Test("the rubber band shows part of an overshoot, never all of it")
    func rubberBand() {
        let g = CropGeometry.initial(imageSize: Self.landscape)
        let far = g.panned(by: CGSize(width: 10, height: 0))
        let banded = far.rubberBanded()
        let clamped = far.normalized()
        #expect(abs(banded.focus.x) > abs(clamped.focus.x))
        #expect(abs(banded.focus.x) < abs(far.focus.x))
    }

    @Test("the window offset puts the focus at the window centre")
    func offsetMatchesFocus() {
        var g = CropGeometry.initial(imageSize: Self.landscape)
        g.angle = 20
        g.scale = g.minScale * 2
        g.focus = CGPoint(x: 200, y: -50)
        // The focus point, drawn: R(θ)·(focus·scale) + offset == 0.
        let t = CGAffineTransform(rotationAngle: g.theta)
        let drawn = CGPoint(x: g.focus.x * g.scale, y: g.focus.y * g.scale).applying(t)
        #expect(abs(drawn.x + g.windowOffset.width) < 1e-9)
        #expect(abs(drawn.y + g.windowOffset.height) < 1e-9)
    }
}
