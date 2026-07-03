import SwiftUI

/// Body of the pebble read view. Pure UI — receives a fully-loaded
/// `PebbleDetail` and lays out the sections per spec
/// `docs/superpowers/specs/2026-04-29-ios-pebble-read-view-polish-design.md`.
///
/// `PebbleDetailSheet` wraps this view with the navigation bar (privacy
/// chip + edit button) and handles loading/error states.
struct PebbleReadView: View {
    let detail: PebbleDetail

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                PebbleReadBanner(
                    snapStoragePath: detail.snaps.first?.storagePath,
                    renderSvg: detail.renderSvg,
                    renderVersion: detail.renderVersion,
                    emotionId: detail.emotion.id,
                    valence: detail.valence
                )

                PebbleReadTitle(name: detail.name, happenedAt: detail.happenedAt)

                metadataRow

                if let description = detail.description, !description.isEmpty {
                    Text(description)
                        .font(.system(size: 17, weight: .regular, design: .serif))
                        .foregroundStyle(Color.system.foreground)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if !detail.souls.isEmpty {
                    soulsRow
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 32)
        }
        .background(Color.system.background)
    }

    /// Emotion / domain / collection as the shared `SurfaceTile`s — the same
    /// tiles used for the profile shortcuts and glyph swap stats (issue #513).
    /// Page-wide emotion palette coloring is intentionally out of scope, so the
    /// emotion tile keeps the default accent surface like its neighbours.
    @ViewBuilder
    private var metadataRow: some View {
        HStack(spacing: Spacing.sm) {
            // Emotion — always present.
            SurfaceTile(systemImage: "heart.fill") {
                Text(LocalizedStringResource(stringLiteral: detail.emotion.localizedName))
            }

            // Domain — always rendered. Muted placeholder when unset.
            if detail.domains.isEmpty {
                SurfaceTile(systemImage: "tag.fill", muted: true) {
                    Text("No domain")
                }
            } else {
                SurfaceTile(systemImage: "tag.fill") {
                    Text(LocalizedStringResource(
                        stringLiteral: detail.domains.map(\.localizedName).joined(separator: ", ")
                    ))
                }
            }

            // Collections — only when non-empty.
            if !detail.collections.isEmpty {
                SurfaceTile(systemImage: "square.stack.3d.up.fill") {
                    Text(LocalizedStringResource(
                        stringLiteral: detail.collections.map(\.name).joined(separator: ", ")
                    ))
                }
            }
        }
    }

    @ViewBuilder
    private var soulsRow: some View {
        LazyVGrid(columns: SoulPillGrid.columns, spacing: SoulPillGrid.spacing) {
            ForEach(detail.souls) { soulWithGlyph in
                SoulItem(case: .default, soul: soulWithGlyph, count: nil)
            }
        }
    }
}
