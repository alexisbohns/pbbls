import CoreGraphics
import Testing
@testable import Pebbles

@Suite("PhotoCropRenderer")
struct PhotoCropRendererTests {
    enum Quadrant { case red, green, blue, yellow }

    /// Upright quadrants: red top-left, green top-right, blue bottom-right, yellow bottom-left.
    static func quadrants(size: Int = 400) throws -> CGImage {
        let space = try #require(CGColorSpace(name: CGColorSpace.sRGB))
        let ctx = try #require(CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                                         space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue))
        let h = CGFloat(size) / 2
        // CGContext is y-up: the top half of the picture is y ∈ [h, 2h].
        ctx.setFillColor(red: 1, green: 0, blue: 0, alpha: 1); ctx.fill(CGRect(x: 0, y: h, width: h, height: h))
        ctx.setFillColor(red: 0, green: 1, blue: 0, alpha: 1); ctx.fill(CGRect(x: h, y: h, width: h, height: h))
        ctx.setFillColor(red: 0, green: 0, blue: 1, alpha: 1); ctx.fill(CGRect(x: h, y: 0, width: h, height: h))
        ctx.setFillColor(red: 1, green: 1, blue: 0, alpha: 1); ctx.fill(CGRect(x: 0, y: 0, width: h, height: h))
        return try #require(ctx.makeImage())
    }

    /// The colour at a point given y-down from the image's top-left.
    static func colour(_ image: CGImage, x: Int, yDown: Int) throws -> Quadrant? {
        let rgba = try #require(ReferenceImage.rgba(image))
        let i = (yDown * image.width + x) * 4
        let (r, g, b) = (rgba[i], rgba[i + 1], rgba[i + 2])
        switch (r > 200, g > 200, b > 200) {
        case (true, false, false): return .red
        case (false, true, false): return .green
        case (false, false, true): return .blue
        case (true, true, false): return .yellow
        default: return nil
        }
    }

    private func corners(_ image: CGImage) throws -> [Quadrant?] {
        let w = image.width, h = image.height, m = 4
        return [try Self.colour(image, x: m, yDown: m), try Self.colour(image, x: w - m, yDown: m),
                try Self.colour(image, x: w - m, yDown: h - m), try Self.colour(image, x: m, yDown: h - m)]
    }

    @Test("untouched square crop keeps the picture upright")
    func upright() throws {
        let source = try Self.quadrants()
        let g = CropGeometry.initial(imageSize: CGSize(width: 400, height: 400))
        let out = try PhotoCropRenderer.render(source, geometry: g)
        #expect(out.width == 400 && out.height == 400)
        #expect(try corners(out) == [.red, .green, .blue, .yellow])
    }

    @Test("a clockwise quarter turn moves top-left to top-right")
    func quarterTurn() throws {
        let source = try Self.quadrants()
        let g = CropGeometry.initial(imageSize: CGSize(width: 400, height: 400)).turnedQuarter()
        let out = try PhotoCropRenderer.render(source, geometry: g)
        #expect(try corners(out) == [.yellow, .red, .green, .blue])
    }

    @Test("a 4:5 crop panned left shows only the left half's colours")
    func panAndCrop() throws {
        let source = try Self.quadrants()
        var g = CropGeometry.initial(imageSize: CGSize(width: 400, height: 400)).withRatio(.fourFive)
        g.scale = g.minScale * 2.5
        g.focus = CGPoint(x: -100, y: 0)
        g = g.normalized()
        let out = try PhotoCropRenderer.render(source, geometry: g)
        #expect(abs(CGFloat(out.width) / CGFloat(out.height) - 0.8) < 0.01)
        #expect(try corners(out) == [.red, .red, .yellow, .yellow])
    }

    @Test("the output long edge never exceeds 1024 nor the source pixels it covers")
    func outputSize() {
        var g = CropGeometry.initial(imageSize: CGSize(width: 2048, height: 1536))
        #expect(PhotoCropRenderer.outputSize(for: g) == CGSize(width: 1024, height: 768))
        g.scale = g.minScale * 8   // window covers 256×192 source pixels
        #expect(PhotoCropRenderer.outputSize(for: g) == CGSize(width: 256, height: 192))
    }

    /// A solid-colour image of an awkward size, so the output rounds unevenly.
    private static func solid(width: Int, height: Int) throws -> CGImage {
        let space = try #require(CGColorSpace(name: CGColorSpace.sRGB))
        let ctx = try #require(CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                                         bytesPerRow: 0, space: space,
                                         bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue))
        ctx.setFillColor(red: 0.3, green: 0.6, blue: 0.2, alpha: 1)
        ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
        return try #require(ctx.makeImage())
    }

    @Test("every border pixel of a minimum-scale crop is the photo, never the empty context",
          arguments: [0.0, 7.0, 30.0, 45.0, -45.0])
    func edgesAreFilled(angle: Double) throws {
        for (width, height) in [(997, 1013), (1536, 2048), (811, 641)] {
            let source = try Self.solid(width: width, height: height)
            for ratio in [CropRatio.fourFive, .square, .fourThree] {
                var g = CropGeometry.initial(imageSize: CGSize(width: width, height: height)).withRatio(ratio)
                g.angle = angle
                g = g.normalized()
                g.scale = g.minScale
                let out = try PhotoCropRenderer.render(source, geometry: g)
                let rgba = try #require(ReferenceImage.rgba(out))
                let w = out.width, h = out.height
                var darkest = 255
                for y in 0..<h {
                    for x in 0..<w where x == 0 || y == 0 || x == w - 1 || y == h - 1 {
                        let i = (y * w + x) * 4
                        darkest = min(darkest, Int(min(rgba[i], rgba[i + 1], rgba[i + 2])))
                    }
                }
                // The solid colour's darkest channel is 0.2 * 255 = 51.
                #expect(darkest >= 49, "\(width)x\(height) \(ratio) \(angle): border dipped to \(darkest) in \(w)x\(h)")
            }
        }
    }

    @Test("a source that does not match the geometry's image size is refused")
    func sizeMismatch() throws {
        let source = try Self.quadrants()
        let g = CropGeometry.initial(imageSize: CGSize(width: 300, height: 400))
        #expect(throws: PhotoCropRendererError.self) { try PhotoCropRenderer.render(source, geometry: g) }
    }

    @Test("a tiny window still gives at least one pixel")
    func tinyOutput() {
        var g = CropGeometry.initial(imageSize: CGSize(width: 2048, height: 1536))
        g.scale = 100   // far past any real zoom
        let size = PhotoCropRenderer.outputSize(for: g)
        #expect(size.width >= 1 && size.height >= 1)
    }
}
