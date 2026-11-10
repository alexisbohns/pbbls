import SwiftUI

/// Step 1 (#1510 D2): the letter beside its rail, the first paragraph in the italic lead, the mark
/// and the signature under a rule, one button. The letter is plain paragraphs (see `PromiseLetter`).
struct PromiseStep: View {
    let letter: PromiseLetter?
    let unavailable: Bool
    let onContinue: () -> Void
    var scrolls = true

    private static let railWidth: CGFloat = 44
    private static let ruleWidth: CGFloat = 64

    /// 12 pt capitals at 0.3 em. Set on the rank itself: a `.tracking` outside `spineType` loses to
    /// the rank's own.
    private static let railLabel: SpineType.Style = {
        var style = SpineType.Style.badgeLabel
        style.size = 12
        style.trackingEm = 0.3
        style.isUppercase = true
        return style
    }()

    @Environment(\.locale) private var locale

    var body: some View {
        VStack(spacing: Spacing.lg) {
            // The rail runs from the top to just above the button; only the letter scrolls.
            HStack(alignment: .top, spacing: 0) {
                rail
                letterColumn
            }
            .frame(maxHeight: .infinity)

            Button(action: onContinue) { Text("I'll hold you to it") }
                .buttonStyle(PebblesPrimaryButtonStyle())
                .padding(.horizontal, Spacing.xl)
                .padding(.bottom, Spacing.sm)
        }
    }

    /// The letter's margin: a vertical label against a hairline. Decoration, the title says it already.
    private var rail: some View {
        HStack(spacing: 0) {
            RotatedLabel {
                Text("A promise")
                    .spineType(Self.railLabel)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .fixedSize()
                    .rotationEffect(.degrees(-90))
            }
            .padding(.top, Spacing.xxl)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            Rectangle().fill(Color.pebbles.outlineVariant).frame(width: 1)
        }
        .frame(width: Self.railWidth)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private var letterColumn: some View {
        if scrolls {
            ScrollView { letterContent }
                .scrollBounceBehavior(.basedOnSize)
        } else {
            letterContent.frame(maxHeight: .infinity, alignment: .top)
        }
    }

    private var letterContent: some View {
        let french = locale.prefersFrench
        return VStack(alignment: .leading, spacing: Spacing.lg) {
            PromiseTitle()
                // Wraps to a second line instead of truncating when the column is not in a scroll view.
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Rectangle().fill(Color.pebbles.outlineVariant).frame(width: Self.ruleWidth, height: 1)
            if let letter {
                ForEach(Array(letter.paragraphs(french: french).enumerated()), id: \.offset) { index, paragraph in
                    if index == 0 {
                        Text(paragraph).spineType(.greetingLine)
                    } else {
                        // Inside `pebblesFont`: the rank sets its own line spacing, and the nearer modifier wins.
                        Text(paragraph).lineSpacing(6).pebblesFont(.body)
                    }
                }
                .foregroundStyle(Color.pebbles.onSurface)
                Rectangle().fill(Color.pebbles.outlineVariant).frame(height: 1)
                HStack(spacing: Spacing.md) {
                    PbblsGlyphView(.pebbleRound, .tile, form: PbblsGlyph.pebbleRound.form(for: .selected),
                                   style: AnyShapeStyle(Color.pebbles.primary))
                        .accessibilityHidden(true)
                    Text(letter.signedBy(french: french))
                        .spineType(.communityTitle)
                        .foregroundStyle(Color.pebbles.onSurface)
                }
            } else if unavailable {
                Text("Our promise could not load. It will be here in Settings.")
                    .pebblesFont(.body)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, Spacing.xl)
        .padding(.trailing, Spacing.xl)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.lg)
    }
}

/// "Before anything else, *a promise.*": the title with its closing words in primary italic.
private struct PromiseTitle: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight

    var body: some View {
        let roman = SpineType.Style.sectionTitle
        let spacing = SpineType.resolve(roman, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
            .lineSpacing
        var italic = roman
        italic.italic = true
        italic.weight = 340
        return (run(String(localized: "Before anything else,"), roman, Color.pebbles.onSurface)
            + Text(" ")
            + run(String(localized: "a promise."), italic, Color.pebbles.primary))
            // SwiftUI clamps a negative `lineSpacing` to zero (measured), so a rank's line height
            // below the font's natural one never shows; the gap stays the font's natural one.
            .lineSpacing(spacing)
    }

    private func run(_ text: String, _ style: SpineType.Style, _ colour: Color) -> Text {
        let resolved = SpineType.resolve(style, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
        return Text(verbatim: text).font(Font(resolved.uiFont)).tracking(resolved.tracking).foregroundStyle(colour)
    }
}

/// Lays its child out at the rotated bounding box, so a view turned by `rotationEffect` takes the
/// room it draws in (a rotation alone leaves the layout frame unturned).
private struct RotatedLabel<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View { RotatedLayout { content() } }
}

private struct RotatedLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let size = subviews.first?.sizeThatFits(.unspecified) ?? .zero
        return CGSize(width: size.height, height: size.width)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: CGPoint(x: bounds.midX, y: bounds.midY), anchor: .center, proposal: .unspecified)
    }
}
