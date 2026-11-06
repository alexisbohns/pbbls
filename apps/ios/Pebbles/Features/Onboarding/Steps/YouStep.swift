import SwiftUI

/// Step 4 (#1510 §1): a large bare name field on a rule, the 3 × 3 glyph stone under it. With the
/// keyboard up the stone folds into one row of four. Continue needs a name or a glyph; Skip is the
/// top bar's.
struct YouStep: View {
    let name: String
    let glyphs: [Glyph]
    let selectedId: UUID?
    let canContinue: Bool
    let onNameChange: (String) -> Void
    let onSelect: (Glyph) -> Void
    let onDraw: () -> Void
    let onContinue: () -> Void
    var scrolls = true
    /// Reference renders only: `ImageRenderer` draws no `TextField`, so the name is a `Text`, and
    /// the stone's folded state is forced here instead of by the field's focus.
    var drawsStaticFields = false
    var drawsFolded = false

    @FocusState private var nameIsFocused: Bool

    /// 12 pt regular, set on the rank itself: a modifier outside a rank loses to it.
    private static let captionRank = SpineType.Style.inclusive(12, 400)

    private var folded: Bool { nameIsFocused || drawsFolded }

    var body: some View {
        OnboardingStepScaffold(title: "This is you", scrolls: scrolls) {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                nameField
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text("Your glyph")
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .accessibilityAddTraits(.isHeader)
                    GlyphStoneGrid(
                        glyphs: glyphs,
                        selectedId: selectedId,
                        onSelect: onSelect,
                        onDraw: onDraw,
                        folded: folded
                    )
                }
                .padding(.top, Spacing.sm)
            }
            // Takes back part of the scaffold's gap to its content: the name sits close under the title.
            .padding(.top, -(Spacing.lg - Spacing.sm))
            .animation(.snappy(duration: 0.28), value: folded)
        } footer: {
            Button(action: onContinue) { Text("That's me") }
                .buttonStyle(PebblesPrimaryButtonStyle())
                .disabled(!canContinue)
        }
    }

    private var nameField: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Group {
                if drawsStaticFields {
                    Text(name)
                } else {
                    TextField("", text: Binding(get: { name }, set: onNameChange))
                        .focused($nameIsFocused)
                        .textInputAutocapitalization(.words)
                        .submitLabel(.done)
                        .onSubmit { nameIsFocused = false }
                        .tint(Color.pebbles.primary)
                        .accessibilityLabel(Text("onboarding.you.name"))
                }
            }
            .spineType(.greeting)
            .foregroundStyle(Color.pebbles.onSurface)
            .frame(minHeight: 44, alignment: .leading)
            Rectangle()
                .fill(nameIsFocused ? Color.pebbles.primary : Color.pebbles.outline)
                .frame(height: nameIsFocused ? 2 : 1)
            Text("onboarding.you.name")
                .spineType(Self.captionRank)
                .foregroundStyle(nameIsFocused ? Color.pebbles.primary : Color.pebbles.onSurfaceVariant)
                .padding(.top, Spacing.xs)
                .accessibilityHidden(true)
        }
    }
}
