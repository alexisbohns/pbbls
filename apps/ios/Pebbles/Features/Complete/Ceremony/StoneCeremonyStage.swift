import SwiftUI

/// The stone reveal ceremony's stone (#1691), drawn over the success step at one frame:
/// bottom to top, the hero waves, the lift shadow, the stone, its glint, the landing waves.
/// `centre` and `cell` are the hero slot and the landing slot in the stage's own space,
/// `bounds` its size. It takes no touch and says nothing to VoiceOver: the step does both.
///
/// No rings at rest, ever: only the ripple waves (`StoneRipple`) and the glint (`StoneGlint`).
/// Every resting stone is a native paint at its own size (D3); a bitmap is scaled only in flight.
struct StoneCeremonyStage: View {
    let spec: LitStoneSpec
    let valence: Valence
    let frame: StoneCeremonyFrame
    let centre: CGRect
    let cell: CGRect
    let bounds: CGSize

    init(spec: LitStoneSpec, valence: Valence, frame: StoneCeremonyFrame, centre: CGRect, cell: CGRect,
         bounds: CGSize) {
        self.spec = spec
        self.valence = valence
        self.frame = frame
        self.centre = centre
        self.cell = cell
        self.bounds = bounds
    }

    private var hero: CGSize { StoneCeremonyLayout.heroSize(spec.size) }
    private var landing: CGSize { StoneCeremonyLayout.landingSize(spec.size) }

    /// The press's waves, sweep and spark follow the stone: Act 1's are the hero's, at the centre;
    /// once the flight has begun they are the landing's, at the cell. The landing waves only start
    /// once the flight has ended, so none is drawn in between.
    private var atCell: Bool { frame.flight > 0 }

    /// The live carve is drawn until the ink has fully set (D2); then the cached carved image.
    private var carving: Bool { frame.lipTrim < 1 || frame.inkTrim < 1 || frame.inkOpacity < 1 }

    var body: some View {
        let rect = StoneCeremonyLayout.flightRect(from: centre, to: cell, progress: frame.flight)
        ZStack(alignment: .topLeading) {
            heroWaves
            LitStoneLiftShadow(spec: spec, lift: frame.lift)
                .frame(width: rect.width, height: rect.height)
                .scaleEffect(frame.stoneScale)
                .position(x: rect.midX, y: rect.midY)
            stone(in: rect)
            glint(in: rect)
            landingWaves
        }
        .frame(width: bounds.width, height: bounds.height, alignment: .topLeading)
        .opacity(frame.stoneOpacity)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: Layers

    /// Trimmed to the stage: they reach for the screen's edges (`heroWaveReach`).
    @ViewBuilder
    private var heroWaves: some View {
        if !atCell, !frame.press.waves.isEmpty, let geometry = StoneRingGeometry.forStone(valence, in: hero) {
            Color.clear
                .overlay(alignment: .topLeading) {
                    StoneRipple(geometry: geometry, stone: hero, waves: frame.press.waves,
                                reach: StoneCeremonyLayout.heroWaveReach(screenWidth: bounds.width,
                                                                         heroWidth: hero.width, wave: 0))
                        .position(x: centre.midX, y: centre.midY)
                }
                .frame(width: bounds.width, height: bounds.height)
                .clipped()
        }
    }

    @ViewBuilder
    private var landingWaves: some View {
        if atCell, !frame.press.waves.isEmpty, let geometry = StoneRingGeometry.forStone(valence, in: landing) {
            StoneRipple(geometry: geometry, stone: landing, waves: frame.press.waves,
                        reach: StoneMotion.Wave.reach[spec.size] ?? 5.6)
                .position(x: cell.midX, y: cell.midY)
        }
    }

    /// The hero image stays in the tree from the first frame, so the cache has painted it before
    /// the carve hands over to it; the landing image is already painted by the week row.
    private func stone(in rect: CGRect) -> some View {
        let landed = StoneCeremonyLayout.flightCrossfade(frame.flight)
        return ZStack(alignment: .topLeading) {
            if carving {
                ZStack {
                    LitStoneImage(spec: spec.uncarved)
                    LitStoneCarve(spec: spec, lip: frame.lipTrim, ink: frame.inkTrim, inkOpacity: frame.inkOpacity)
                }
                .frame(width: hero.width, height: hero.height)
                .scaleEffect(frame.stoneScale * rect.width / hero.width)
                .position(x: rect.midX, y: rect.midY)
            }
            LitStoneImage(spec: spec)
                .frame(width: hero.width, height: hero.height)
                .scaleEffect(frame.stoneScale * rect.width / hero.width)
                .opacity(carving ? 0 : 1 - landed)
                .position(x: rect.midX, y: rect.midY)
            if landed > 0 {
                LitStoneImage(spec: spec)
                    .frame(width: landing.width, height: landing.height)
                    .scaleEffect(frame.stoneScale * rect.width / landing.width)
                    .opacity(landed)
                    .position(x: rect.midX, y: rect.midY)
            }
        }
    }

    /// At rest at either end only: the closure spark at the hero, the sweep and spark at the cell.
    @ViewBuilder
    private func glint(in rect: CGRect) -> some View {
        let size = atCell ? landing : hero
        if frame.flight == 0 || frame.flight >= 1, let geometry = StoneRingGeometry.forStone(valence, in: size) {
            StoneGlint(outline: geometry.outline, stone: size, frame: frame.press,
                       strength: StoneGlintInk.strength(valence.polarity))
                .scaleEffect(frame.stoneScale)
                .position(x: rect.midX, y: rect.midY)
        }
    }
}
