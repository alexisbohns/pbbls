import SwiftUI

/// Single soul cell used in both `SoulsListView` (always `.default`) and
/// `SoulPickerSheet` (state-driven). Vertical stack: `GlyphView` on top,
/// name in subhead, optional `fossil.shell` + ripple/pebble count below.
///
/// Visual specification table is in
/// `docs/superpowers/specs/2026-05-17-issue-459-glyph-souls-consistency-design.md` §3.
struct SoulItem: View {
    enum Case { case selected, unselected, `default`, create }

    let `case`: Case
    let soul: SoulWithGlyph?
    let count: Int?
    var onTap: (() -> Void)? = nil

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Group {
            if let onTap {
                Button(action: onTap) { content }
                    .buttonStyle(.plain)
            } else {
                content
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(displayName)
        .accessibilityAddTraits(traits)
        .accessibilityHint(`case` == .create ? Text("Opens soul creation form") : Text(""))
    }

    private var traits: AccessibilityTraits {
        var t: AccessibilityTraits = []
        if onTap != nil { t.insert(.isButton) }
        if `case` == .selected { t.insert(.isSelected) }
        return t
    }

    @ViewBuilder
    private var content: some View {
        VStack(spacing: Spacing.sm) {
            GlyphView(case: glyphCase, strokes: soul?.glyph.strokes, side: 96)

            VStack(spacing: Spacing.xs) {
                Text(displayName)
                    .pebblesFont(nameToken)
                    .foregroundStyle(nameColor)
                    .lineLimit(1)
                    .truncationMode(.tail)

                if `case` != .create, let count {
                    HStack(spacing: Spacing.xs) {
                        Image(systemName: "fossil.shell")
                            .font(.system(size: 11))
                            .foregroundStyle(fossilColor)
                        Text("\(count)")
                            .pebblesFont(.meta)
                            .foregroundStyle(Color.system.secondary)
                    }
                }
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var glyphCase: GlyphView.Case {
        switch `case` {
        case .selected:   return .selected
        case .unselected: return .unselected
        case .default:    return .default
        case .create:     return .create
        }
    }

    private var displayName: String {
        switch `case` {
        case .create: return String(localized: "New soul")
        default:      return soul?.name ?? ""
        }
    }

    // A name always renders in the handwritten face (issue #515); selection is
    // carried by `nameColor`, not weight.
    private var nameToken: PebblesFont { .bodyLeadHand }

    private var nameColor: Color {
        `case` == .selected ? Color.accent.primary : Color.system.secondary
    }

    /// Count icon uses AccentSecondary in light, AccentShaded in dark.
    private var fossilColor: Color {
        colorScheme == .dark ? Color.accent.shaded : Color.accent.secondary
    }
}

#Preview("All cases") {
    let sample = SoulWithGlyph(
        id: UUID(),
        name: "Molly",
        glyphId: SystemGlyph.default,
        glyph: Glyph(
            id: SystemGlyph.default,
            name: nil,
            strokes: [
                GlyphStroke(d: "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width: 6)
            ],
            viewBox: "0 0 200 200",
            userId: nil
        ),
        pebblesCount: 12
    )

    return ScrollView {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 96), spacing: Spacing.lg)],
            spacing: Spacing.lg
        ) {
            SoulItem(case: .selected,   soul: sample, count: 12)
            SoulItem(case: .unselected, soul: sample, count: 9)
            SoulItem(case: .default,    soul: sample, count: 3)
            SoulItem(case: .create,     soul: nil,   count: nil)
        }
        .padding(Spacing.lg)
    }
}
