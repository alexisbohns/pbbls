import SwiftUI

/// The held card (#1146): a still shell (`SpineCardShell`), a panel that shows
/// the recto or the verso, and the still footer. Both faces are built and laid
/// out in one `FaceStack`, each `.compositingGroup()`, the one not in front at
/// opacity 0 and hidden from VoiceOver. In E1 the footer's button turns the
/// card at once; E2 adds the rotation and the drag.
struct PebbleCard: View {
    let detail: MomentDetail
    let pebble: MomentDetail.Pebble
    let palette: EmotionPalette?
    /// The photo's ratio once probed; nil holds the square.
    let ratio: CGFloat?
    let width: CGFloat
    let domains: [Domain]
    let collectionGlyphs: [UUID: Glyph]
    let soulGlyphs: [String: [GlyphStroke]]
    let soulOrder: [String]
    let turn: CardTurnModel

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let stone = SpineColors.palette(palette, isBlank: false)
        SpineCardShell(emotion: palette, polarity: pebble.valence.polarity, intensity: pebble.intensity) {
            VStack(spacing: 0) {
                panel
                    .frame(width: width)
                CardFooter(visibility: pebble.visibility, turnTo: turn.front.other,
                           stone: SpineColors.spot(stone, scheme: scheme), onTurn: { turn.turnInstantly() })
                    .frame(width: width)
            }
        }
        .accessibilityElement(children: .contain)
    }

    private var panel: some View {
        FaceStack {
            face(.recto) {
                CardRecto(detail: detail, pebble: pebble, palette: palette, ratio: ratio, width: width,
                          soulGlyphs: soulGlyphs, soulOrder: soulOrder)
            }
            face(.verso) {
                CardVerso(pebble: pebble, summary: MomentSummary(detail: detail), palette: palette, width: width,
                          domains: domains, collectionGlyphs: collectionGlyphs)
            }
        }
    }

    private func face<Content: View>(_ role: CardFace, @ViewBuilder _ content: () -> Content) -> some View {
        content()
            .compositingGroup()
            .opacity(turn.front == role ? 1 : 0)
            .accessibilityHidden(turn.front != role)
    }
}
