import SwiftUI

/// The Deck card (#1130 R4–R5): a `SpineCardFace` on a `SpineCardShell`, sized
/// by `CardSizes` at the photo's ratio, `u = min(width / 70, 4.4)`; card text
/// scales through `SpineType`'s `.card(cap: 1.3)` (D11). Until the ratio is
/// known the field is square, then it eases to the ratio over 240 ms (snaps
/// under Reduce Motion).
///
/// No button of its own: `SpinePebble` wraps it. It reports its global frame to
/// `CardFrameRegistry` for stack E and hides while E flies it.
struct SpineCard: View {
    let moment: MomentSummary
    /// The emotion's palette; nil for a bare moment or an unknown emotion, which
    /// wear the blank or brand ramp and paint no edge (Android's `SpineCardShell`).
    let emotion: EmotionPalette?
    /// The photo's ratio once probed; nil holds the square.
    let ratio: CGFloat?
    let column: CGFloat
    let soulNames: [String: String]
    let soulGlyphs: [String: [GlyphStroke]]
    /// The persons-of-the-week order, so the strip names people as the page does.
    let soulOrder: [String]
    /// Up to this many souls are named in full: 3 on the Spine (R7), 8 on the held card (E).
    var namedInFull = 3

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(CardFrameRegistry.self) private var registry: CardFrameRegistry?
    @State private var shownRatio: CGFloat?
    /// The frame this slot last reported; a reference, so writing it on every
    /// scroll frame invalidates nothing.
    @State private var reported = ReportedFrame()

    init(moment: MomentSummary, emotion: EmotionPalette?, ratio: CGFloat?, column: CGFloat,
         soulNames: [String: String], soulGlyphs: [String: [GlyphStroke]], soulOrder: [String], namedInFull: Int = 3) {
        self.moment = moment
        self.emotion = emotion
        self.ratio = ratio
        self.column = column
        self.soulNames = soulNames
        self.soulGlyphs = soulGlyphs
        self.soulOrder = soulOrder
        self.namedInFull = namedInFull
        // A ratio already known draws at once (and under ImageRenderer); a later one eases.
        _shownRatio = State(initialValue: ratio)
    }

    private var intensity: Int { moment.pebble?.intensity ?? 2 }
    private var valence: Valence { moment.pebble?.valence ?? .neutralMedium }
    private var palette: StonePalette { SpineColors.palette(emotion, isBlank: moment.pebble == nil) }

    var body: some View {
        let size = CardSizes.cardSize(intensity: intensity, ratio: shownRatio, column: column)
        let u = min(size.width / 70, 4.4)
        SpineCardShell(emotion: emotion, polarity: valence.polarity, intensity: intensity) {
            SpineCardFace(moment: moment, palette: palette, ratio: shownRatio, width: size.width, u: u,
                          soulNames: soulNames, soulGlyphs: soulGlyphs, soulOrder: soulOrder, namedInFull: namedInFull)
        }
        .opacity(registry?.hidden == moment.id ? 0 : 1)
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frame in
            reported.frame = frame
            registry?.report(moment.id, frame: frame)
        }
        .onDisappear {
            guard let frame = reported.frame else { return }
            registry?.forget(moment.id, ifAt: frame)
            reported.frame = nil
        }
        .onChange(of: ratio) { _, ratio in
            guard let ratio, ratio != shownRatio else { return }
            if reduceMotion {
                shownRatio = ratio
            } else {
                withAnimation(.easeOut(duration: 0.24)) { shownRatio = ratio }
            }
        }
    }
}
