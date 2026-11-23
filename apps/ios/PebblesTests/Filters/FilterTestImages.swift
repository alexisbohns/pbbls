import CoreGraphics
import CoreImage
import CoreVideo
import Testing
@testable import Pebbles

/// Synthetic pictures and pixel statistics for the filter tests.
enum FilterTestImages {
    static let sRGB = CGColorSpace(name: CGColorSpace.sRGB)!

    /// `CGColor(red:green:blue:alpha:)` is Generic RGB, which shifts every value
    /// when drawn into an sRGB context; the tests want the numbers they write.
    static func srgb(_ red: CGFloat, _ green: CGFloat, _ blue: CGFloat) -> CGColor {
        CGColor(colorSpace: sRGB, components: [red, green, blue, 1])! // fixed component count, cannot fail
    }

    static func context(width: Int, height: Int) -> CGContext {
        CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                  space: sRGB, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    }

    /// A diagonal colour ramp with a white square, the same picture at any size.
    static func ramp(size: Int) -> CGImage {
        let ctx = context(width: size, height: size)
        let colours = [srgb(0.15, 0.25, 0.35),
                       srgb(0.85, 0.75, 0.65)] as CFArray
        let gradient = CGGradient(colorsSpace: sRGB, colors: colours, locations: [0, 1])!
        ctx.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: size, y: size), options: [])
        ctx.setFillColor(srgb(1, 1, 1))
        let s = CGFloat(size)
        ctx.fill(CGRect(x: s * 0.4, y: s * 0.4, width: s * 0.2, height: s * 0.2))
        return ctx.makeImage()!
    }

    /// Vertical dark and light stripes, `size / 32` px per half period so the same
    /// picture reads at any size. A high-frequency picture for blur and sharpen tests.
    static func stripes(size: Int) -> CGImage {
        let ctx = context(width: size, height: size)
        let width = max(size / 32, 1)
        for x in stride(from: 0, to: size, by: width) {
            let light = (x / width) % 2 == 0
            ctx.setFillColor(srgb(light ? 0.7 : 0.3, light ? 0.7 : 0.3, light ? 0.7 : 0.3))
            ctx.fill(CGRect(x: x, y: 0, width: width, height: size))
        }
        return ctx.makeImage()!
    }

    /// A subject mask the way Vision hands it over: a one-component 8-bit pixel
    /// buffer (left half 255, right half 0) read without colour management.
    static func subjectMask(width: Int, height: Int) throws -> CIImage {
        var buffer: CVPixelBuffer?
        CVPixelBufferCreate(nil, width, height, kCVPixelFormatType_OneComponent8, nil, &buffer)
        let pixels = try #require(buffer)
        CVPixelBufferLockBaseAddress(pixels, [])
        defer { CVPixelBufferUnlockBaseAddress(pixels, []) }
        let base = try #require(CVPixelBufferGetBaseAddress(pixels)).assumingMemoryBound(to: UInt8.self)
        let stride = CVPixelBufferGetBytesPerRow(pixels)
        for y in 0..<height {
            for x in 0..<width { base[y * stride + x] = x < width / 2 ? 255 : 0 }
        }
        return CIImage(cvPixelBuffer: pixels, options: [.colorSpace: NSNull()])
    }

    static func flat(red: CGFloat, green: CGFloat, blue: CGFloat, size: Int = 64) -> CGImage {
        let ctx = context(width: size, height: size)
        ctx.setFillColor(srgb(red, green, blue))
        ctx.fill(CGRect(x: 0, y: 0, width: size, height: size))
        return ctx.makeImage()!
    }

    /// 256 × 4, column x is grey x/255.
    static func greyRamp() -> CGImage {
        let ctx = context(width: 256, height: 4)
        for x in 0..<256 {
            let v = CGFloat(x) / 255
            ctx.setFillColor(srgb(v, v, v))
            ctx.fill(CGRect(x: x, y: 0, width: 1, height: 4))
        }
        return ctx.makeImage()!
    }

    static func render(_ params: FilterParams, on image: CGImage, masks: FilterMasks = .none) throws -> CGImage {
        let output = FilterPipeline.apply(params, to: CIImage(cgImage: image), masks: masks)
        return try #require(FilterPipeline.render(output, context: FilterPipeline.sharedContext, colorSpace: sRGB))
    }

    static func downscaled(_ image: CGImage, to size: Int) -> CGImage {
        let ctx = context(width: size, height: size)
        ctx.interpolationQuality = .high
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
        return ctx.makeImage()!
    }

    static func bytes(_ image: CGImage) throws -> [UInt8] {
        try #require(ReferenceImage.rgba(image))
    }

    /// Mean red, green, blue (0…1) over a region given y-down from the top-left.
    static func means(_ image: CGImage, x: Range<Int>? = nil, y: Range<Int>? = nil) throws -> [Double] {
        let rgba = try bytes(image)
        let xs = x ?? 0..<image.width, ys = y ?? 0..<image.height
        var sums = [0.0, 0.0, 0.0]
        for row in ys {
            for col in xs {
                let i = (row * image.width + col) * 4
                for c in 0..<3 { sums[c] += Double(rgba[i + c]) / 255 }
            }
        }
        let n = Double(xs.count * ys.count)
        return sums.map { $0 / n }
    }

    static func luma(_ rgb: [Double]) -> Double { 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2] }

    /// Standard deviation of luma over a region given y-down from the top-left.
    static func lumaStdDev(_ image: CGImage, x: Range<Int>? = nil, y: Range<Int>? = nil) throws -> Double {
        let rgba = try bytes(image)
        let xs = x ?? 0..<image.width, ys = y ?? 0..<image.height
        var lumas: [Double] = []
        for row in ys {
            for col in xs {
                let i = (row * image.width + col) * 4
                lumas.append(luma([Double(rgba[i]) / 255, Double(rgba[i + 1]) / 255, Double(rgba[i + 2]) / 255]))
            }
        }
        let mean = lumas.reduce(0, +) / Double(lumas.count)
        return (lumas.map { ($0 - mean) * ($0 - mean) }.reduce(0, +) / Double(lumas.count)).squareRoot()
    }

    /// Mean per-pixel chroma: max − min of red, green and blue, 0…1.
    static func meanChroma(_ image: CGImage) throws -> Double {
        let rgba = try bytes(image)
        var total = 0.0
        for i in stride(from: 0, to: rgba.count, by: 4) {
            let r = Double(rgba[i]), g = Double(rgba[i + 1]), b = Double(rgba[i + 2])
            total += (max(r, g, b) - min(r, g, b)) / 255
        }
        return total / Double(rgba.count / 4)
    }

    static func meanAbsDifference(_ a: CGImage, _ b: CGImage) throws -> Double {
        let x = try bytes(a), y = try bytes(b)
        try #require(x.count == y.count)
        let total = zip(x, y).reduce(0.0) { $0 + abs(Double($1.0) - Double($1.1)) }
        return total / Double(x.count) / 255
    }
}
