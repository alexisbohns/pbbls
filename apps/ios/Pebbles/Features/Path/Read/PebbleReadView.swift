import SwiftUI

/// Body of the pebble read view. Pure UI — receives a fully-loaded
/// `MomentDetail` plus its resolved emotion `palette` and lays out the sections
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
    let detail: MomentDetail
    let palette: EmotionPalette?
    /// Offered on a bare moment: opens the Complete flow.
    var onComplete: (() -> Void)? = nil

    @Environment(\.colorScheme) private var colorScheme
    @Environment(ReferenceDataService.self) private var refs

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
                    pebble: detail.pebble
                )

                PebbleReadTitle(
                    name: detail.name,
                    happenedAt: detail.happenedAt,
                    nameColor: pageColors?.title,
                    dateColor: pageColors?.date
                )

                if let pebble = detail.pebble {
                    metadataRow(pebble)
                }

                if let description = detail.description, !description.isEmpty {
                    Text(description)
                        .font(.system(size: 17, weight: .regular, design: .serif))
                        .foregroundStyle(pageColors?.description ?? Color.pebbles.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if detail.pebble == nil, let onComplete {
                    Button("Make it a pebble", action: onComplete)
                        .buttonStyle(PebblesPrimaryButtonStyle())
                        .padding(.top, Spacing.sm)
                }

                if let pebble = detail.pebble, !pebble.souls.isEmpty {
                    soulsRow(pebble)
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
        .background(pageColors?.background ?? Color.pebbles.surface)
    }

    /// Emotion / domain / collection as the shared `SurfaceTile`s — the same
    /// tiles used for the profile shortcuts and glyph swap stats (issue #513).
    /// The read page tints them to the emotion palette (#605); the muted
    /// "No domain" placeholder keeps its muted icon/label on the tinted surface.
    @ViewBuilder
    private func metadataRow(_ pebble: MomentDetail.Pebble) -> some View {
        HStack(spacing: Spacing.sm) {
            // Emotion — always present.
            SurfaceTile(
                systemImage: "heart.fill",
                backgroundColor: pageColors?.tileBackground,
                iconTint: pageColors?.tileIcon,
                labelColor: pageColors?.tileLabel
            ) {
                Text(LocalizedStringResource(stringLiteral: pebble.emotion.localizedName))
            }

            // Domain — always rendered. Muted placeholder when unset.
            if pebble.domains.isEmpty {
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
                        stringLiteral: pebble.domains.map(\.localizedName).joined(separator: ", ")
                    ))
                }
            }

            // Collections — only when non-empty.
            if !pebble.collections.isEmpty {
                SurfaceTile(
                    systemImage: "square.stack.3d.up.fill",
                    backgroundColor: pageColors?.tileBackground,
                    iconTint: pageColors?.tileIcon,
                    labelColor: pageColors?.tileLabel
                ) {
                    Text(LocalizedStringResource(
                        stringLiteral: pebble.collections.map(\.name).joined(separator: ", ")
                    ))
                }
            }
        }
    }

    @ViewBuilder
    private func soulsRow(_ pebble: MomentDetail.Pebble) -> some View {
        LazyVGrid(columns: SoulPillGrid.columns, spacing: SoulPillGrid.spacing) {
            ForEach(pebble.souls) { soul in
                DetailSoulCell(
                    name: soul.name,
                    // v_moments_full carries {id, name}; the glyph comes from
                    // the cached soul list (A6). Unknown → empty glyph.
                    strokes: refs.souls.first { $0.id == soul.id }?.glyph.strokes ?? [],
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
    let name: String
    let strokes: [GlyphStroke]
    var glyphColor: Color? = nil
    var nameColor: Color? = nil

    var body: some View {
        VStack(spacing: Spacing.sm) {
            GlyphThumbnail(
                strokes: strokes,
                side: 96,
                strokeColor: glyphColor ?? Color.pebbles.onSurfaceVariant
            )
            Text(name)
                .pebblesFont(.bodyLeadHand)
                .foregroundStyle(nameColor ?? Color.pebbles.onSurfaceVariant)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(name)
    }
}
