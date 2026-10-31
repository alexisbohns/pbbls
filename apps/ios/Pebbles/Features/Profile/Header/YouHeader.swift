import SwiftUI

/// You's head (#1382 D17): the settings stone top-right, the profile glyph in
/// a squircle pentagon, the name in the hand, "Member since", and the Ripples
/// level on one line. The pentagon is decorative; the name is the screen's
/// header; the Ripples line is one element that speaks the badge's phrase.
struct YouHeader: View {
    let name: String?
    let memberSince: Date?
    let glyphStrokes: [GlyphStroke]?
    let rippleLevel: Int?
    let activeToday: Bool
    var onSettings: () -> Void = {}

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button(action: onSettings) {
                    PbblsIconImage(.slider, .toolbar, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
                        .frame(width: 46, height: 44)
                        .background {
                            StoneShape(seed: "settings", options: StoneOptions(corner: 16))
                                .fill(Color.pebbles.surfaceContainerHigh)
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Settings"))
            }
            // c3 pulls the pentagon up under the button.
            .padding(.bottom, -18)

            pentagon
            nameLine.padding(.top, 14)
            if let memberSince {
                Text("Member since \(memberSince.formatted(.dateTime.month(.wide).year()))")
                    .spineType(.memberSince)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)
            }
            ripples.padding(.top, 14)
        }
        .frame(maxWidth: .infinity)
    }

    private var pentagon: some View {
        SquirclePentagon()
            .fill(Color.pebbles.primaryContainer)
            .frame(width: 128, height: 122)
            .overlay {
                Group {
                    if let glyphStrokes, !glyphStrokes.isEmpty {
                        GlyphThumbnail(strokes: glyphStrokes, side: 58, strokeColor: Color.pebbles.onPrimaryContainer)
                    } else {
                        PbblsIconImage(.pebbleDottedGlyph, size: 58,
                                       style: AnyShapeStyle(Color.pebbles.onPrimaryContainer))
                    }
                }
                .padding(.top, 8)
            }
            .accessibilityHidden(true)
    }

    private var nameLine: some View {
        Text(verbatim: name ?? "")
            .pebblesFont(.largeTitleHand)
            .foregroundStyle(Color.pebbles.onSurface)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .accessibilityAddTraits(.isHeader)
    }

    private var ripples: some View {
        let level = rippleLevel ?? 0
        let badge = RippleBadge(level: level, activeToday: activeToday, side: 22)
        return HStack(spacing: 7) {
            badge
            Text("Ripples Level \(level)")
                .spineType(.rowLabel)
                .foregroundStyle(Color.pebbles.onSurface)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(badge.accessibilityLabel))
    }
}
