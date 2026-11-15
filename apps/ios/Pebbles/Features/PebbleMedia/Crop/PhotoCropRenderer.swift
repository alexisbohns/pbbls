import CoreGraphics
import Foundation

enum PhotoCropRendererError: Error { case contextFailed, sizeMismatch }

/// Draws the crop window's content (spec "Rendering on Done") from the working
/// image. Pure: no UIKit, no logging. The output is at most 1024 px on the long
/// edge and never more pixels than the window covers in the source.
enum PhotoCropRenderer {
    static let maxOutputEdge: CGFloat = 1024

    /// The photo is drawn this much larger than the geometry says, about the
    /// window centre: it overfills the window by a hair so antialiasing never
    /// blends an edge pixel with the empty context behind it.
    static let overfill: CGFloat = 1.002

    static func outputSize(for g: CropGeometry) -> CGSize {
        let window = g.windowSize
        let longWindow = max(window.width, window.height)
        let sourcePixels = longWindow / g.scale
        let longEdge = max(1, min(maxOutputEdge, sourcePixels.rounded()))
        let perUnit = longEdge / longWindow
        return CGSize(width: max(1, (window.width * perUnit).rounded()),
                      height: max(1, (window.height * perUnit).rounded()))
    }

    static func render(_ image: CGImage, geometry g: CropGeometry) throws -> CGImage {
        guard g.imageSize == CGSize(width: image.width, height: image.height) else {
            throw PhotoCropRendererError.sizeMismatch
        }
        let size = outputSize(for: g)
        let width = Int(size.width), height = Int(size.height)
        guard let space = ImagePipeline.drawingColorSpace(for: image),
              let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
            throw PhotoCropRendererError.contextFailed
        }
        ctx.interpolationQuality = .high

        // y-down, window centre at the origin, window units.
        ctx.translateBy(x: 0, y: size.height)
        ctx.scaleBy(x: 1, y: -1)
        ctx.translateBy(x: size.width / 2, y: size.height / 2)
        // The rounded output is not exactly the window's shape, so x and y
        // scale on their own and the window still fills it edge to edge.
        ctx.scaleBy(x: size.width / g.windowSize.width, y: size.height / g.windowSize.height)
        // Same chain as the canvas: offset, turn, scale.
        ctx.translateBy(x: g.windowOffset.width, y: g.windowOffset.height)
        ctx.rotate(by: g.theta)
        ctx.scaleBy(x: g.scale, y: g.scale)
        ctx.translateBy(x: g.focus.x, y: g.focus.y)
        ctx.scaleBy(x: overfill, y: overfill)
        ctx.translateBy(x: -g.focus.x, y: -g.focus.y)
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
