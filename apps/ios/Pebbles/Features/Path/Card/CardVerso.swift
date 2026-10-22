import SwiftUI

/// The verso (#1146 §Verso): the emotion's mat full-bleed round a stock panel,
/// and in it the valence lockup, the emotion once under a hairline, the lit
/// stone centred in the room left, and the domains as postmarks; the collection
/// band wraps across its foot. Given the face's height (`FaceStack` proposes
/// the taller face's), the stone centres between the emotion and the postmarks.
///
/// VoiceOver: "Your pebble" (a heading, whose value is the valence, "Highlight
/// of my month"), the emotion, the stone, the domains, the collections.
struct CardVerso: View {
    let pebble: MomentDetail.Pebble
    /// The moment as the Spine reads it, for the stone's description.
    let summary: MomentSummary
    let palette: EmotionPalette?
    let width: CGFloat
    let domains: [Domain]
    let collectionGlyphs: [UUID: Glyph]
    /// The print's timeline (E3); nil at rest.
    var print: Double?
    /// The verso stone's press, played by the print (E3).
    var stonePress: StonePress?

    /// One environment for the ramp's mix and its scheme, so both agree.
    @Environment(\.self) private var environment

    private var scheme: ColorScheme { environment.colorScheme }
    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        let u = CardLayout.u(cardWidth: width)
        let stone = SpineColors.palette(palette, isBlank: false)
        let spot = SpineColors.spot(stone, scheme: scheme)
        let foot = CardLayout.bandFoot(u: u)
        // The panel's padding less the row's outset, so four stamps keep their size on a phone.
        let side = foot + 1.4 * u
        VersoStack(top: foot + 3.6 * u, side: side, foot: foot, bareFoot: 4.4 * u) {
            VStack(spacing: 0) {
                lockup(stone, u: u)
                Rectangle()
                    .fill(Color.pebbles.cardRule)
                    .frame(width: 24, height: 1)
                    .padding(.top, 2.2 * u)
                    .padding(.bottom, 2 * u)
                    .accessibilityHidden(true)
                Text(verbatim: pebble.emotion.localizedName)
                    .spineType(.cardEmotion)
                    .foregroundStyle(Color.pebbles.onSurface)
                Spacer(minLength: 0)
                clearing
                Spacer(minLength: 0)
                PostmarkRow(domains: pebble.domains, references: domains,
                            ink: stone.color(CardColors.postmarkInkSlot(scheme: scheme)),
                            paper: Color.pebbles.stock, rowWidth: width - 2 * side)
                    .opacity(print.map { PrintPart.postmarks.alpha(CompletionMotion.print($0)) } ?? 1)
            }
            if !pebble.collections.isEmpty {
                CollectionBand(collections: pebble.collections, glyphs: collectionGlyphs, spot: spot)
                    .opacity(print.map { PrintPart.band.alpha(CompletionMotion.print($0)) } ?? 1)
            }
        }
        .background { VersoFrame(polarity: pebble.valence.polarity, spot: spot, u: u) }
        .accessibilityElement(children: .contain)
    }

    private var soulNames: [String: String] {
        Dictionary(pebble.souls.map { ($0.id.uuidString.lowercased(), $0.name) },
                   uniquingKeysWith: { first, _ in first })
    }

    private func lockup(_ stone: StonePalette, u: CGFloat) -> some View {
        let lockup = ValenceLockup.of(pebble.valence)
        let word = SpineType.Style.lockupWord(lockup.rank.sizeGroup)
        return VStack(spacing: 0) {
            Text(lockup.word)
                .spineType(word)
                .foregroundStyle(ink(lockup.ink, stone))
                .padding(.horizontal, word.inkOverhang)
            Text(lockup.span)
                .spineType(.cardLockupSpan)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(CardStrings.versoHeading))
        .accessibilityValue(Text(lockup.word) + Text(verbatim: " ") + Text(lockup.span))
        .accessibilityAddTraits(.isHeader)
        .opacity(print.map { PrintPart.lockup.alpha(CompletionMotion.print($0)) } ?? 1)
    }

    private func ink(_ ink: LockupInk, _ stone: StonePalette) -> AnyShapeStyle {
        switch ink {
        case .stoneRamp:
            return AnyShapeStyle(LinearGradient(stops: CardColors.highlightRamp(stone, scheme: scheme, in: environment),
                                                startPoint: .top, endPoint: .bottom))
        case .ink:
            return AnyShapeStyle(Color.pebbles.onSurface)
        case .primary:
            return AnyShapeStyle(Color.pebbles.primary)
        }
    }

    /// The stone's clearing: room above and below for the rings and the halo
    /// (0.3 of the stone's width), so the halo never covers the emotion.
    private var clearing: some View {
        let stone = CardLayout.versoStoneSize(pebble.valence.sizeGroup)
        return VersoStone(valence: pebble.valence, glyph: pebble.glyph, palette: palette,
                          seed: StableHash.seed(pebble.id), press: stonePress)
            .padding(.vertical, stone.width * 0.3)
            .opacity(print.map { CompletionMotion.litStoneAlpha(CompletionMotion.stone($0)) } ?? 1)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: SpineStoneDescription.text(
                summary, names: soulNames, order: [], calendar: SpineCalendar.current, locale: locale,
                dateLocale: SpineStrings.dateLocale)))
            .anchorPreference(key: PrintAnchors.self, value: .bounds) { [.versoStone: $0] }
    }
}

/// The verso's frame, behind its content: the mat at 1.6u with its print and
/// keyline, and the stock panel `bandFoot` in with its keyline and inner rule.
/// The panel runs to the band's foot, so a band lies over it; with no band its
/// foot shows.
struct VersoFrame: View {
    let polarity: ValencePolarity
    let spot: Color
    let u: CGFloat

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let mat = RoundedRectangle(cornerRadius: 1.3 * u, style: .continuous)
        let panel = RoundedRectangle(cornerRadius: 1 * u, style: .continuous)
        ZStack {
            GeometryReader { proxy in
                SpineCardField(polarity: polarity, spot: spot, tint: SpineColors.tintK(scheme: scheme),
                               pattern: SpineColors.patternK(scheme: scheme), size: proxy.size)
            }
            .clipShape(mat)
            .overlay { mat.strokeBorder(spot.opacity(CardColors.matRuleAlpha), lineWidth: 1) }
            .padding(CardLayout.versoMatUnits * u)
            panel
                .fill(Color.pebbles.stock)
                .overlay { panel.strokeBorder(spot.opacity(CardColors.panelRuleAlpha), lineWidth: 1) }
                .overlay {
                    RoundedRectangle(cornerRadius: 0.8 * u, style: .continuous)
                        .strokeBorder(Color.pebbles.cardRule, lineWidth: 1)
                        .padding(1.1 * u)
                }
                .padding(CardLayout.bandFoot(u: u))
        }
        .accessibilityHidden(true)
    }
}

/// The verso's two parts in one pass: the content column (subview 0), proposed
/// the face's height less the panel's padding and the foot, so its spacers
/// centre the stone; the band (subview 1, optional) at full width, its foot on
/// the panel's foot line. With a band the postmarks keep 18 pt above it.
struct VersoStack: Layout {
    let top: CGFloat
    let side: CGFloat
    /// From the face's foot to the band's: `CardLayout.bandFoot(u:)`.
    let foot: CGFloat
    /// Below the content when there is no band.
    let bareFoot: CGFloat

    private func bandHeight(width: CGFloat, subviews: Subviews) -> CGFloat? {
        guard subviews.count > 1 else { return nil }
        return subviews[1].sizeThatFits(ProposedViewSize(width: width, height: nil)).height
    }

    private func contentFoot(_ band: CGFloat?) -> CGFloat {
        band.map { foot + $0 + CardLayout.postmarkRowToBand } ?? foot + bareFoot
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let content = subviews.first else { return .zero }
        let width = proposal.width ?? 0
        if let height = proposal.height, height.isFinite { return CGSize(width: width, height: height) }
        let natural = content.sizeThatFits(ProposedViewSize(width: max(0, width - 2 * side), height: nil))
        let band = bandHeight(width: width, subviews: subviews)
        return CGSize(width: width, height: top + natural.height + contentFoot(band))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard let content = subviews.first else { return }
        let band = bandHeight(width: bounds.width, subviews: subviews)
        content.place(at: CGPoint(x: bounds.minX + side, y: bounds.minY + top), anchor: .topLeading,
                      proposal: ProposedViewSize(width: max(0, bounds.width - 2 * side),
                                                 height: max(0, bounds.height - top - contentFoot(band))))
        if let band {
            subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.maxY - foot - band), anchor: .topLeading,
                              proposal: ProposedViewSize(width: bounds.width, height: band))
        }
    }
}
