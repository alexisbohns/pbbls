import CoreGraphics
import Foundation
import SwiftUI

/// Painted lit stones, kept as images (#1690). A Path draws the same few
/// hundred stones on every scroll frame; painting one is a stack of blurred
/// layers, so each is painted once and the picture is reused.
///
/// Main-actor, because `ImageRenderer` is, and a stone paints synchronously on
/// its first request: a stone is a few milliseconds, and the view that asks
/// needs the picture in the same pass. The image is the stone's box plus its
/// shadow margin (D6), the margin rounded up to whole points so the box lands
/// on a whole pixel and sits where an unpadded paint draws it.
@MainActor
final class LitStoneCache {
    static let shared = LitStoneCache()

    private let storage = NSCache<Key, Entry>()

    init(countLimit: Int = 400) {
        storage.countLimit = countLimit
    }

    /// The stone's image, painted into the box plus `padding(_:box:)` on every side.
    func image(_ spec: LitStoneSpec, box: CGSize, scale: CGFloat) -> CGImage {
        let key = Self.key(spec, box: box, scale: scale)
        if let hit = storage.object(forKey: key) { return hit.image }
        let snapped = Self.snapped(box, scale: scale)
        let image = Self.paint(spec, box: snapped, scale: scale)
        storage.setObject(Entry(image), forKey: key)
        return image
    }

    /// The shadow margin in whole points, so the box starts on a whole pixel.
    static func padding(_ spec: LitStoneSpec, box: CGSize) -> EdgeInsets {
        let margin = LitStonePainter.shadowMargin(size: spec.size, box: box)
        return EdgeInsets(top: ceil(margin.top), leading: ceil(margin.leading),
                          bottom: ceil(margin.bottom), trailing: ceil(margin.trailing))
    }

    /// The key: the spec, the box in whole pixels, and the scale. Boxes that
    /// differ by less than a pixel share an entry, and are painted at the
    /// snapped size, so the entry does not depend on who asked first.
    static func key(_ spec: LitStoneSpec, box: CGSize, scale: CGFloat) -> Key {
        Key(spec: spec, widthPx: Int((box.width * scale).rounded()),
            heightPx: Int((box.height * scale).rounded()), scale: scale)
    }

    private static func snapped(_ box: CGSize, scale: CGFloat) -> CGSize {
        CGSize(width: (box.width * scale).rounded() / scale, height: (box.height * scale).rounded() / scale)
    }

    private static func paint(_ spec: LitStoneSpec, box: CGSize, scale: CGFloat) -> CGImage {
        let padding = padding(spec, box: box)
        let canvas = CGSize(width: padding.leading + box.width + padding.trailing,
                            height: padding.top + box.height + padding.bottom)
        let content = Canvas { context, _ in
            var context = context
            context.translateBy(x: padding.leading, y: padding.top)
            LitStonePainter.paint(spec, in: &context, box: box, scale: scale)
        }
        .frame(width: canvas.width, height: canvas.height)
        let renderer = ImageRenderer(content: content)
        renderer.scale = scale
        // The renderer only fails on an empty frame, which a stone's box never is.
        return renderer.cgImage ?? emptyImage
    }

    private static let emptyImage: CGImage = {
        let context = CGContext(data: nil, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                                space: CGColorSpaceCreateDeviceRGB(),
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        // A 1 x 1 RGBA context always exists; the image is transparent.
        return context!.makeImage()!
    }()

    /// `NSCache` wants class keys and values, with `hash` and `isEqual` agreeing.
    final class Key: NSObject {
        let spec: LitStoneSpec
        let widthPx: Int
        let heightPx: Int
        let scale: CGFloat

        init(spec: LitStoneSpec, widthPx: Int, heightPx: Int, scale: CGFloat) {
            self.spec = spec
            self.widthPx = widthPx
            self.heightPx = heightPx
            self.scale = scale
        }

        override var hash: Int {
            var hasher = Hasher()
            hasher.combine(spec)
            hasher.combine(widthPx)
            hasher.combine(heightPx)
            hasher.combine(scale)
            return hasher.finalize()
        }

        override func isEqual(_ object: Any?) -> Bool {
            guard let other = object as? Key else { return false }
            return spec == other.spec && widthPx == other.widthPx
                && heightPx == other.heightPx && scale == other.scale
        }
    }

    final class Entry {
        let image: CGImage
        init(_ image: CGImage) { self.image = image }
    }
}
