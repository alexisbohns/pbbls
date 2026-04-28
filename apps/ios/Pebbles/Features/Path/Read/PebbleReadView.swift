import SwiftUI

/// Body of the pebble read view. Pure UI — receives a fully-loaded
/// `PebbleDetail` and lays out the sections per spec
/// `docs/superpowers/specs/2026-04-28-ios-pebble-read-view-design.md`.
///
/// The `PebbleDetailSheet` wraps this view with the navigation bar (privacy
/// badge + edit button) and handles loading/error states.
struct PebbleReadView: View {
    let detail: PebbleDetail

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                PebbleReadHeader(detail: detail)
                    .padding(.top, 8)

                if let firstSnap = detail.snaps.first {
                    PebbleReadPicture(storagePath: firstSnap.storagePath)
                }

                metadataBlock

                if let description = detail.description, !description.isEmpty {
                    Text(description)
                        .font(.system(size: 17, weight: .regular, design: .serif))
                        .foregroundStyle(Color.pebblesForeground)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
        }
        .background(Color.pebblesBackground)
    }

    @ViewBuilder
    private var metadataBlock: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Emotion — always rendered.
            PebbleMetadataRow(
                icon: .system("heart.fill"),
                label: LocalizedStringResource(stringLiteral: detail.emotion.localizedName),
                style: .emotion(color: Color(hex: detail.emotion.color) ?? Color.pebblesAccent)
            )

            // Domain — always rendered. Set if non-empty, otherwise dashed.
            if detail.domains.isEmpty {
                PebbleMetadataRow(
                    icon: .system("square.grid.2x2"),
                    label: "No domain",
                    style: .unset
                )
            } else {
                PebbleMetadataRow(
                    icon: .system("square.grid.2x2"),
                    label: LocalizedStringResource(
                        stringLiteral: detail.domains.map(\.localizedName).joined(separator: ", ")
                    ),
                    style: .set
                )
            }

            // Collections — only when non-empty.
            if !detail.collections.isEmpty {
                PebbleMetadataRow(
                    icon: .system("folder.fill"),
                    label: LocalizedStringResource(
                        stringLiteral: detail.collections.map(\.name).joined(separator: ", ")
                    ),
                    style: .set
                )
            }

            // Souls — only when non-empty. One row per soul.
            ForEach(detail.souls) { soulWithGlyph in
                PebbleMetadataRow(
                    icon: .glyph(soulWithGlyph.glyph),
                    label: LocalizedStringResource(stringLiteral: soulWithGlyph.name),
                    style: .set
                )
            }
        }
    }
}

// MARK: - Hex color helper

/// Parses `#RRGGBB` strings stored on `EmotionRef.color`. Falls back to
/// `nil` if the format is unexpected — caller decides on a default.
private extension Color {
    init?(hex: String) {
        var trimmed = hex.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("#") { trimmed.removeFirst() }
        guard trimmed.count == 6, let value = UInt32(trimmed, radix: 16) else {
            return nil
        }
        let red   = Double((value >> 16) & 0xFF) / 255.0
        let green = Double((value >> 8) & 0xFF) / 255.0
        let blue  = Double(value & 0xFF) / 255.0
        self.init(red: red, green: green, blue: blue)
    }
}
