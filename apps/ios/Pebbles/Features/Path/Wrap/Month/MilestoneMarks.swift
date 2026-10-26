import os
import SwiftUI
import UIKit

/// A milestone's own stone (#1241 D16): the Spine's lit stone with its rings
/// drawn quieter, stamped in by the intro, and rippling once as the stamp
/// meets the paper, at the stone's own pace whatever the intro's tempo. A
/// card dealt settled, or revisited, does not ripple.
struct MilestoneStone: View {
    let moment: MomentSummary
    let palette: EmotionPalette?
    /// Its row in the month's milestones, for the intro's keys.
    let row: Int
    let scale: CGFloat

    @State private var press: StonePress?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The halo, the rings and the ripple, quieter than the Spine's.
    static let rings = 0.45

    var body: some View {
        let stone = SpineLayout.stoneSize(moment.pebble?.valence.sizeGroup ?? .medium)
        IntroReader { stage in
            let stamped = stage(.stamp(row))
            SpineStone(moment: moment, palette: palette, press: press, rings: Self.rings)
                .scaleEffect(scale * IntroMotion.stampScale(stamped))
                .opacity(IntroMotion.stampOpacity(stamped))
                .frame(width: stone.width * scale, height: stone.height * scale)
                .onChange(of: stage(.impact(row)) >= 1) { _, struck in
                    if struck { press = .next(after: press, still: reduceMotion, now: .now) }
                }
        }
        .stonePressLifetime($press)
    }
}

/// A milestone's photo as a mini polaroid (#1241 D16): the stock under a rule,
/// the photo in a window of one of three shapes, square, 3:4 or 4:3, the
/// nearest to its own (a 16:9 or 9:16 photo fills a 4:3 or 3:4 window,
/// cropped), a strip under it with two faint lines for a caption, a slight
/// clockwise tilt. The card keeps within a box `height` tall: a portrait photo
/// makes it narrow, a landscape one short. The ratio is the loaded photo's,
/// else the Spine's cache's, else square until it loads. It
/// loads as the Spine's card face does: the snap source's image, the shared
/// cache's, or its own fetch of the signed original; until then the window
/// is a bed in the pebble's tint. Decorative: the row says the milestone.
struct MilestoneSnap: View {
    let path: String
    /// The bed's ink while the photo loads.
    let tint: Color
    let height: CGFloat

    @Environment(\.spineSnapSource) private var snaps
    @Environment(SnapAspectCache.self) private var aspects: SnapAspectCache?
    @State private var fetched: (path: String, image: UIImage)?

    /// The box's width over its height: room for a landscape photo.
    private static let box: CGFloat = 1.15
    /// The strip under the photo, of the box's height.
    private static let strip: CGFloat = 0.24
    private static let tilt: Double = 4
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "wrap")

    var body: some View {
        let layout = Self.layout(height: height, ratio: ratio)
        let shape = RoundedRectangle(cornerRadius: max(2, height * 0.05), style: .continuous)
        VStack(spacing: 0) {
            photo
                .frame(width: layout.window.width, height: layout.window.height)
                .clipped()
            caption(width: layout.window.width, height: layout.strip)
        }
        .padding([.top, .horizontal], layout.margin)
        .frame(width: layout.card.width, height: layout.card.height, alignment: .top)
        .background { shape.fill(Color.pebbles.stock) }
        .overlay { shape.strokeBorder(Color.pebbles.cardRule, lineWidth: SpineLayout.rule) }
        .rotationEffect(.degrees(Self.tilt))
        .frame(width: height * Self.box, height: height)
        .task(id: path) { await load() }
        .accessibilityHidden(true)
    }

    /// The card's frames for a photo of `ratio` (width over height) in a box `height` tall.
    static func layout(height: CGFloat, ratio: CGFloat) -> MilestoneSnapLayout {
        let margin = max(2, height * 0.06)
        let strip = height * Self.strip
        let room = CGSize(width: height * box - 2 * margin, height: height - margin - strip)
        let safe = shape(ratio)
        let window = safe >= room.width / room.height
            ? CGSize(width: room.width, height: room.width / safe)
            : CGSize(width: room.height * safe, height: room.height)
        let card = CGSize(width: window.width + 2 * margin, height: margin + window.height + strip)
        return MilestoneSnapLayout(card: card, window: window, margin: margin, strip: strip)
    }

    /// The window's shape for a photo of `ratio`: square, 3:4 or 4:3, whichever is nearest; square while unknown.
    static func shape(_ ratio: CGFloat) -> CGFloat {
        guard ratio.isFinite, ratio > 0 else { return 1 }
        // The geometric midpoints between the three shapes.
        if ratio >= (4 / 3 as CGFloat).squareRoot() { return 4 / 3 }
        if ratio <= (3 / 4 as CGFloat).squareRoot() { return 3 / 4 }
        return 1
    }

    private var ratio: CGFloat {
        if let image = decoded, image.size.height > 0 { return image.size.width / image.size.height }
        return aspects?.ratio(for: path) ?? CardSizes.pendingRatio
    }

    @ViewBuilder
    private var photo: some View {
        if let image = decoded {
            Image(uiImage: image).resizable().scaledToFill()
        } else {
            Rectangle().fill(tint.opacity(0.18))
        }
    }

    /// Two faint lines where a caption would be written, the second shorter.
    private func caption(width: CGFloat, height: CGFloat) -> some View {
        let line = max(1, self.height * 0.018)
        return VStack(alignment: .leading, spacing: height * 0.18) {
            Capsule().fill(Color.pebbles.outlineVariant).frame(width: width * 0.72, height: line)
            Capsule().fill(Color.pebbles.outlineVariant).frame(width: width * 0.46, height: line)
        }
        .frame(width: width, height: height, alignment: .leading)
    }

    private var decoded: UIImage? {
        snaps.image(path) ?? SnapImageCache.shared.image(for: path) ?? (fetched?.path == path ? fetched?.image : nil)
    }

    /// Fetches the signed original once and keeps the decoded image in the shared cache, as the Spine's card does.
    private func load() async {
        guard decoded == nil else { return }
        // Unsigned: the cache logged why; the bed stays.
        guard let url = await snaps.url(path) else { return }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let image = await UIImage(data: data)?.byPreparingForDisplay() else {
                throw URLError(.cannotDecodeContentData)
            }
            SnapImageCache.shared.store(image, for: path)
            guard !Task.isCancelled else { return }
            fetched = (path, image)
        } catch {
            guard !Task.isCancelled else { return }
            Self.logger.warning("milestone photo failed: \(error.localizedDescription, privacy: .private)")
        }
    }
}

/// A milestone polaroid's frames: the card, the photo's window, the margin round it and the strip under it.
struct MilestoneSnapLayout: Equatable {
    let card: CGSize
    let window: CGSize
    let margin: CGFloat
    let strip: CGFloat
}
