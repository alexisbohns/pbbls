#if DEBUG
import SwiftUI

/// The stone lab (#974): nine stones under one light, one emotion palette at
/// a time, with the material knobs beside them. Debug-only. Nothing on this
/// page is time-driven; the only animation is the light's spring back to
/// rest on release.
struct StoneLabView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var paletteIndex = 2 // joy: the warmest, easiest to judge
    @State private var light: StoneLight = .rest
    @State private var materials: [ValencePolarity: StoneMaterial] = Dictionary(
        uniqueKeysWithValues: ValencePolarity.allCases.map { ($0, StoneMaterial.starting(for: $0)) }
    )
    @State private var tones: [ValencePolarity: StoneTones] = Dictionary(
        uniqueKeysWithValues: ValencePolarity.allCases.map { ($0, StoneTones.starting(for: $0)) }
    )
    @State private var knobPolarity: ValencePolarity = .neutral
    @State private var isFlat = false
    @State private var isLowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
    @State private var detailValence: Valence?
    @State private var isKnobsOpen = false

    private var palette: StoneLabPalette { StoneLabPalettes.all[paletteIndex] }
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
            .onReceive(NotificationCenter.default.publisher(for: .NSProcessInfoPowerStateDidChange)) { _ in
                isLowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
            }
            .sheet(item: $detailValence) { valence in
                detail(valence)
            }
        }
        .task(priority: .userInitiated) {
            // The nine carvings cost about a second of wobbling; off the main
            // actor, before the grid asks for them.
            await Task.detached(priority: .userInitiated) { StoneLabArt.prewarm() }.value
        }
    }

    // MARK: - Emotion

    private var emotionPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(StoneLabPalettes.all.enumerated()), id: \.element.id) { index, palette in
                    Button {
                        paletteIndex = index
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

    private var grid: some View {
        GeometryReader { proxy in
            let rowHeight = min(130, (proxy.size.height - 32) / 3)
            VStack(spacing: 10) {
                ForEach(ValenceSizeGroup.allCases) { size in
                    HStack(spacing: 10) {
                        ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                            let valence = Valence.allCases.first { $0.sizeGroup == size && $0.polarity == polarity }!
                            stone(valence, height: rowHeight)
                                .frame(maxWidth: .infinity)
                                .onLongPressGesture { detailValence = valence }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(lightDrag(center: CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 2)))
        }
        .padding(.horizontal, 16)
    }

    private func stone(_ valence: Valence, height: CGFloat) -> some View {
        StoneView(
            valence: valence,
            palette: palette,
            material: materials[valence.polarity] ?? .starting(for: valence.polarity),
            tones: tones[valence.polarity] ?? .starting(for: valence.polarity),
            light: light,
            height: height,
            isFlat: flat
        )
    }

    /// The light sits where the finger is, relative to the grid's centre.
    /// Release springs it home so screenshots are repeatable.
    private func lightDrag(center: CGPoint) -> some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                let offset = CGVector(dx: value.location.x - center.x, dy: value.location.y - center.y)
                light = StoneLight(pointingTo: offset, elevationDegrees: light.elevationDegrees)
            }
            .onEnded { _ in
                withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                    light = StoneLight(direction: StoneLight.rest.direction, elevationDegrees: light.elevationDegrees)
                }
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
                    for polarity in ValencePolarity.allCases {
                        materials[polarity] = .starting(for: polarity)
                        tones[polarity] = .starting(for: polarity)
                    }
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
            get: { materials[knobPolarity] ?? .starting(for: knobPolarity) },
            set: { materials[knobPolarity] = $0 }
        )
    }

    private var tonesBinding: Binding<StoneTones> {
        Binding(
            get: { tones[knobPolarity] ?? .starting(for: knobPolarity) },
            set: { tones[knobPolarity] = $0 }
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
            knob("Crack", m.crack, 0...0.3)
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

    private func detail(_ valence: Valence) -> some View {
        VStack(spacing: 16) {
            Text(verbatim: valence.assetName).font(.caption).foregroundStyle(.secondary)
            stone(valence, height: 320)
                .contentShape(Rectangle())
                .gesture(lightDrag(center: CGPoint(x: 180, y: 160)))
            Spacer()
        }
        .padding(24)
        .presentationDetents([.large])
    }
}
#endif
