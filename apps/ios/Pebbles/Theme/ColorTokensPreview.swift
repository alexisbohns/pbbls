import SwiftUI
import UIKit

/// A role resolved for an explicit trait collection. SwiftUI previews cannot
/// turn on Increase Contrast (`colorSchemeContrast` is read-only in the
/// environment), so the swatch resolves the UIColor itself.
private struct RoleSwatch: View {
    let role: PebblesColorRole
    let traits: UITraitCollection

    private func resolved(_ role: PebblesColorRole) -> Color {
        Color(uiColor: UIColor.pebbles(role).resolvedColor(with: traits))
    }

    var body: some View {
        VStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(resolved(role))
                .frame(height: 56)
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .strokeBorder(resolved(.outlineVariant))
                )
            Text(verbatim: role.rawValue)
                .font(.caption2.monospaced())
                .foregroundStyle(resolved(.onSurface))
        }
    }
}

private struct RolesGrid: View {
    let title: String
    let traits: UITraitCollection
    private let columns = [GridItem(.adaptive(minimum: 110), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(verbatim: title)
                    .font(.headline)
                    .foregroundStyle(Color(uiColor: UIColor.pebbles(.onSurface).resolvedColor(with: traits)))
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(PebblesColorRole.allCases, id: \.self) { role in
                        RoleSwatch(role: role, traits: traits)
                    }
                }
            }
            .padding()
        }
        .background(Color(uiColor: UIColor.pebbles(.surface).resolvedColor(with: traits)))
    }
}

private func previewTraits(_ style: UIUserInterfaceStyle, _ contrast: UIAccessibilityContrast) -> UITraitCollection {
    UITraitCollection { traits in
        traits.userInterfaceStyle = style
        traits.accessibilityContrast = contrast
    }
}

private struct Swatch: View {
    let name: String
    let color: Color

    var body: some View {
        VStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(color)
                .frame(height: 56)
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .strokeBorder(Color.pebbles.outlineVariant)
                )
            Text(name)
                .font(.caption2.monospaced())
                .foregroundStyle(Color.pebbles.onSurface)
        }
    }
}

private struct TokensGrid: View {
    private let columns = [GridItem(.adaptive(minimum: 110), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(verbatim: "System").font(.headline)
                    LazyVGrid(columns: columns, spacing: 12) {
                        Swatch(name: "system.foreground", color: .system.foreground)
                        Swatch(name: "system.secondary",  color: .system.secondary)
                        Swatch(name: "system.muted",      color: .system.muted)
                        Swatch(name: "system.background", color: .system.background)
                        Swatch(name: "system.onLight",    color: .system.onLight)
                    }
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text(verbatim: "Accent").font(.headline)
                    LazyVGrid(columns: columns, spacing: 12) {
                        Swatch(name: "accent.dark",      color: .accent.dark)
                        Swatch(name: "accent.shaded",    color: .accent.shaded)
                        Swatch(name: "accent.primary",   color: .accent.primary)
                        Swatch(name: "accent.secondary", color: .accent.secondary)
                        Swatch(name: "accent.light",     color: .accent.light)
                        Swatch(name: "accent.surface",   color: .accent.surface)
                    }
                }
            }
            .padding()
        }
        .background(Color.system.background)
    }
}

#Preview("Tokens — Light") {
    TokensGrid().preferredColorScheme(.light)
}

#Preview("Tokens — Dark") {
    TokensGrid().preferredColorScheme(.dark)
}

#Preview("Roles — Light") {
    RolesGrid(title: "Light", traits: previewTraits(.light, .normal))
}

#Preview("Roles — Dark") {
    RolesGrid(title: "Dark", traits: previewTraits(.dark, .normal))
}

#Preview("Roles — Light, High Contrast") {
    RolesGrid(title: "Light · High Contrast", traits: previewTraits(.light, .high))
}

#Preview("Roles — Dark, High Contrast") {
    RolesGrid(title: "Dark · High Contrast", traits: previewTraits(.dark, .high))
}
