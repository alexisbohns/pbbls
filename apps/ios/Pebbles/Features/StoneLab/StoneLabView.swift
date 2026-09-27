#if DEBUG
import SwiftUI

/// The stone lab (#974): nine stones under one light, one emotion palette at
/// a time, with the material knobs beside them. Debug-only. Nothing on this
/// page is time-driven or animated: the stones redraw while the finger moves
/// the light, and not after.
struct StoneLabView: View {
    @Environment(\.dismiss) private var dismiss

    // The knobs live in `StoneLabSettings`, restored from the device on
    // open and saved on every change, so a restart does not lose a pick.
    @State private var settings = StoneLabSettings.load()
    @State private var light: StoneLight = .rest
    @State private var knobPolarity: ValencePolarity = .neutral
    @State private var isFlat = false
    @State private var isLowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
    @State private var detailValence: Valence?
    @State private var isKnobsOpen = false
    /// False until the nine carvings are built off the main actor, so the
    /// first open never wobbles them on the main thread.
    @State private var isArtReady = false

    private var palette: StoneLabPalette {
        StoneLabPalettes.all[min(max(settings.paletteIndex, 0), StoneLabPalettes.all.count - 1)]
    }
    private var paletteIndex: Int { settings.paletteIndex }
    private var flat: Bool { isFlat || isLowPower }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                emotionPicker
                grid
                knobs
            }
            .navigationTitle(Text(verbatim: "Stone lab"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { dismiss() } label: { Text(verbatim: "Close") }
                }
            }
            .onReceive(
                NotificationCenter.default.publisher(for: .NSProcessInfoPowerStateDidChange)
                    .receive(on: DispatchQueue.main)
            ) { _ in
                isLowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
            }
            .sheet(item: $detailValence) { valence in
                detail(valence)
            }
            .onChange(of: settings) { _, new in new.save() }
        }
        .task(priority: .userInitiated) {
            // The nine carvings cost about a second of wobbling; off the main
            // actor, before the grid asks for them.
            await Task.detached(priority: .userInitiated) { StoneLabArt.prewarm() }.value
            isArtReady = true
        }
    }

    // MARK: - Emotion

    private var emotionPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(StoneLabPalettes.all.enumerated()), id: \.element.id) { index, palette in
                    Button {
                        settings.paletteIndex = index
                    } label: {
                        Text(verbatim: palette.slug.capitalized)
                            .font(.footnote.weight(.medium))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                Capsule().fill(palette.color(.primary).opacity(index == paletteIndex ? 1 : 0.18))
                            )
                            .foregroundStyle(index == paletteIndex ? palette.color(.light) : palette.color(.dark))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
    }

    // MARK: - Grid

    private static let gridSpacing: CGFloat = 10

    @ViewBuilder
    private var grid: some View {
        if isArtReady {
            stoneGrid
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var stoneGrid: some View {
        GeometryReader { proxy in
            VStack(spacing: Self.gridSpacing) {
                ForEach(ValenceSizeGroup.allCases) { size in
                    HStack(spacing: Self.gridSpacing) {
                        ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                            let valence = Valence.allCases.first { $0.sizeGroup == size && $0.polarity == polarity }!
                            stone(valence, height: Self.rowHeight(for: size, in: proxy.size))
                                .frame(maxWidth: .infinity)
                                .onLongPressGesture { detailValence = valence }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            // Simultaneous, so a drag that starts on a stone is not held back
            // while the stone's long press decides.
            .simultaneousGesture(lightDrag(center: CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 2)))
        }
        .padding(.horizontal, 16)
    }

    /// A row's stone height. The three rows share the grid height in the
    /// fan's own proportions (`ValenceFanLayout.stoneHeight`), so the size
    /// axis reads the same here as on the record step; each row is then
    /// capped so its stone, at its silhouette's aspect ratio, never grows
    /// wider than its third of the width.
    private static func rowHeight(for size: ValenceSizeGroup, in grid: CGSize) -> CGFloat {
        let fanTotal = ValenceSizeGroup.allCases.reduce(CGFloat(0)) { $0 + ValenceFanLayout.stoneHeight(for: $1) }
        let available = max(0, grid.height - 2 * gridSpacing)
        let fitsHeight = available * ValenceFanLayout.stoneHeight(for: size) / fanTotal
        let columnWidth = max(0, (grid.width - 2 * gridSpacing) / 3)
        let fitsWidth = columnWidth / CGFloat(PebbleOutlineGeometry.aspectRatio(for: size))
        return max(0, min(fitsHeight, fitsWidth))
    }

    private func stone(_ valence: Valence, height: CGFloat) -> some View {
        StoneView(
            valence: valence,
            palette: palette,
            material: settings.material(for: valence.polarity),
            tones: settings.tones(for: valence.polarity),
            light: light,
            height: height,
            isFlat: flat
        )
    }

    /// The light sits where the finger is, relative to the grid's centre.
    /// Release puts it straight back to the rest direction so screenshots are
    /// repeatable. No animation: `StoneLight` is not animatable, so the
    /// material would snap anyway.
    private func lightDrag(center: CGPoint) -> some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                let offset = CGVector(dx: value.location.x - center.x, dy: value.location.y - center.y)
                light = StoneLight(pointingTo: offset, elevationDegrees: light.elevationDegrees)
            }
            .onEnded { _ in
                light = StoneLight(direction: StoneLight.rest.direction, elevationDegrees: light.elevationDegrees)
            }
    }

    // MARK: - Knobs

    private var knobs: some View {
        VStack(spacing: 0) {
            HStack {
                Toggle(isOn: $isFlat) { Text(verbatim: "Flat") }.toggleStyle(.button)
                Text(verbatim: isLowPower ? "Low Power: on" : "Low Power: off")
                    .font(.caption).foregroundStyle(.secondary)
                Spacer()
                Button { isKnobsOpen.toggle() } label: { Text(verbatim: isKnobsOpen ? "Hide knobs" : "Knobs") }
                Button {
                    UIPasteboard.general.string = settings.summary
                } label: {
                    Text(verbatim: "Copy")
                }
                Button {
                    settings = StoneLabSettings()
                    light = .rest
                } label: {
                    Text(verbatim: "Reset")
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            if isKnobsOpen {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Picker(selection: $knobPolarity) {
                            ForEach(ValencePolarity.allCases, id: \.self) {
                                Text(verbatim: $0.rawValue.capitalized).tag($0)
                            }
                        } label: {
                            Text(verbatim: "Polarity")
                        }
                        .pickerStyle(.segmented)
                        materialKnobs
                        toneKnobs
                        lightKnobs
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
                .frame(maxHeight: 280)
            }
        }
        .background(.bar)
    }

    private var materialBinding: Binding<StoneMaterial> {
        Binding(
            get: { settings.material(for: knobPolarity) },
            set: { settings.materials[knobPolarity.rawValue] = $0 }
        )
    }

    private var tonesBinding: Binding<StoneTones> {
        Binding(
            get: { settings.tones(for: knobPolarity) },
            set: { settings.tones[knobPolarity.rawValue] = $0 }
        )
    }

    @ViewBuilder
    private var materialKnobs: some View {
        let m = materialBinding
        Picker(selection: m.kind) {
            ForEach(StoneMaterial.Kind.allCases) { Text(verbatim: $0.label).tag($0) }
        } label: {
            Text(verbatim: "Material")
        }
        .pickerStyle(.segmented)
        knob("Scale", m.scale, 0.01...0.6)
        knob("Relief", m.relief, 0...3)
        knob("Contrast", m.contrast, 0...1)
        knob("Sheen", m.sheen, 0...1)
        knob("Lip width", m.lipWidth, 0...4)
        knob("Lip opacity", m.lipOpacity, 0...1)
        switch m.wrappedValue.kind {
        case .lava:
            knob("Pits", m.pits, 0...1)
        case .river:
            knob("Banding", m.banding, 0...1)
        case .gem:
            knob("Facet density", m.facetDensity, 0.3...3)
            knob("Glitter", m.glitter, 0...1)
        }
    }

    @ViewBuilder
    private var toneKnobs: some View {
        let t = tonesBinding
        tonePick("Body", t.body)
        tonePick("Ink", t.ink)
        tonePick("Lip light", t.lipLight)
        tonePick("Lip shadow", t.lipShadow)
    }

    @ViewBuilder
    private var lightKnobs: some View {
        knob("Light elevation", Binding(
            get: { light.elevationDegrees },
            set: { light = StoneLight(direction: light.direction, elevationDegrees: $0) }
        ), 10...85)
    }

    private func knob(_ label: String, _ value: Binding<Double>, _ range: ClosedRange<Double>) -> some View {
        HStack {
            Text(verbatim: label).font(.caption).frame(width: 96, alignment: .leading)
            Slider(value: value, in: range)
            Text(verbatim: value.wrappedValue.formatted(.number.precision(.fractionLength(2))))
                .font(.caption.monospacedDigit()).frame(width: 44, alignment: .trailing)
        }
    }

    private func tonePick(_ label: String, _ pick: Binding<StoneTones.Pick>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: label).font(.caption)
            Picker(selection: pick.slot) {
                ForEach(StoneLabPalette.Slot.allCases) { Text(verbatim: $0.rawValue).tag($0) }
            } label: {
                Text(verbatim: label)
            }
            .pickerStyle(.segmented)
            knob("opacity", pick.opacity, 0...1)
        }
    }

    // MARK: - Detail

    /// The stone at up to 320 pt tall, narrowed to the sheet's width when its
    /// silhouette would not fit; the light drags about the stone's own centre.
    private func detail(_ valence: Valence) -> some View {
        let aspect = CGFloat(PebbleOutlineGeometry.aspectRatio(for: valence.sizeGroup))
        return VStack(spacing: 16) {
            Text(verbatim: valence.assetName).font(.caption).foregroundStyle(.secondary)
            GeometryReader { proxy in
                let width = max(0, min(proxy.size.width, 320 * aspect))
                let height = width / aspect
                stone(valence, height: height)
                    .contentShape(Rectangle())
                    .gesture(lightDrag(center: CGPoint(x: width / 2, y: height / 2)))
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(24)
        .presentationDetents([.large])
    }
}
#endif
