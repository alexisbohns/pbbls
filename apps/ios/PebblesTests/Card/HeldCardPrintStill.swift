import SwiftUI
@testable import Pebbles

/// The bare page alone, or the print frozen at `t`, at 393 pt without a
/// `ScrollView`: the page is F4's opening and place; the card F1's, verso up.
struct HeldCardPrintStill: View {
    var bare: MomentDetail = HeldCardFixtures.f4
    var t: Double?

    var body: some View {
        let width = CardLayout.cardWidth(available: ReferenceConfig.phoneWidth)
        let ratio = bare.snaps.first.flatMap { HeldCardFixtures.ratio($0.storagePath) }
        let page = BareMomentPage(detail: bare, ratio: ratio, onComplete: {})
        Group {
            if let t, let pebble = HeldCardFixtures.f1.pebble {
                CardPrint(t: t, crossfade: false, page: page,
                          card: PebbleCard(detail: HeldCardFixtures.f1, pebble: pebble, palette: HeldCardFixtures.joy,
                                           ratio: HeldCardFixtures.ratio(HeldCardFixtures.fountain), width: width,
                                           domains: HeldCardFixtures.domains,
                                           collectionGlyphs: HeldCardFixtures.collectionGlyphs,
                                           soulGlyphs: HeldCardFixtures.soulGlyphs, soulOrder: [],
                                           turn: CardTurnModel(face: .verso), turnByDrag: false, print: t))
            } else {
                page
            }
        }
        .padding(.vertical, 24)
        .frame(width: ReferenceConfig.phoneWidth)
        .background(Color.pebbles.surface)
        .environment(\.spineSnapSource, SpineFixturePhotos.source)
    }
}
