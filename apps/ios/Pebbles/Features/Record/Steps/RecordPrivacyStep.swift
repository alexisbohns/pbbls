import SwiftUI

/// Step 9 — who gets to see it, and the publish button.
///
/// The grade is the decision most coupled to "am I ready for other people to
/// see this", which is why it sits against publish rather than in a toolbar
/// chip eight fields away (D2).
///
/// A tap selects and does not advance (D3). The snap state and any publish
/// error live here too, because this is where the user is standing when
/// publishing is blocked or fails (D10).
struct RecordPrivacyStep: View {
    let model: RecordFlowModel
    /// Non-nil while the attached photo blocks publishing (uploading or failed).
    let snapBlockedMessage: String?

    var body: some View {
        VStack(spacing: Spacing.md) {
            ForEach(Visibility.allCases) { grade in
                row(for: grade)
            }

            if let snapBlockedMessage {
                Text(verbatim: snapBlockedMessage)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.sm)
            }

            if let error = model.publishError {
                Text(verbatim: error)
                    .pebblesFont(.callout)
                    .foregroundStyle(.red)
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

    /// One line per M51 grade. Deliberately not on `Visibility` itself: the
    /// chip and the badge want the bare label, and only this step has room for
    /// the explanation.
    private func explanation(for grade: Visibility) -> LocalizedStringResource {
        switch grade {
        case .secret:  return "Only you can see this one."
        case .private: return "Your mutual connections can see it."
        case .public:  return "Anyone with the link can see it."
        }
    }
}
