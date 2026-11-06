import SwiftUI

/// Step 5 (#1510 §1, D7): "Who would you call first with good news?" One split stone of three pieces
/// (the Home quick actions' seed), no outlines but the empty place's dash. A named person's piece is
/// `primaryContainer` with their glyph; the name and "Change glyph" sit under it. The empty place is
/// a dashed outline with a plus: tapping it opens the name field under the piece. Continue needs one
/// named person; Later is the top bar's.
struct PeopleStep: View {
    let people: [OnboardingPerson]
    let glyphFor: (Int) -> Glyph?
    let canAdd: Bool
    let canContinue: Bool
    let cta: LocalizedStringResource
    let onAdd: () -> Void
    let onNameChange: (Int, String) -> Void
    let onCommit: (Int) -> Void
    let onEdit: (Int) -> Void
    let onChangeGlyph: (Int) -> Void
    let onContinue: () -> Void
    var scrolls = true
    /// Reference renders only: `ImageRenderer` draws no `TextField`, so a field is a `Text`.
    var drawsStaticFields = false

    static let places = 3
    /// The Home quick actions' rock, so the pieces lean as they do there.
    static let seed = "camera|fixed|rock|q11"
    private static let options = StoneOptions()

    /// 12 pt regular, set on the rank itself: a modifier outside a rank loses to it.
    private static let captionRank = SpineType.Style.inclusive(12, 400)
    private static let addRank = SpineType.Style.inclusive(14, 500)

    private var isEditing: Bool { people.contains(where: \.isEditing) }

    var body: some View {
        OnboardingStepScaffold(
            title: "Three places",
            lead: "Who would you call first with good news?",
            scrolls: scrolls
        ) {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                if !isEditing {
                    Text("First names only. They are never contacted.")
                        .pebblesFont(.body)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .fixedSize(horizontal: false, vertical: true)
                }
                stone
                // The names' 44 pt targets sit lower than their text: this takes the gap back.
                names.padding(.top, -Spacing.lg)
                Text("You can add more people later.")
                    .spineType(Self.captionRank)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            .padding(.top, Spacing.xs)
        } footer: {
            Button(action: onContinue) { Text(cta) }
                .buttonStyle(PebblesPrimaryButtonStyle())
                .disabled(!canContinue)
        }
    }

    // MARK: - The stone

    private var stone: some View {
        SplitStone(
            count: Self.places,
            seed: Self.seed,
            options: Self.options,
            fills: (0..<Self.places).map { people.indices.contains($0) ? Color.pebbles.primaryContainer : Color.clear },
            minHeight: 116
        ) { index in
            piece(index)
        }
        // The empty place's dash follows the piece's own outline: the same cut as the stone's.
        .background {
            GeometryReader { proxy in
                let pieces = StoneCache.split(
                    size: proxy.size, count: Self.places, seed: Self.seed, options: Self.options
                )
                ForEach(Array(pieces.enumerated()), id: \.offset) { index, outline in
                    if !people.indices.contains(index) {
                        StoneGeometry.path(outline)
                            .stroke(Color.pebbles.outline, style: StrokeStyle(lineWidth: 1.5, dash: [6, 6]))
                    }
                }
            }
            .accessibilityHidden(true)
        }
    }

    @ViewBuilder
    private func piece(_ index: Int) -> some View {
        if people.indices.contains(index) {
            ZStack {
                if let glyph = glyphFor(index) {
                    GlyphThumbnail(strokes: glyph.strokes, side: 56, strokeColor: Color.pebbles.onPrimaryContainer)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityHidden(true)
        } else {
            Button(action: onAdd) {
                PbblsIconImage(.plusCircle, .tile, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!canAdd)
            .accessibilityLabel(Text("Add one"))
        }
    }

    // MARK: - Under the pieces

    private var names: some View {
        HStack(alignment: .top, spacing: Self.options.gutter) {
            ForEach(0..<Self.places, id: \.self) { index in
                column(index)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    @ViewBuilder
    private func column(_ index: Int) -> some View {
        if people.indices.contains(index) {
            let person = people[index]
            if person.isEditing {
                PersonNameField(
                    name: person.name,
                    index: index,
                    drawsStatic: drawsStaticFields,
                    onNameChange: onNameChange,
                    onCommit: onCommit
                )
            } else {
                VStack(spacing: 0) {
                    Button { onEdit(index) } label: {
                        Text(verbatim: person.name)
                            .spineType(.communityTitle)
                            .foregroundStyle(Color.pebbles.onSurface)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .bottom)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Button { onChangeGlyph(index) } label: {
                        Text("Change glyph")
                            .spineType(Self.captionRank)
                            .foregroundStyle(Color.pebbles.primary)
                            .multilineTextAlignment(.center)
                            .padding(.top, Spacing.xs)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .top)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        } else {
            Text("Add one")
                .spineType(Self.addRank)
                .foregroundStyle(Color.pebbles.primary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .bottom)
                .accessibilityHidden(true)
        }
    }
}

/// S3: the name typed in place under the piece, focused as it appears; return commits. Focus loss
/// commits only after focus was gained, so the field's initial unfocused state never drops the place
/// before a keystroke.
private struct PersonNameField: View {
    let name: String
    let index: Int
    let drawsStatic: Bool
    let onNameChange: (Int, String) -> Void
    let onCommit: (Int) -> Void

    @FocusState private var focused: Bool
    @State private var wasFocused = false

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Group {
                if drawsStatic {
                    Text(name.isEmpty ? "Their name" : name)
                        .foregroundStyle(name.isEmpty ? Color.pebbles.onSurfaceVariant : Color.pebbles.onSurface)
                } else {
                    TextField(
                        text: Binding(get: { name }, set: { onNameChange(index, $0) }),
                        prompt: Text("Their name").foregroundStyle(Color.pebbles.onSurfaceVariant)
                    ) { Text("Their name") }
                        .focused($focused)
                        .textInputAutocapitalization(.words)
                        .submitLabel(.done)
                        .onSubmit { onCommit(index) }
                        .tint(Color.pebbles.primary)
                        .foregroundStyle(Color.pebbles.onSurface)
                }
            }
            .spineType(.communityTitle)
            .multilineTextAlignment(.center)
            .lineLimit(1)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .bottom)
            Rectangle().fill(Color.pebbles.primary).frame(height: 2)
        }
        .task { focused = true }
        .onChange(of: focused) { _, isFocused in
            if isFocused {
                wasFocused = true
            } else if wasFocused {
                wasFocused = false
                onCommit(index)
            }
        }
    }
}
