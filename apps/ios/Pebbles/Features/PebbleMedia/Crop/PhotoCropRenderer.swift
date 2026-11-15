import CoreGraphics
import Foundation

enum PhotoCropRendererError: Error { case contextFailed }

/// Draws the crop window's content (spec "Rendering on Done") from the working
/// image. Pure: no UIKit, no logging. The output is at most 1024 px on the long
/// edge and never more pixels than the window covers in the source.
enum PhotoCropRenderer {
    static let maxOutputEdge: CGFloat = 1024

    static func outputSize(for g: CropGeometry) -> CGSize {
        let window = g.windowSize
        let longWindow = max(window.width, window.height)
        let sourcePixels = longWindow / g.scale
        let longEdge = min(maxOutputEdge, sourcePixels.rounded())
        let perUnit = longEdge / longWindow
        return CGSize(width: (window.width * perUnit).rounded(), height: (window.height * perUnit).rounded())
    }

    static func render(_ image: CGImage, geometry g: CropGeometry) throws -> CGImage {
        let size = outputSize(for: g)
        let width = Int(size.width), height = Int(size.height)
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
            throw PhotoCropRendererError.contextFailed
        }
        ctx.interpolationQuality = .high
        let perUnit = size.height / g.windowSize.height

        // y-down, window centre at the origin, window units.
        ctx.translateBy(x: 0, y: size.height)
        ctx.scaleBy(x: 1, y: -1)
        ctx.translateBy(x: size.width / 2, y: size.height / 2)
        ctx.scaleBy(x: perUnit, y: perUnit)
        // Same chain as the canvas: offset, turn, scale.
        ctx.translateBy(x: g.windowOffset.width, y: g.windowOffset.height)
        ctx.rotate(by: g.theta)
        ctx.scaleBy(x: g.scale, y: g.scale)
        // Image pixels from the image centre, y-down. CGContext.draw puts a
        // picture's top row at the rect's max y, so flip once more locally.
        let w = CGFloat(image.width), h = CGFloat(image.height)
        ctx.translateBy(x: -w / 2, y: h / 2)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))

        guard let output = ctx.makeImage() else { throw PhotoCropRendererError.contextFailed }
        return output
    }
}
