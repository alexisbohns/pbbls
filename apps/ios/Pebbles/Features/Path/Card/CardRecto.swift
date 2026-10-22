import SwiftUI

/// The recto (#1146 §Recto): the Spine card at the held card's width (a uniform
/// scale of the Spine card, `u = width / 70`), every soul named up to eight, the
/// photo at its own ratio, and the leaf with the full dateline and the words. A
/// pebble with no photo shows `CardSlip` in place of the photo (F3). The field
/// eases to the photo's ratio once probed, as the Spine card's does.
struct CardRecto: View {
    let detail: MomentDetail
    let pebble: MomentDetail.Pebble
    let palette: EmotionPalette?
    let ratio: CGFloat?
    let width: CGFloat
    let soulGlyphs: [String: [GlyphStroke]]
    /// The Spine's persons-of-the-week order; `[]` keeps the pebble's own.
    let soulOrder: [String]

    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shownRatio: CGFloat?

    init(detail: MomentDetail, pebble: MomentDetail.Pebble, palette: EmotionPalette?, ratio: CGFloat?,
         width: CGFloat, soulGlyphs: [String: [GlyphStroke]], soulOrder: [String]) {
        self.detail = detail
        self.pebble = pebble
        self.palette = palette
        self.ratio = ratio
        self.width = width
        self.soulGlyphs = soulGlyphs
        self.soulOrder = soulOrder
        _shownRatio = State(initialValue: ratio)
    }

    private var locale: Locale { SpineStrings.appLocale }
    private var stone: StonePalette { SpineColors.palette(palette, isBlank: false) }

    private var soulNames: [String: String] {
        Dictionary(pebble.souls.map { ($0.id.uuidString.lowercased(), $0.name) },
                   uniquingKeysWith: { first, _ in first })
    }

    var body: some View {
        let u = CardLayout.u(cardWidth: width)
        // The time in the device's hour cycle, as the Spine's rail prints it (`dateLocale`).
        let dateline = CardDateline.text(detail.happenedAt, calendar: SpineCalendar.current,
                                         locale: SpineStrings.dateLocale)
        SpineCardFace(moment: MomentSummary(detail: detail), palette: stone, ratio: shownRatio, width: width, u: u,
                      soulNames: soulNames, soulGlyphs: soulGlyphs, soulOrder: soulOrder,
                      namedInFull: PeopleLines.namedOnCard, accessible: true,
                      slip: detail.snaps.isEmpty ? AnyView(slip(u: u)) : nil,
                      leaf: AnyView(CardLeaf(dateline: dateline,
                                             datelineLabel: dateline + ", " + pebble.emotion.localizedName,
                                             words: detail.description, u: u)))
            .accessibilityElement(children: .contain)
            .task(id: ratio) {
                guard let ratio, ratio != shownRatio else { return }
                if reduceMotion {
                    shownRatio = ratio
                } else {
                    withAnimation(.easeOut(duration: 0.24)) { shownRatio = ratio }
                }
            }
    }

    private func slip(u: CGFloat) -> some View {
        let name = detail.name.flatMap { $0.isEmpty ? nil : $0 } ?? SpineCopy.untitled(locale: locale)
        let cut = EntryTypes.entryType(intensity: pebble.intensity, valence: pebble.valence.positiveness).name
        return CardSlip(name: name, cut: cut, spot: SpineColors.spot(stone, scheme: scheme), u: u)
    }
}
