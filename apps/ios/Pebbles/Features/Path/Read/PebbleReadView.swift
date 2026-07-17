import SwiftUI

/// Body of the pebble read view. Pure UI — receives a fully-loaded
/// `PebbleDetail` and lays out the sections per spec
/// `docs/superpowers/specs/2026-04-29-ios-pebble-read-view-polish-design.md`.
///
/// `PebbleDetailSheet` wraps this view with the navigation bar (privacy
/// chip + edit button) and handles loading/error states.
struct PebbleReadView: View {
    let detail: PebbleDetail

    /// Flips true once `PebbleReadBanner` reports the snap + pebble are ready.
    /// Drives the whole-page reveal cascade. Kept false until then so the
    /// placeholder→image settling happens off-screen (no visible reflow), and
    /// the content then slides+fades in progressively rather than popping in.
    @State private var revealed = false

    // Cascade step indices — the order the page reveals in. Tiles and souls
    // each advance the step per element ("one by one").
    private let stepBanner = 0
    private let stepTitle = 1
    private let stepTilesBase = 2      // emotion, domain, collection → 2, 3, 4
    private let stepDescription = 5
    private let stepSoulsBase = 6      // then one step per soul

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                PebbleReadBanner(
                    snapStoragePath: detail.snaps.first?.storagePath,
                    renderSvg: detail.renderSvg,
                    renderVersion: detail.renderVersion,
                    emotionId: detail.emotion.id,
                    valence: detail.valence,
                    onReady: { revealed = true }
                )
                .cascade(step: stepBanner, revealed: revealed)

                PebbleReadTitle(name: detail.name, happenedAt: detail.happenedAt)
                    .cascade(step: stepTitle, revealed: revealed)

                metadataRow

                if let description = detail.description, !description.isEmpty {
                    Text(description)
                        .font(.system(size: 17, weight: .regular, design: .serif))
                        .foregroundStyle(Color.system.foreground)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .cascade(step: stepDescription, revealed: revealed)
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
            .cascade(step: stepTilesBase, revealed: revealed)

            // Domain — always rendered. Muted placeholder when unset.
            Group {
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
            }
            .cascade(step: stepTilesBase + 1, revealed: revealed)

            // Collections — only when non-empty.
            if !detail.collections.isEmpty {
                SurfaceTile(systemImage: "square.stack.3d.up.fill") {
                    Text(LocalizedStringResource(
                        stringLiteral: detail.collections.map(\.name).joined(separator: ", ")
                    ))
                }
                .cascade(step: stepTilesBase + 2, revealed: revealed)
            }
        }
    }

    @ViewBuilder
    private var soulsRow: some View {
        LazyVGrid(columns: SoulPillGrid.columns, spacing: SoulPillGrid.spacing) {
            ForEach(Array(detail.souls.enumerated()), id: \.element.id) { index, soulWithGlyph in
                SoulItem(case: .default, soul: soulWithGlyph, count: nil)
                    .cascade(step: stepSoulsBase + index, revealed: revealed)
            }
        }
    }
}

/// Staggered slide-fade reveal for the pebble page. Each element declares its
/// `step` in the cascade; when `revealed` flips true they animate in one after
/// another. The transform (opacity + a small upward slide) never changes layout,
/// so the page never reflows — it only progressively appears.
private struct CascadeReveal: ViewModifier {
    let step: Int
    let revealed: Bool

    /// The banner (step 0) reveals immediately; the text below waits a beat so
    /// the picture + pebble read as "displayed first", then cascades.
    private var delay: Double {
        guard step > 0 else { return 0 }
        return 0.3 + Double(step - 1) * 0.08
    }

    func body(content: Content) -> some View {
        content
            .opacity(revealed ? 1 : 0)
            .offset(y: revealed ? 0 : 12)
            .animation(.easeOut(duration: 0.5).delay(delay), value: revealed)
    }
}

private extension View {
    func cascade(step: Int, revealed: Bool) -> some View {
        modifier(CascadeReveal(step: step, revealed: revealed))
    }
}
