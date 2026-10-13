import SwiftUI

/// Who gets to see it, against the "Make it a pebble" button. A tap selects
/// and does not advance; the grade defaults to secret (A1). A failed write
/// shows here, where the user is standing.
struct CompletePrivacyStep: View {
    let model: CompleteFlowModel

    var body: some View {
        VStack(spacing: Spacing.md) {
            ForEach(Visibility.allCases) { grade in
                row(for: grade)
            }

            if let error = model.completeError {
                Text(verbatim: error)
                    .pebblesFont(.callout)
                    .foregroundStyle(.red) // #921
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.sm)
            }
        }
    }

    @ViewBuilder
    private func row(for grade: Visibility) -> some View {
        let isSelected = (grade == model.draft.visibility)

        Button {
            model.select(visibility: grade)
        } label: {
            HStack(spacing: Spacing.md) {
                Image(systemName: grade.systemImageName)
                    .font(.title3)
                    .frame(width: 32)
                    .foregroundStyle(isSelected ? Color.accent.primary : Color.system.secondary)

                VStack(alignment: .leading, spacing: 2) {
                    Text(grade.label)
                        .pebblesFont(.bodyEmphasized)
                        .foregroundStyle(isSelected ? Color.accent.primary : Color.system.foreground)
                    Text(explanation(for: grade))
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.leading)
                }

                Spacer(minLength: 0)
            }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? Color.accent.primary.opacity(0.12) : Color.system.muted)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(grade.label)
        .accessibilityHint(explanation(for: grade))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }

    private func explanation(for grade: Visibility) -> LocalizedStringResource {
        switch grade {
        case .secret:  return "Only you can see this one."
        case .private: return "Your mutual connections can see it."
        case .public:  return "Anyone with the link can see it."
        }
    }
}
