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
                    emotionColorHex: detail.emotion.color,
                    valence: detail.valence
                )

                PebbleReadTitle(name: detail.name, happenedAt: detail.happenedAt)

                metadataRow

                if let description = detail.description, !description.isEmpty {
                    Text(description)
                        .font(.system(size: 17, weight: .regular, design: .serif))
                        .foregroundStyle(Color.pebblesForeground)
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
        .background(Color.pebblesBackground)
    }

    @ViewBuilder
    private var metadataRow: some View {
        PebblePillFlow {
            // Emotion — always present.
            PebbleMetaPill(
                icon: .system("heart.fill"),
                label: LocalizedStringResource(stringLiteral: detail.emotion.localizedName),
                style: .emotion(color: Color(hex: detail.emotion.color) ?? Color.pebblesAccent)
            )

            // Domain — always rendered. Set when non-empty, else dashed unset.
            if detail.domains.isEmpty {
                PebbleMetaPill(
                    icon: .system("square.grid.2x2"),
                    label: "No domain",
                    style: .unset
                )
            } else {
                PebbleMetaPill(
                    icon: .system("square.grid.2x2"),
                    label: LocalizedStringResource(
                        stringLiteral: detail.domains.map(\.localizedName).joined(separator: ", ")
                    ),
                    style: .neutral
                )
            }

            // Collections — only when non-empty.
            if !detail.collections.isEmpty {
                PebbleMetaPill(
                    icon: .system("folder.fill"),
                    label: LocalizedStringResource(
                        stringLiteral: detail.collections.map(\.name).joined(separator: ", ")
                    ),
                    style: .neutral
                )
            }
        }
    }

    @ViewBuilder
    private var soulsRow: some View {
        PebblePillFlow {
            ForEach(detail.souls) { soulWithGlyph in
                PebbleMetaPill(
                    icon: .glyph(soulWithGlyph.glyph),
                    label: LocalizedStringResource(stringLiteral: soulWithGlyph.name),
                    style: .neutral
                )
            }
        }
    }
}
