import SwiftUI

/// Path-specific pebble row, used by `WeekPathView`. Renders three
/// states based on the pebble's `intensity`:
///   - intensity 1–2 (small/medium): 56pt thumbnail backed by
///     `PebbleOutlineBackdropView` with `palette.surface` fill and
///     `palette.secondary` glyph stroke; name color follows the scheme
///     (light=primary, dark=light).
///   - intensity 3 (large): 96pt thumbnail with `palette.primary` fill and
///     `palette.light` glyph stroke; name color is `palette.light` in both
///     schemes (the primary fill carries scheme contrast).
///
/// Colors are sourced from `palette.pebbleFrameColors(forIntensity:)`.
///
/// When `pebble.firstSnapPath` is non-nil, a 64pt photo is rendered to
/// the right with rotation by parity (even = -7°, odd = +4°) and a white
/// border + drop shadow. Row height grows to fit the rotated photo per
/// `rowHeight(intensity:hasPhoto:positionIndex:)`.
///
/// Long-press surfaces a delete option via `.contextMenu` — the parent
/// `PathView` owns the confirmation dialog.
struct PathPebbleRow: View {
    let pebble: Pebble
    let positionIndex: Int
    let onTap: () -> Void
    let onDelete: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.colorScheme) private var colorScheme

    private static let smallThumbnailSize: CGFloat = 56
    private static let largeThumbnailSize: CGFloat = 96
    private static let photoSize: CGFloat = 64

    private var isLarge: Bool { pebble.intensity >= 3 }
    private var thumbnailSize: CGFloat { isLarge ? Self.largeThumbnailSize : Self.smallThumbnailSize }
    private var hasPhoto: Bool { pebble.firstSnapPath != nil }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                thumbnail
                VStack(alignment: .leading, spacing: 4) {
                    Text(pebble.name)
                        .font(.ysabeauSemibold(17))
                        .foregroundStyle(nameColor)
                    Text(formattedWeekdayTime)
                        .font(.caption)
                        .tracking(1.0)
                        .textCase(.uppercase)
                        .foregroundStyle(nameColor.opacity(0.5))
                }
                if hasPhoto, let path = pebble.firstSnapPath {
                    Spacer(minLength: 0)
                    photoView(path: path)
                }
            }
            .frame(
                height: PathPebbleRow.rowHeight(
                    intensity: pebble.intensity,
                    hasPhoto: hasPhoto,
                    positionIndex: positionIndex
                ),
                alignment: .center
            )
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button(role: .destructive, action: onDelete) {
                Label("Delete", systemImage: "trash")
            }
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        ZStack {
            PebbleOutlineBackdropView(
                size: pebble.valence.sizeGroup,
                polarity: pebble.valence.polarity,
                fillHex: frameColors?.fillHex ?? Color.accent.primaryHex,
                fillOpacity: frameColors?.fillOpacity ?? 1
            )
            if let svg = pebble.renderSvg {
                let strokeHex = frameColors?.strokeHex ?? Color.accent.primaryHex
                PebbleStaticRenderView(
                    svg: svg,
                    strokeColor: Color(hex: strokeHex) ?? Color.accent.primary,
                    strokeColorHex: strokeHex
                )
                .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: pebble.valence.sizeGroup))
            }
        }
        .aspectRatio(PebbleOutlineGeometry.aspectRatio(for: pebble.valence.sizeGroup), contentMode: .fit)
        .frame(width: thumbnailSize, height: thumbnailSize)
    }

    @ViewBuilder
    private func photoView(path: String) -> some View {
        PathPebbleSnapThumb(storagePath: path)
            .frame(width: Self.photoSize, height: Self.photoSize)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.white, lineWidth: 4)
            )
            .shadow(color: Color.black.opacity(0.18), radius: 6, x: 0, y: 2)
            .rotationEffect(.degrees(PathPebbleRow.rotationAngle(forPositionIndex: positionIndex)))
    }

    private var palette: EmotionPalette? {
        guard let emotionId = pebble.emotion?.id else { return nil }
        return palettes.palette(for: emotionId)
    }

    private var frameColors: PebbleFrameColors? {
        palette?.pebbleFrameColors(forIntensity: pebble.intensity)
    }

    private var nameColor: Color {
        guard let palette else { return Color.system.foreground }
        if isLarge { return palette.light }
        return colorScheme == .dark ? palette.light : palette.primary
    }

    /// Weekday + time only — the focused week is already known from
    /// `WeekHeaderView`, so day/month would be redundant.
    private var formattedWeekdayTime: String {
        let weekday = pebble.happenedAt.formatted(.dateTime.weekday(.wide))
        let time    = pebble.happenedAt.formatted(.dateTime.hour().minute())
        return "\(weekday) · \(time)"
    }
}

#Preview {
    let supabase = SupabaseService()
    return List {
        Section {
            PathPebbleRow(
                pebble: Pebble(
                    id: UUID(),
                    name: "Sample pebble",
                    happenedAt: Date(),
                    createdAt: Date(),
                    intensity: 1,
                    positiveness: 0,
                    renderSvg: nil,
                    emotion: nil,
                    firstSnapPath: nil
                ),
                positionIndex: 0,
                onTap: {},
                onDelete: {}
            )
            .listRowBackground(Color.clear)
        }
    }
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(supabase)
}

extension PathPebbleRow {

    /// Photo rotation by row position. Even indices (0, 2, 4...) lean
    /// counter-clockwise (-7°); odd lean clockwise (+4°).
    static func rotationAngle(forPositionIndex index: Int) -> Double {
        index.isMultiple(of: 2) ? -7 : 4
    }

    /// Row height by intensity + photo state + parity. Sized to fit the
    /// rotated 64pt photo's bounding box for small/medium rows; large rows
    /// are dominated by the 96pt thumbnail and stay at 100pt.
    static func rowHeight(intensity: Int, hasPhoto: Bool, positionIndex: Int) -> CGFloat {
        if intensity >= 3 { return 100 }
        if !hasPhoto { return 60 }
        return positionIndex.isMultiple(of: 2) ? 71 : 68
    }
}
