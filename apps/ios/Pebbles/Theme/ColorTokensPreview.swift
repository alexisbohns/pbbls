import SwiftUI

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
                        .strokeBorder(Color.primary.opacity(0.08))
                )
            Text(name)
                .font(.caption2.monospaced())
                .foregroundStyle(.primary)
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
