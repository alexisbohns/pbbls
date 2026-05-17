import SwiftUI

/// Canonical glyph chrome component. Renders a 2XL-radius (34pt) frame
/// with state-driven border (continuous/dashed, weight, color) and either
/// the user's glyph strokes or an SF Symbol overlay (scribble for `.carve`,
/// plus for `.create`).
///
/// Named `GlyphView` (not `Glyph`) because the model type at
/// `Features/Glyph/Models/Glyph.swift` already owns the `Glyph` symbol
/// in this module.
///
/// Visual specification table is in
/// `docs/superpowers/specs/2026-05-17-issue-459-glyph-souls-consistency-design.md` §2.
struct GlyphView: View {
    enum Case {
        case profile      // continuous 1pt system.muted; glyph in accent.primary
        case carve        // dashed 2pt system.muted; sf.scribble in system.secondary
        case create       // dashed 2pt system.muted; sf.plus in system.muted
        case selected     // continuous 2pt accent.primary; glyph in accent.primary
        case unselected   // continuous 1pt system.muted; glyph in system.muted
        case `default`    // continuous 1pt system.muted; glyph in system.secondary
    }

    let `case`: Case
    let strokes: [GlyphStroke]?
    var side: CGFloat = 96

    init(case: Case, strokes: [GlyphStroke]? = nil, side: CGFloat = 96) {
        self.case = `case`
        self.strokes = strokes
        self.side = side
    }

    var body: some View {
        ZStack {
            border
            content
        }
        .frame(width: side, height: side)
    }

    @ViewBuilder
    private var border: some View {
        let shape = RoundedRectangle(cornerRadius: Spacing.xxl, style: .continuous)
        switch `case` {
        case .selected:
            shape.strokeBorder(Color.accent.primary, lineWidth: 2)
        case .carve, .create:
            shape.strokeBorder(
                Color.system.muted,
                style: StrokeStyle(lineWidth: 2, dash: [10, 10])
            )
        case .profile, .unselected, .default:
            shape.strokeBorder(Color.system.muted, lineWidth: 1)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch `case` {
        case .carve:
            Image(systemName: "scribble")
                .font(.system(size: max(side * 0.4, 18), weight: .regular))
                .foregroundStyle(Color.system.secondary)
        case .create:
            Image(systemName: "plus")
                .font(.system(size: max(side * 0.4, 18), weight: .regular))
                .foregroundStyle(Color.system.muted)
        case .profile, .selected:
            GlyphThumbnail(strokes: strokes ?? [], side: side, strokeColor: Color.accent.primary)
        case .unselected:
            GlyphThumbnail(strokes: strokes ?? [], side: side, strokeColor: Color.system.muted)
        case .default:
            GlyphThumbnail(strokes: strokes ?? [], side: side, strokeColor: Color.system.secondary)
        }
    }
}

#Preview("All cases — light") {
    let strokes = [
        GlyphStroke(d: "M40,40 C80,20 120,20 160,40 S180,120 160,160 S80,180 40,160 S20,80 40,40", width: 6)
    ]
    return ScrollView {
        VStack(spacing: Spacing.lg) {
            HStack(spacing: Spacing.lg) {
                VStack { GlyphView(case: .profile,    strokes: strokes); Text(".profile") }
                VStack { GlyphView(case: .carve);                       Text(".carve") }
                VStack { GlyphView(case: .create);                      Text(".create") }
            }
            HStack(spacing: Spacing.lg) {
                VStack { GlyphView(case: .selected,   strokes: strokes); Text(".selected") }
                VStack { GlyphView(case: .unselected, strokes: strokes); Text(".unselected") }
                VStack { GlyphView(case: .default,    strokes: strokes); Text(".default") }
            }
        }
        .padding(Spacing.lg)
    }
}

#Preview("All cases — dark") {
    let strokes = [
        GlyphStroke(d: "M40,40 C80,20 120,20 160,40 S180,120 160,160 S80,180 40,160 S20,80 40,40", width: 6)
    ]
    return ScrollView {
        VStack(spacing: Spacing.lg) {
            HStack(spacing: Spacing.lg) {
                GlyphView(case: .profile,    strokes: strokes)
                GlyphView(case: .carve)
                GlyphView(case: .create)
            }
            HStack(spacing: Spacing.lg) {
                GlyphView(case: .selected,   strokes: strokes)
                GlyphView(case: .unselected, strokes: strokes)
                GlyphView(case: .default,    strokes: strokes)
            }
        }
        .padding(Spacing.lg)
    }
    .preferredColorScheme(.dark)
}
