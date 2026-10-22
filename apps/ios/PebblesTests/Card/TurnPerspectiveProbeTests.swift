import CoreGraphics
import SwiftUI
import Testing
@testable import Pebbles

/// #1146 §Turn, decision 1: render a 300 × 400 panel turned to t = 0.25 and
/// measure its two edges. The near edge must be `h·d / (d − (w/2)·sin θ)`
/// without the fit (the eye 864 pt away), and the card's height with it; the
/// end edge (right, in LTR) is the near one, as a page turned by its edge.
@Suite("Turn perspective probe")
@MainActor
struct TurnPerspectiveProbeTests {
    static let size = CGSize(width: 300, height: 400)

    /// The heights of the leftmost and rightmost painted columns.
    private func edges<V: View>(_ view: V) throws -> (left: CGFloat, right: CGFloat) {
        let renderer = ImageRenderer(content: view.frame(width: 600, height: 600))
        renderer.scale = 1
        let image = try #require(renderer.cgImage)
        let width = image.width, height = image.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                                          bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        #expect(drawn)
        func column(_ x: Int) -> Int { (0..<height).filter { pixels[($0 * width + x) * 4 + 3] > 128 }.count }
        let painted = (0..<width).filter { column($0) > 0 }
        let first = try #require(painted.first), last = try #require(painted.last)
        return (CGFloat(column(first + 1)), CGFloat(column(last - 1)))
    }

    private var panel: some View {
        Rectangle().fill(Color.pebbles.onSurface).frame(width: Self.size.width, height: Self.size.height)
    }

    private func nearEdge(_ t: Double) -> CGFloat {
        let lean = Self.size.width / 2 * CGFloat(sin(Double.pi * t))
        return Self.size.height * TurnGeometry.eyeDistance / (TurnGeometry.eyeDistance - lean)
    }

    @Test("SwiftUI's rotation3DEffect puts the eye 864 pt away with this perspective")
    func swiftUIProjection() throws {
        let view = panel.rotation3DEffect(TurnGeometry.angle(progress: 0.25, rightToLeft: false),
                                          axis: (x: 0, y: 1, z: 0), anchor: .center,
                                          perspective: TurnGeometry.perspective(width: Self.size.width,
                                                                                height: Self.size.height))
        let measured = try edges(view)
        #expect(abs(measured.right - nearEdge(0.25)) <= 3, "near edge \(measured.right), predicted \(nearEdge(0.25))")
        #expect(measured.right > measured.left)
    }

    @Test("CardTurnEffect builds the same projection, and its fit holds the near edge to the card",
          arguments: [0.25, 0.45])
    func effect(t: Double) throws {
        let unfitted = try edges(panel.modifier(CardTurnEffect(progress: t, rightToLeft: false, fits: false)))
        #expect(abs(unfitted.right - nearEdge(t)) <= 6, "near edge \(unfitted.right), predicted \(nearEdge(t))")
        let fitted = try edges(panel.modifier(CardTurnEffect(progress: t, rightToLeft: false)))
        #expect(fitted.right <= Self.size.height + 1, "fitted near edge \(fitted.right)")
        #expect(fitted.right > fitted.left)
    }
}
