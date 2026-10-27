import SwiftUI

/// The Collections tab's explainer card (spec §3, E2): what a collection is,
/// the three steps drawn with the app's real parts, the aside and one text
/// link. Nothing is coloured but the seals; the drawings are hidden from
/// VoiceOver so the card reads top to bottom.
struct CollectionsNote: View {
    let onStart: () -> Void

    /// Fixed so the sample row never changes identity between renders.
    private static let sampleId = UUID(uuidString: "5B4C1F3E-9A2D-4E7B-8C6F-0D1E2F3A4B5C")!

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(verbatim: CollectionsNoteCopy.kicker)
                .spineType(.kicker)
                .foregroundStyle(Color.pebbles.outline)
                .accessibilityAddTraits(.isHeader)
            Text(verbatim: CollectionsNoteCopy.sentence)
                .spineType(.summary)
                .italic()
                .foregroundStyle(Color.pebbles.onSurface)
            step(1, caption: CollectionsNoteCopy.step1) { seals }
            step(2, caption: CollectionsNoteCopy.step2) { modes }
            step(3, caption: CollectionsNoteCopy.step3) { sampleRow }
            summary(CollectionsNoteCopy.aside)
            Button(action: onStart) {
                Text(verbatim: CollectionsNoteCopy.start)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.primary)
            }
            .buttonStyle(.plain)
        }
        .padding(Spacing.lg)
        .frame(maxWidth: 393)
        .background(Color.pebbles.surfaceContainerLow)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.lg))
        .overlay {
            RoundedRectangle(cornerRadius: Spacing.lg)
                .strokeBorder(Color.pebbles.rule, lineWidth: 1)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Parts

    private func step<Drawing: View>(_ number: Int, caption: String,
                                     @ViewBuilder drawing: () -> Drawing) -> some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Text(verbatim: "\(number)")
                .spineType(.kicker)
                .foregroundStyle(Color.pebbles.outline)
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(verbatim: caption)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.onSurface)
                drawing()
                    .accessibilityHidden(true)
            }
        }
    }

    /// `.summary` is the Spine's italic line.
    private func summary(_ text: String) -> some View {
        Text(verbatim: text)
            .spineType(.summary)
            .italic()
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
    }

    /// Step 1: three lit stones, the default palette.
    private var seals: some View {
        HStack(spacing: Spacing.sm) {
            ForEach([Valence.highlightSmall, .neutralLarge, .lowlightMedium]) { valence in
                StoneSeal(valence: valence, glyph: nil,
                          palette: SpineColors.palette(nil, isBlank: false),
                          isBlank: false, size: .contents)
            }
        }
    }

    /// Step 2: the three mode marks, one per row, each with its description.
    private var modes: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            mode(.stack, CollectionsNoteCopy.step2Stack)
            mode(.pack, CollectionsNoteCopy.step2Pack)
            mode(.track, CollectionsNoteCopy.step2Track)
        }
    }

    private func mode(_ mode: CollectionMode, _ description: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
            CollectionModeBadge(mode: mode)
            summary(description)
                // An HStack otherwise truncates the line instead of wrapping it.
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Step 3: one real row, as the list will draw it.
    private var sampleRow: some View {
        CollectionRow(collection: Collection(id: Self.sampleId, name: CollectionsNoteCopy.sample,
                                             mode: .track, pebbleCount: 3))
    }
}

/// The one-line footnote shown after the first collection (spec §3), with a
/// dismissal the device remembers.
struct CollectionsFootnote: View {
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
            Text(verbatim: CollectionsNoteCopy.footnote)
                .spineType(.summary)
                .italic()
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .padding(Spacing.xs)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(CollectionsNoteCopy.footnoteDismiss)
        }
    }
}

#Preview("Card") {
    ScrollView {
        CollectionsNote(onStart: {})
            .padding(Spacing.lg)
    }
    .background(Color.pebbles.surface)
}

#Preview("Footnote") {
    CollectionsFootnote(onDismiss: {})
        .padding(Spacing.lg)
        .background(Color.pebbles.surface)
}
