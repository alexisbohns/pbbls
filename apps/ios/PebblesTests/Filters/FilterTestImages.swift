import CoreGraphics
import CoreImage
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

    static func lumaStdDev(_ image: CGImage) throws -> Double {
        let rgba = try bytes(image)
        let lumas = stride(from: 0, to: rgba.count, by: 4).map { i in
            luma([Double(rgba[i]) / 255, Double(rgba[i + 1]) / 255, Double(rgba[i + 2]) / 255])
        }
        let mean = lumas.reduce(0, +) / Double(lumas.count)
        return (lumas.map { ($0 - mean) * ($0 - mean) }.reduce(0, +) / Double(lumas.count)).squareRoot()
    }

    static func meanAbsDifference(_ a: CGImage, _ b: CGImage) throws -> Double {
        let x = try bytes(a), y = try bytes(b)
        try #require(x.count == y.count)
        let total = zip(x, y).reduce(0.0) { $0 + abs(Double($1.0) - Double($1.1)) }
        return total / Double(x.count) / 255
    }
}
