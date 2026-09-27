import SwiftUI

/// One stone under the lab's light. Bottom to top: the wobbled backdrop
/// silhouette filled with the body tone and painted by the `stone` shader,
/// the carving (outline, veins, fossil, glyph) shaded by `carve`, and a sheen
/// pool masked by the silhouette. `isFlat` draws the feed rendering instead:
/// body tone and ink, no shader, no sheen.
///
/// Nothing here reads time. The view redraws when a parameter changes.
struct StoneView: View {
    let valence: Valence
    let palette: StonePalette
    let material: StoneMaterial
    let tones: StoneTones
    let light: StoneLight
    /// On-screen height of the whole stone, backdrop included.
    let height: CGFloat
    var isFlat: Bool = false
    var look: StoneLook = .standard

    @Environment(\.colorScheme) private var scheme

    private var size: ValenceSizeGroup { valence.sizeGroup }
    private var width: CGFloat { height * PebbleOutlineGeometry.aspectRatio(for: size) }
    /// A different crust per shape, stable across launches.
    private var seed: Int { Valence.allCases.firstIndex(of: valence).map { $0 * 31 + 7 } ?? 7 }

    var body: some View {
        ZStack {
            if !isFlat && look.shadowStrength > 0 {
                shadowLayer
            }
            bodyLayer
            if look.edgeWidth > 0 {
                edgeLayer
            }
            carvingLayer
                .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: size) * look.carvingScale)
            if !isFlat {
                sheenLayer
            }
        }
        .frame(width: width, height: height)
    }

    // MARK: - Body

    @ViewBuilder
    private var bodyLayer: some View {
        if let art = WobbleRenderer.backdropArt(size: size, polarity: valence.polarity) {
            let shape = WobbledBackdropShape(art: art)
            let fill = shape.fill(tones.color(tones.body, in: palette), style: FillStyle(eoFill: art.usesEvenOddFill))
            if isFlat {
                fill
            } else {
                // Points per viewBox unit for this on-screen size, so the
                // shader's grain is the same size on every stone.
                let unit = width / art.viewBox.width
                let rim = CGFloat(material.rimWidth)
                fill.layerEffect(
                    StoneShaders.stone(
                        material: material, tones: tones, palette: palette, scheme: scheme,
                        light: light, unit: unit, seed: seed
                    ),
                    maxSampleOffset: CGSize(width: rim, height: rim)
                )
            }
        } else {
            Color.clear
        }
    }

    // MARK: - Carving

    @ViewBuilder
    private var carvingLayer: some View {
        if let art = StoneCarvingArt.art(for: valence) {
            let ink = ZStack {
                if let fossil = art.fossil {
                    WobbledPathShape(path: fossil.path, layerTransform: .identity, viewBox: art.viewBox)
                        .fill(tones.color(tones.ink, in: palette), style: FillStyle(eoFill: fossil.usesEvenOddFill))
                }
                WobbledPathShape(path: art.ink, layerTransform: .identity, viewBox: art.viewBox)
                    .fill(tones.color(tones.ink, in: palette))
                if look.showOutline {
                    WobbledPathShape(path: art.outline, layerTransform: .identity, viewBox: art.viewBox)
                        .fill(tones.color(tones.ink, in: palette))
                }
            }
            .compositingGroup()
            if isFlat {
                ink
            } else {
                let lip = CGFloat(material.lipWidth)
                ink.layerEffect(
                    StoneShaders.carve(material: material, tones: tones, palette: palette, scheme: scheme, light: light),
                    maxSampleOffset: CGSize(width: lip, height: lip)
                )
            }
        }
    }

    // MARK: - Edge line

    /// The silhouette's own contour, stroked and pulled in by `edgeInset`, then
    /// carved like any other ink. Because it is the same wobbled path as the
    /// body, it follows the edge exactly, which the engine outline never could.
    @ViewBuilder
    private var edgeLayer: some View {
        if let art = WobbleRenderer.backdropArt(size: size, polarity: valence.polarity) {
            let line = WobbledBackdropShape(art: art)
                .stroke(tones.color(tones.ink, in: palette), style: StrokeStyle(lineWidth: look.edgeWidth, lineCap: .round, lineJoin: .round))
                .scaleEffect(1 - look.edgeInset)
                .compositingGroup()
            if isFlat {
                line
            } else {
                let lip = CGFloat(material.lipWidth)
                line.layerEffect(
                    StoneShaders.carve(material: material, tones: tones, palette: palette, scheme: scheme, light: light),
                    maxSampleOffset: CGSize(width: lip, height: lip)
                )
            }
        }
    }

    // MARK: - Contact shadow

    /// A blurred copy of the silhouette pushed away from the light, so the
    /// stone sits on the page. Static: rendered once per parameter change.
    @ViewBuilder
    private var shadowLayer: some View {
        if let art = WobbleRenderer.backdropArt(size: size, polarity: valence.polarity) {
            let push = light.lipOffset(width: -Double(height) * 0.03)
            WobbledBackdropShape(art: art)
                .fill(Color.black.opacity(look.shadowStrength), style: FillStyle(eoFill: art.usesEvenOddFill))
                .blur(radius: height * 0.03)
                .offset(x: push.width, y: push.height)
        }
    }

    // MARK: - Sheen

    @ViewBuilder
    private var sheenLayer: some View {
        if let art = WobbleRenderer.backdropArt(size: size, polarity: valence.polarity) {
            let center = light.poolCenter
            RadialGradient(
                colors: [Color.white.opacity(0.9), Color.white.opacity(0.25), .clear],
                center: UnitPoint(x: center.x, y: center.y),
                startRadius: 0,
                endRadius: width * 0.6
            )
            .blendMode(.screen)
            .opacity(material.sheen)
            .mask {
                WobbledBackdropShape(art: art)
                    .fill(style: FillStyle(eoFill: art.usesEvenOddFill))
            }
        }
    }
}

#Preview("Nine stones · brand") {
    let palette = StonePalette.brand
    return VStack(spacing: 12) {
        ForEach(ValenceSizeGroup.allCases) { size in
            HStack(spacing: 12) {
                ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                    let valence = Valence.allCases.first { $0.sizeGroup == size && $0.polarity == polarity }!
                    StoneView(
                        valence: valence, palette: palette,
                        material: .starting(for: polarity), tones: .starting(for: polarity),
                        light: .rest, height: 110
                    )
                }
            }
        }
    }
    .padding()
    .background(Color.system.background)
}
