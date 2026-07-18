import SwiftUI

/// Body of the pebble read view. Pure UI — receives a fully-loaded
/// `PebbleDetail` plus its resolved emotion `palette` and lays out the sections
/// per spec `docs/superpowers/specs/2026-04-29-ios-pebble-read-view-polish-design.md`.
///
/// The whole page tints to the pebble's emotion palette (#605) — background plus
/// every text / tile / soul color resolves through `pebblePageColors(for:)`. On
/// a palette cache miss (`palette` nil) the page falls back to the system/accent
/// chrome.
///
/// `PebbleDetailSheet` wraps this view with the navigation bar (privacy
/// chip + edit button) and handles loading/error states.
struct PebbleReadView: View {
    let detail: PebbleDetail
    let palette: EmotionPalette?

    @Environment(\.colorScheme) private var colorScheme

    /// #605 page colors for the current scheme, or `nil` on a palette cache
    /// miss — every leaf falls back to its system/accent chrome then.
    private var pageColors: PebblePageColors? {
        palette?.pebblePageColors(for: colorScheme)
    }

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

                PebbleReadTitle(
                    name: detail.name,
                    happenedAt: detail.happenedAt,
                    nameColor: pageColors?.title,
                    dateColor: pageColors?.date
                )

                metadataRow

                if let description = detail.description, !description.isEmpty {
                    Text(description)
                        .font(.system(size: 17, weight: .regular, design: .serif))
                        .foregroundStyle(pageColors?.description ?? Color.system.foreground)
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
        // The ScrollView frame fills the sheet (content is inset by the safe
        // area, the frame is not), so this background carries the #605 page tint
        // across the whole page — behind the transparent nav bar and into the
        // bottom inset — meeting the Petroglyph's palette seamlessly. Falls back
        // to the system background on a palette cache miss.
        .background(pageColors?.background ?? Color.system.background)
    }

    /// Emotion / domain / collection as the shared `SurfaceTile`s — the same
    /// tiles used for the profile shortcuts and glyph swap stats (issue #513).
    /// The read page tints them to the emotion palette (#605); the muted
    /// "No domain" placeholder keeps its muted icon/label on the tinted surface.
    @ViewBuilder
    private var metadataRow: some View {
        HStack(spacing: Spacing.sm) {
            // Emotion — always present.
            SurfaceTile(
                systemImage: "heart.fill",
                backgroundColor: pageColors?.tileBackground,
                iconTint: pageColors?.tileIcon,
                labelColor: pageColors?.tileLabel
            ) {
                Text(LocalizedStringResource(stringLiteral: detail.emotion.localizedName))
            }

            // Domain — always rendered. Muted placeholder when unset.
            if detail.domains.isEmpty {
                SurfaceTile(
                    systemImage: "tag.fill",
                    muted: true,
                    backgroundColor: pageColors?.tileBackground
                ) {
                    Text("No domain")
                }
            } else {
                SurfaceTile(
                    systemImage: "tag.fill",
                    backgroundColor: pageColors?.tileBackground,
                    iconTint: pageColors?.tileIcon,
                    labelColor: pageColors?.tileLabel
                ) {
                    Text(LocalizedStringResource(
                        stringLiteral: detail.domains.map(\.localizedName).joined(separator: ", ")
                    ))
                }
            }

            // Collections — only when non-empty.
            if !detail.collections.isEmpty {
                SurfaceTile(
                    systemImage: "square.stack.3d.up.fill",
                    backgroundColor: pageColors?.tileBackground,
                    iconTint: pageColors?.tileIcon,
                    labelColor: pageColors?.tileLabel
                ) {
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
                DetailSoulCell(
                    soul: soulWithGlyph,
                    glyphColor: pageColors?.soulGlyph,
                    nameColor: pageColors?.soulName
                )
            }
        }
    }
}

/// One soul cell on the read page — ports the shared `SoulItem(case: .default)`
/// look (glyph above its name in the hand font, no pebble count) with the glyph
/// and name tinted to the emotion palette (#605). `glyphColor` / `nameColor`
/// fall back to `system.secondary` on a palette cache miss, reproducing the
/// shared cell's default chrome. Dedicated (rather than tinting the shared
/// `SoulItem` / `GlyphView`) so the picker's spec-governed state colors stay
/// untouched — the glyph rides `GlyphThumbnail`, which already takes a color.
private struct DetailSoulCell: View {
    let soul: SoulWithGlyph
    var glyphColor: Color? = nil
    var nameColor: Color? = nil

    var body: some View {
        VStack(spacing: Spacing.sm) {
            GlyphThumbnail(
                strokes: soul.glyph.strokes,
                side: 96,
                strokeColor: glyphColor ?? Color.system.secondary
            )
            Text(soul.name)
                .pebblesFont(.bodyLeadHand)
                .foregroundStyle(nameColor ?? Color.system.secondary)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(soul.name)
    }
}
