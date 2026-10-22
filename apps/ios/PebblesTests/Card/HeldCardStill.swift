import SwiftUI
@testable import Pebbles

/// The held card without a `ScrollView` (the B2 harness cannot render one), on
/// the page's ground at 393 pt: the card, optionally under its top bar. The ⋮
/// list is a popover `ImageRenderer` cannot draw; the menu is checked on the device.
struct HeldCardStill: View {
    let detail: MomentDetail
    var face: CardFace = .recto
    var palette: EmotionPalette? = HeldCardFixtures.joy
    var showsBar = false
    var width: CGFloat = ReferenceConfig.phoneWidth

    var body: some View {
        VStack(alignment: .trailing, spacing: 8) {
            if showsBar {
                CardTopBar(shareURL: shareURL, menuItems: menu, isDisabled: false, onBack: {})
            }
            if let pebble = detail.pebble {
                PebbleCard(detail: detail, pebble: pebble, palette: palette,
                           ratio: detail.snaps.first.flatMap { HeldCardFixtures.ratio($0.storagePath) },
                           width: CardLayout.cardWidth(available: width), domains: HeldCardFixtures.domains,
                           collectionGlyphs: HeldCardFixtures.collectionGlyphs, soulGlyphs: HeldCardFixtures.soulGlyphs,
                           soulOrder: [], turn: CardTurnModel(face: face))
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.vertical, 24)
        .frame(width: width)
        .background(Color.pebbles.surface)
        .environment(\.spineSnapSource, SpineFixturePhotos.source)
    }

    /// A public pebble's share link; the share button shows for no other grade.
    private var shareURL: URL? {
        detail.pebble.flatMap { $0.visibility == .public ? SharedPebbleLink.url(for: $0.id) : nil }
    }

    private var menu: [PebblesMenuItem] {
        HeldCardMenu.items(hasPebble: detail.pebble != nil, editMoment: {}, editPebble: {}, delete: {})
    }
}
