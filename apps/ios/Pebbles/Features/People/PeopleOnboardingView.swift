import SwiftUI

/// The People tab's onboarding (spec §2, E3 "Start from what you have"): what
/// a soul is, then the user's own latest pebbles asked about one by one. A
/// pure rendering of `PeopleOnboardingState`; nothing is coloured but the
/// seals (D2). `.done` draws nothing: the grid stands alone.
struct PeopleOnboardingView: View {
    let state: PeopleOnboardingState
    let palette: (UUID?) -> EmotionPalette?
    let onRecord: () -> Void
    let onWho: (PeoplePrompts.Prompt) -> Void
    let onNotNow: () -> Void

    var body: some View {
        switch state {
        case .noPebbles:
            noPebbles
        case .noSouls(let prompts, let skipped):
            noSouls(prompts: prompts, skipped: skipped)
        case .fewSouls(let prompts):
            fewSouls(prompts: prompts)
        case .done:
            EmptyView()
        }
    }

    // MARK: - States

    private var noPebbles: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            kicker
            lines
            textLink(PeopleOnboardingCopy.record, action: onRecord)
            placeholder
                .padding(.top, Spacing.lg)
        }
    }

    private func noSouls(prompts: [PeoplePrompts.Prompt], skipped: [String]) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            kicker
            summary(PeopleOnboardingCopy.question, italic: true)
            lines
            if !prompts.isEmpty {
                card(title: PeopleOnboardingCopy.latest, prompts: prompts) { EmptyView() }
                    .padding(.top, Spacing.sm)
            }
            ForEach(skipped, id: \.self) { name in
                summary(PeopleOnboardingCopy.notAsked(name), italic: true)
            }
            placeholder
                .padding(.top, Spacing.lg)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                summary(PeopleOnboardingCopy.placeholder1)
                summary(PeopleOnboardingCopy.placeholder2)
            }
        }
    }

    private func fewSouls(prompts: [PeoplePrompts.Prompt]) -> some View {
        card(title: PeopleOnboardingCopy.fewTitle, prompts: prompts) {
            summary(PeopleOnboardingCopy.fewMutual, italic: true)
            textLink(PeopleOnboardingCopy.notNow, action: onNotNow)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
    }

    // MARK: - Parts

    private var kicker: some View {
        Text(verbatim: PeopleOnboardingCopy.kicker)
            .spineType(.kicker)
            .foregroundStyle(Color.pebbles.outline)
            .accessibilityAddTraits(.isHeader)
    }

    private var lines: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            summary(PeopleOnboardingCopy.line1)
            summary(PeopleOnboardingCopy.line2)
            summary(PeopleOnboardingCopy.line3)
        }
    }

    /// `.summary` is the Spine's italic; `italic` here is the summary's own
    /// slant, for the question, the skipped lines and the mutual note.
    private func summary(_ text: String, italic: Bool = false) -> some View {
        (italic ? Text(verbatim: text).italic() : Text(verbatim: text))
            .spineType(.summary)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
    }

    private func textLink(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(verbatim: title)
                .pebblesFont(.callout)
                .foregroundStyle(Color.pebbles.primary)
        }
        .buttonStyle(.plain)
    }

    /// Decorative here: the toolbar `+` is the real create button.
    private var placeholder: some View {
        SoulItem(case: .create, soul: nil, count: nil)
            .frame(maxWidth: .infinity)
            .accessibilityHidden(true)
    }

    /// The hairline card `HomeChecklistView` draws, around a header and the rows.
    private func card<Footer: View>(title: String, prompts: [PeoplePrompts.Prompt],
                                    @ViewBuilder footer: () -> Footer) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            SpineSectionHeader(title)
            VStack(spacing: 0) {
                ForEach(Array(prompts.enumerated()), id: \.element.id) { index, prompt in
                    PromptRow(prompt: prompt, palette: palette(prompt.emotionId),
                              isLast: index == prompts.count - 1) { onWho(prompt) }
                }
            }
            footer()
        }
        .padding(Spacing.lg)
        .background(Color.pebbles.surfaceContainerLow)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.pebbles.rule, lineWidth: 1)
        }
    }
}
