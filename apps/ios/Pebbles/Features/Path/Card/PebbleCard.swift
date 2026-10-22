import SwiftUI

/// The held card (#1146): a still shell (`SpineCardShell`), a panel that turns,
/// and the still footer. Both faces are built and laid out in one `FaceStack`,
/// each `.compositingGroup()`; which is opaque is decided per frame from the
/// animated progress (`CardFaceVisibility`), and only the one in front speaks
/// to VoiceOver. The panel alone rotates on Y (`CardTurnEffect`).
///
/// Ways to turn: the footer's button (a 0.42 s spring, no bounce), and a
/// horizontal pan on the panel (`TurnPanFallback`, a `UIPanGestureRecognizer`
/// that leaves vertical drags to the scroll) measured in its unrotated parent
/// (a flick carries, a release past the half completes, `TurnSettle`). For VoiceOver the
/// footer's button is the turn; the card has no custom action. Under Reduce
/// Motion the faces crossfade in 0.15 s and a drag commits only on release.
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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection
    private var rightToLeft: Bool { layoutDirection == .rightToLeft }
    /// Reduce Motion never rotates: the progress the faces see stays 0.
    private var shownProgress: Double { reduceMotion ? 0 : turn.progress }

    var body: some View {
        let stone = SpineColors.palette(palette, isBlank: false)
        SpineCardShell(emotion: palette, polarity: pebble.valence.polarity, intensity: pebble.intensity) {
            VStack(spacing: 0) {
                panel
                    .frame(width: width)
                    .modifier(CardTurnEffect(progress: shownProgress, rightToLeft: rightToLeft))
                    .overlay {
                        TurnPanFallback(
                            onBegan: { turn.startDrag() },
                            onChanged: { dx in
                                turn.drag(to: TurnSettle.progress(drag: dx, width: width, rightToLeft: rightToLeft),
                                          reduceMotion: reduceMotion)
                            },
                            onEnded: { dx, vx in release(translation: dx, velocity: vx) },
                            onCancelled: { if turn.cancelDrag() { settleBack() } }
                        )
                    }
                TurnFooter(progress: shownProgress, face: turn.face, visibility: pebble.visibility,
                           stone: SpineColors.spot(stone, scheme: scheme), onTurn: turnByButton)
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
            .modifier(CardFaceVisibility(progress: shownProgress, isCommitted: turn.face == role))
            .accessibilityHidden(turn.front != role)
    }

    // MARK: - The button

    private func turnByButton() {
        if reduceMotion {
            // The outgoing face is hidden from VoiceOver at once; the picture crossfades.
            withAnimation(.easeInOut(duration: 0.15)) { _ = turn.beginTurn(reduceMotion: true) }
            return
        }
        guard turn.beginTurn(reduceMotion: false) else { return }
        withAnimation(.spring(duration: 0.42, bounce: 0), completionCriteria: .logicallyComplete) {
            turn.progress = 1
        } completion: {
            land()
        }
    }

    /// The landing commits the other face at 0 with no animation, so no frame
    /// shows the new face at 180°.
    private func land() {
        withTransaction(Transaction(animation: nil)) { turn.settled() }
    }

    // MARK: - The drag

    private func release(translation: CGFloat, velocity: CGFloat) {
        let at = TurnSettle.progress(drag: translation, width: width, rightToLeft: rightToLeft)
        let turns = TurnSettle.turnsPerSecond(velocity: velocity, width: width, rightToLeft: rightToLeft)
        if reduceMotion {
            withAnimation(.easeInOut(duration: 0.15)) { _ = turn.release(at: at, velocity: turns, reduceMotion: true) }
            return
        }
        guard let settle = turn.release(at: at, velocity: turns, reduceMotion: false) else { return }
        withAnimation(.interpolatingSpring(duration: 0.35, bounce: 0, initialVelocity: settle.springVelocity),
                      completionCriteria: .logicallyComplete) {
            turn.progress = settle.target
        } completion: {
            land()
        }
    }

    /// A drag taken away (the scroll took it, the page left) reverts.
    private func settleBack() {
        withAnimation(.spring(duration: 0.21, bounce: 0), completionCriteria: .logicallyComplete) {
            turn.progress = 0
        } completion: {
            land()
        }
    }
}

/// The still footer for the turn: it names the face not in front and switches
/// at the half, with the picture, because it reads the animated progress
/// (`Animatable`), so a drag held past the half offers the turn back.
private struct TurnFooter: View, Animatable {
    var progress: Double
    let face: CardFace
    let visibility: Visibility
    let stone: Color
    let onTurn: () -> Void

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        let front = progress < TurnSettle.half ? face : face.other
        CardFooter(visibility: visibility, turnTo: front.other, stone: stone, onTurn: onTurn)
    }
}
