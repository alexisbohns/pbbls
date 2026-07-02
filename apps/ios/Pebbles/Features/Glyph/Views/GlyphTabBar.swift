import SwiftUI

/// Floating segmented pill for the glyph page. Three segments; the selected one
/// gets an accent-surface capsule. Liquid-glass background reusing the app's
/// iOS-26 `glassEffect` + iOS-17 material fallback (see `KarmaEarnedCapsule`).
struct GlyphTabBar: View {
    @Binding var selection: GlyphTab

    var body: some View {
        HStack(spacing: Spacing.xs) {
            ForEach(GlyphTab.allCases) { tab in
                segment(tab)
            }
        }
        .padding(Spacing.xs)
        .modifier(GlyphTabBarGlass())
        .clipShape(Capsule())
        .frame(maxWidth: 320)
        .padding(.bottom, Spacing.sm)
    }

    private func segment(_ tab: GlyphTab) -> some View {
        Button {
            withAnimation(.snappy) { selection = tab }
        } label: {
            VStack(spacing: 2) {
                Image(systemName: symbol(tab))
                    .font(.system(size: 15, weight: .medium))
                Text(title(tab))
                    .font(.caption2.weight(.medium))
            }
            .foregroundStyle(selection == tab ? Color.accent.primary : Color.system.secondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.sm)
            .background {
                if selection == tab {
                    Capsule().fill(Color.accent.surface)
                }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title(tab))
        .accessibilityAddTraits(selection == tab ? [.isButton, .isSelected] : .isButton)
    }

    private func title(_ tab: GlyphTab) -> LocalizedStringKey {
        switch tab {
        case .mine: return "Mine"
        case .owned: return "Owned"
        case .commu: return "Commu"
        }
    }

    private func symbol(_ tab: GlyphTab) -> String {
        switch tab {
        case .mine: return "person.fill"
        case .owned: return "checkmark.seal"
        case .commu: return "person.3.fill"
        }
    }
}

/// Liquid-glass pill background matching `KarmaEarnedCapsule`'s treatment.
private struct GlyphTabBarGlass: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.glassEffect(.regular, in: Capsule())
        } else {
            content
                .background {
                    Capsule().fill(Color.system.background)
                        .overlay(Capsule().fill(.regularMaterial))
                        .overlay(Capsule().strokeBorder(.white.opacity(0.22), lineWidth: 0.5))
                }
                .compositingGroup()
                .shadow(color: .black.opacity(0.16), radius: 12, y: 4)
        }
    }
}

#Preview {
    struct Harness: View {
        @State private var tab: GlyphTab = .mine
        var body: some View { GlyphTabBar(selection: $tab) }
    }
    return Harness().padding()
}
