import SwiftUI

/// A guide's picture (spec §1.5), drawn with the real parts the reading is
/// about, as the checklist's pictures are (`ChecklistIllustration`): seals,
/// soul marks, a glyph, the collections row, day dots. Two sizes: `mark`, a
/// 24 pt sign beside the row's title, and `page`, the 120 pt band under the
/// summary. No colour but what those parts carry. Decorative: the title
/// beside it says what it is.
struct GuideIllustrationView: View {
    enum Size { case mark, page }

    let kind: GuideIllustration?
    let size: Size
    /// One stone palette per emotion family, in the families' order; the
    /// `.emotions` seals wear them when given, else the brand ramp.
    var familyPalettes: [StonePalette] = []

    static let markSide: CGFloat = 24
    static let pageHeight: CGFloat = 120

    var body: some View {
        if let kind {
            Group {
                switch size {
                case .mark:
                    GuideMark(kind: kind)
                        // Clamped so the karma mark's "+1" cannot outgrow the square.
                        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                        .frame(width: Self.markSide, height: Self.markSide)
                case .page:
                    GuidePagePicture(kind: kind, familyPalettes: familyPalettes)
                        .frame(maxWidth: .infinity)
                        .frame(height: Self.pageHeight)
                }
            }
            .accessibilityHidden(true)
        }
    }
}

// MARK: - Shared parts

private enum GuideSeals {
    static func blank(_ size: SealSize) -> StoneSeal {
        StoneSeal(valence: .neutralMedium, glyph: nil, palette: SpineColors.palette(nil, isBlank: true),
                  isBlank: true, size: size)
    }

    static func lit(_ valence: Valence, _ size: SealSize, palette: StonePalette? = nil) -> StoneSeal {
        StoneSeal(valence: valence, glyph: nil, palette: palette ?? SpineColors.palette(nil, isBlank: false),
                  isBlank: false, size: size)
    }

    /// The nine stones by row (polarity) and column (size), for the grid.
    static let grid: [[Valence]] = [
        [.lowlightSmall, .lowlightMedium, .lowlightLarge],
        [.neutralSmall, .neutralMedium, .neutralLarge],
        [.highlightSmall, .highlightMedium, .highlightLarge]
    ]

    static let valences: [Valence] = [.lowlightMedium, .neutralMedium, .highlightMedium]

    static let domainDots: [DotStyle] = [.filled, .ring, .quiet]

    static let sampleCollection = Collection(
        id: UUID(uuidString: "00000000-0000-0000-0000-00000000c012")!,
        name: String(localized: "home.task.sample.collection"), mode: .stack, pebbleCount: 5)
}

// MARK: - The 24 pt mark

/// Every mark is composed at its natural size and, where the parts do not fit
/// the square, scaled down as a whole: the frame stays 24 × 24.
private struct GuideMark: View {
    let kind: GuideIllustration

    var body: some View {
        switch kind {
        case .pebbles:
            HStack(spacing: 1) {
                GuideSeals.blank(.pip)
                Image(systemName: "arrow.right")
                    .font(.system(size: 6, weight: .semibold))
                    .foregroundStyle(Color.pebbles.outline)
                GuideSeals.lit(.highlightSmall, .pip)
            }
            .fixedSize()
            .scaleEffect(0.9)
        case .stones:
            VStack(spacing: 1) {
                ForEach(GuideSeals.grid, id: \.self) { row in
                    HStack(alignment: .bottom, spacing: 1) {
                        ForEach(row) { GuideSeals.lit($0, .pip) }
                    }
                }
            }
            .fixedSize()
            .scaleEffect(0.55)
        case .emotions:
            VStack(spacing: 1) {
                HStack(spacing: 0) { ForEach(0..<4, id: \.self) { _ in GuideSeals.lit(.highlightSmall, .pip) } }
                HStack(spacing: 0) { ForEach(0..<3, id: \.self) { _ in GuideSeals.lit(.highlightSmall, .pip) } }
            }
            .fixedSize()
            .scaleEffect(0.75)
        case .valence:
            HStack(spacing: 0) {
                ForEach(GuideSeals.valences) { GuideSeals.lit($0, .pip) }
            }
            .fixedSize()
            .scaleEffect(0.8)
        case .domains:
            HStack(spacing: 3) {
                ForEach(Array(GuideSeals.domainDots.enumerated()), id: \.offset) { _, style in
                    DotMark(style: style, color: Color.pebbles.onSurfaceVariant, diameter: 6)
                }
            }
        case .collections:
            VStack(spacing: 1) {
                GuideSeals.lit(.highlightSmall, .tiny)
                GuideSeals.lit(.neutralSmall, .tiny)
                GuideSeals.lit(.lowlightSmall, .tiny)
            }
            .fixedSize()
            .scaleEffect(0.85)
        case .souls:
            HStack(spacing: 0) {
                ForEach(Array(ChecklistMarks.souls.enumerated()), id: \.offset) { _, strokes in
                    SoulMark(strokes: strokes, side: 10)
                }
            }
        case .glyphs:
            ZStack {
                Circle()
                    .strokeBorder(Color.pebbles.outline, style: StrokeStyle(lineWidth: 1, dash: [2, 2]))
                GlyphThumbnail(strokes: ChecklistMarks.souls[1], side: 14, strokeColor: Color.pebbles.onSurfaceVariant)
                    .frame(width: 14, height: 14)
            }
        case .karma:
            HStack(spacing: 2) {
                GuideSeals.lit(.highlightSmall, .pip)
                Text(verbatim: "+1")
                    .spineType(.kicker)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
            }
            .fixedSize()
            .scaleEffect(0.8)
        }
    }
}

// MARK: - The 120 pt page band

private struct GuidePagePicture: View {
    let kind: GuideIllustration
    var familyPalettes: [StonePalette] = []

    var body: some View {
        switch kind {
        case .pebbles:
            HStack(spacing: 6) {
                GuideSeals.blank(.contents)
                Image(systemName: "arrow.right")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.pebbles.outline)
                GuideSeals.lit(.highlightMedium, .contents)
            }
        case .stones:
            VStack(spacing: Spacing.sm) {
                ForEach(GuideSeals.grid, id: \.self) { row in
                    HStack(alignment: .bottom, spacing: Spacing.md) {
                        ForEach(row) { GuideSeals.lit($0, .contents) }
                    }
                }
            }
        case .emotions:
            HStack(spacing: Spacing.sm) {
                ForEach(0..<7, id: \.self) { i in
                    GuideSeals.lit(.highlightMedium, .contents,
                                   palette: i < familyPalettes.count ? familyPalettes[i] : nil)
                }
            }
        case .valence:
            HStack(spacing: Spacing.md) {
                ForEach(GuideSeals.valences) { GuideSeals.lit($0, .contents) }
            }
        case .domains:
            HStack(spacing: Spacing.md) {
                ForEach(Array(GuideSeals.domainDots.enumerated()), id: \.offset) { _, style in
                    DotMark(style: style, color: Color.pebbles.onSurfaceVariant, diameter: 12)
                }
            }
        case .collections:
            CollectionRow(collection: GuideSeals.sampleCollection)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.lg)
        case .souls:
            HStack(spacing: Spacing.lg) {
                ForEach(Array(ChecklistMarks.souls.enumerated()), id: \.offset) { _, strokes in
                    SoulMark(strokes: strokes, side: 28)
                }
            }
        case .glyphs:
            ZStack {
                Circle()
                    .strokeBorder(Color.pebbles.outline, style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    .frame(width: 56, height: 56)
                GlyphThumbnail(strokes: ChecklistMarks.souls[1], side: 36, strokeColor: Color.pebbles.onSurfaceVariant)
                    .frame(width: 36, height: 36)
            }
        case .karma:
            HStack(spacing: 6) {
                GuideSeals.lit(.highlightMedium, .contents)
                Text(verbatim: "+1")
                    .spineType(.kicker)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
    }
}
