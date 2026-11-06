import SwiftUI

/// Step 2 (#1510 D3): one switch per act, on the app's settings rows. A switch turns on and off
/// freely and Continue records the lot, so nothing has to be taken back. An act already on record
/// stays on and locked: withdrawing one is a Settings action, not a side effect of this step.
struct ConsentStep: View {
    let state: OnboardingConsentState
    let onToggle: (ConsentKind, Bool) -> Void
    let onContinue: () -> Void
    let onLogOut: () -> Void
    var scrolls = true
    /// Reference renders only: `ImageRenderer` draws no `Toggle`, so a capsule and a thumb stand in.
    var drawsStaticSwitches = false

    @State private var presentedLegalDoc: LegalDoc?

    private static let radius = Spacing.lg

    /// 12 pt regular, for the two footer lines: set on the rank itself, a modifier outside loses.
    private static let captionRank = SpineType.Style.inclusive(12, 400)

    /// 17 pt medium, the gate's Log out.
    private static let logOutRank = SpineType.Style.inclusive(17, 500)

    var body: some View {
        OnboardingStepScaffold(
            title: "Your consent",
            lead: "One switch for each thing you agree to.",
            scrolls: scrolls
        ) {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                group
                HStack(spacing: Spacing.sm) {
                    readButton("Read the terms", doc: .terms)
                    readButton("Read the policy", doc: .privacy)
                }
            }
            .padding(.top, Spacing.xs)
        } footer: {
            footer
        }
        .sheet(item: $presentedLegalDoc) { doc in
            LegalDocumentSheet(url: doc.url)
                .ignoresSafeArea()
        }
    }

    // MARK: - Rows

    /// One bordered group, the look of `pebblesListRow`. A `VStack`, not a `List`: a `List` inside
    /// a scroll view misbehaves and `ImageRenderer` cannot draw one.
    private var group: some View {
        let kinds = OnboardingConsentState.order
        return VStack(spacing: 0) {
            ForEach(Array(kinds.enumerated()), id: \.element) { index, kind in
                if index > 0 {
                    Rectangle().fill(Color.pebbles.rule).frame(height: 1)
                }
                row(kind)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Self.radius))
        .overlay(RoundedRectangle(cornerRadius: Self.radius).stroke(Color.pebbles.rule, lineWidth: 1))
    }

    private func row(_ kind: ConsentKind) -> some View {
        let recorded = state.recorded.contains(kind)
        let isOn = recorded || state.on.contains(kind)
        let binding = Binding(get: { isOn }, set: { onToggle(kind, $0) })
        let labels = VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(Self.title(kind))
                .pebblesFont(.body)
                .foregroundStyle(Color.pebbles.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            Text(recorded ? "Already on record" : Self.detail(kind))
                .pebblesFont(.subhead)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
        }
        return Group {
            if drawsStaticSwitches {
                HStack(spacing: Spacing.md) {
                    labels.frame(maxWidth: .infinity, alignment: .leading)
                    StaticSwitch(isOn: isOn)
                }
            } else {
                Toggle(isOn: binding) { labels }
                    .tint(Color.pebbles.primary)
                    .disabled(recorded)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private static func title(_ kind: ConsentKind) -> LocalizedStringResource {
        switch kind {
        case .healthData: "Store how I feel"
        case .terms: "Terms of Service"
        case .privacy: "Privacy Policy"
        case .ageAssurance: "I am 16 or over"
        }
    }

    private static func detail(_ kind: ConsentKind) -> LocalizedStringResource {
        switch kind {
        case .healthData: "Moods, emotions and what I write. Sensitive data under GDPR art. 9."
        case .terms: "How Pebbles works and what we owe you."
        case .privacy: "What we keep, where, and for how long."
        case .ageAssurance: "Pebbles is not made for younger people."
        }
    }

    private func readButton(_ title: LocalizedStringResource, doc: LegalDoc) -> some View {
        Button { presentedLegalDoc = doc } label: {
            Text(title)
                .pebblesFont(.callout)
                .foregroundStyle(Color.pebbles.onSurface)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 44)
                .overlay(Capsule().stroke(Color.pebbles.outlineVariant, lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Footer

    private var footer: some View {
        VStack(spacing: Spacing.sm) {
            if state.failed {
                Text("Your consent could not be saved. Check your connection and try again.")
                    .spineType(Self.captionRank)
                    .foregroundStyle(Color.pebbles.error)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if !state.canContinue && !state.isSaving {
                Text("All four are needed to continue.")
                    .spineType(Self.captionRank)
                    .foregroundStyle(Color.pebbles.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Button(action: onContinue) { Text("Continue") }
                .buttonStyle(PebblesPrimaryButtonStyle(isLoading: state.isSaving))
                .disabled(!state.canContinue)
            if state.hasMissingActs {
                // The exit the gate offers today: declining is Log out.
                Button(action: onLogOut) { Text("Log out") }
                    .spineType(Self.logOutRank)
                    .foregroundStyle(Color.pebbles.primary)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .disabled(state.isSaving)
            }
        }
    }
}

/// A switch drawn by hand for reference renders, never shipped.
private struct StaticSwitch: View {
    let isOn: Bool

    var body: some View {
        ZStack(alignment: isOn ? .trailing : .leading) {
            Capsule().fill(isOn ? Color.pebbles.primary : Color.pebbles.outlineVariant)
            Circle().fill(Color.pebbles.onPrimary).padding(2)
        }
        .frame(width: 51, height: 31)
    }
}
