import SwiftUI

/// What a Spine row needs from its page and its host (#1130 §View tree).
struct SpineFeedContext {
    let page: SpinePage
    let column: CGFloat
    let palette: (MomentSummary) -> EmotionPalette?
    let ratio: (MomentSummary) -> CGFloat?
    let pressRequests: StonePressRequests
    /// A press asks to open the moment on a face; the host decides what opens (until stack E, the detail sheet).
    let onOpen: (MomentSummary, CardFace) -> Void
    let onDelete: (MomentSummary) -> Void
    /// A masthead or mouth link, a contents tap's page change.
    var onFocus: (Date) -> Void = { _ in }
    /// The empty week's button: opens Capture.
    var onCreate: () -> Void = {}
    /// The day being read (D12): the persons' letter and the contents' dot follow it.
    var readingDay: Int?
}

/// One moment on the Spine (#1130 §View tree, §Press-request state): the stone
/// on the line at its anchor, the 1 pt leader from the line to the column, then
/// the card (a photo) or the entry.
///
/// - A stone press plays the press and, 360 ms later (at once under Reduce
///   Motion), asks to open on the verso (the recto for a bare moment).
/// - A card or entry press asks for the recto at once, and the stone plays its press.
/// - A press request for this moment (a contents tap) is claimed when it
///   appears and plays the press without opening (D8).
///
/// The row never clips (D4): the stone overhangs its top by up to 13.6 pt and
/// stays pressable. VoiceOver reads the stone, then the slot (A2, A3).
struct SpinePebble: View {
    let moment: MomentSummary
    let context: SpineFeedContext
    /// The page's slot focus, for VoiceOver's return to a slot. Nil in the eager
    /// stack: under `ImageRenderer`, `.accessibilityFocused` draws nothing of the
    /// slot it marks, and the references render that stack.
    let slotFocus: AccessibilityFocusState<UUID?>.Binding?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight
    @Environment(CardFrameRegistry.self) private var registry: CardFrameRegistry?
    @State private var press: StonePress?
    @State private var pendingOpen: PendingOpen?
    /// The entry's last reported frame (a card reports its own).
    @State private var reported = ReportedFrame()

    private struct PendingOpen: Equatable {
        let seq: Int
        let face: CardFace
    }

    private var hasCard: Bool { moment.firstSnapPath != nil }
    private var size: ValenceSizeGroup { (moment.pebble?.valence ?? .neutralMedium).sizeGroup }
    private var stonePalette: StonePalette {
        SpineColors.palette(context.palette(moment), isBlank: moment.pebble == nil)
    }

    private var anchor: CGFloat {
        Self.anchor(for: moment, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
    }

    /// 31 for a card; 0.6 × the resolved name size for an entry, so the stone
    /// stays centred on the name's first line at any Dynamic Type size (D11).
    static func anchor(for moment: MomentSummary, dynamicTypeSize: DynamicTypeSize,
                       legibilityWeight: LegibilityWeight?) -> CGFloat {
        if moment.firstSnapPath != nil { return SpineLayout.cardAnchor }
        let name = SpineEntry.type(for: moment).name
        return 0.6 * SpineType.resolve(.entryName(name), dynamicTypeSize: dynamicTypeSize,
                                       legibilityWeight: legibilityWeight).size
    }

    /// The top of the stone's outermost ring at rest in the row's coordinates (negative above the row).
    @MainActor
    static func ringTop(for moment: MomentSummary, dynamicTypeSize: DynamicTypeSize,
                        legibilityWeight: LegibilityWeight?) -> CGFloat {
        let valence = moment.pebble?.valence ?? .neutralMedium
        let stone = SpineLayout.stoneSize(valence.sizeGroup)
        let anchor = anchor(for: moment, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
        return anchor - stone.height / 2 + (StoneRingGeometry.forStone(valence)?.ringTop ?? 0)
    }

    private var description: String {
        SpineStoneDescription.text(moment, names: context.page.soulNames, order: context.page.soulOrder,
                                   calendar: SpineCalendar.current, locale: SpineStrings.appLocale,
                                   dateLocale: SpineStrings.dateLocale)
    }

    var body: some View {
        let row = SpineLayout.stoneRow(anchor: anchor, stone: SpineLayout.stoneSize(size))
        slot
            .padding(.leading, SpineLayout.colX)
            .padding(.trailing, SpineLayout.padRight)
            .frame(maxWidth: .infinity, minHeight: row.minHeight, alignment: .topLeading)
            .overlay(alignment: .topLeading) { leader }
            .overlay(alignment: .topLeading) { stone(row) }
            .overlay(alignment: .topLeading) {
                // The contents' scroll target, at the stone's centre in layout (offsets do not move ids).
                Color.clear.frame(width: 1, height: 1)
                    .padding(.leading, SpineLayout.spineX)
                    .padding(.top, max(0, anchor))
                    .id(Self.stoneAnchor(moment.id))
            }
            .accessibilityElement(children: .contain)
            .stonePressLifetime($press)
            .task(id: context.pressRequests.pending) { claim() }
            .task(id: pendingOpen) {
                guard let open = pendingOpen else { return }
                try? await Task.sleep(for: .seconds(StoneMotion.openDelay))
                guard pendingOpen == open else { return }
                // A row that left a lazy stack keeps its state: a cancelled wait drops its
                // open, or the row would open on its way back in. A newer press has already
                // replaced the id, so this never clears a live one.
                pendingOpen = nil
                guard !Task.isCancelled else { return }
                context.onOpen(moment, open.face)
            }
    }

    static func stoneAnchor(_ id: UUID) -> String { "stone-\(id.uuidString)" }

    // MARK: - Parts

    private var leader: some View {
        Rectangle()
            .fill(Color.pebbles.line)
            .frame(width: SpineLayout.colX - SpineLayout.spineX, height: SpineLayout.line)
            .offset(x: SpineLayout.spineX, y: anchor - SpineLayout.line / 2)
            .accessibilityHidden(true)
    }

    private func stone(_ row: StoneRow) -> some View {
        Button(action: pressStone) {
            SpineStone(moment: moment, palette: context.palette(moment), press: press)
                .frame(width: row.hit.width, height: row.hit.height)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .offset(x: row.hit.minX, y: row.hit.minY)
        .accessibilityLabel(Text(verbatim: description))
        .accessibilitySortPriority(2)
    }

    private var slot: some View {
        Button(action: pressSlot) {
            if hasCard {
                SpineCard(moment: moment, emotion: context.palette(moment), ratio: context.ratio(moment),
                          column: context.column, soulNames: context.page.soulNames,
                          soulGlyphs: context.page.soulGlyphs, soulOrder: context.page.soulOrder)
            } else {
                // A one-line entry can fall under 44 pt; the button keeps the target.
                SpineEntry(moment: moment, palette: stonePalette, soulNames: context.page.soulNames,
                           soulGlyphs: context.page.soulGlyphs, soulOrder: context.page.soulOrder)
                    .frame(minHeight: SpineLayout.minTarget, alignment: .topLeading)
                    .opacity(registry?.hidden == moment.id ? 0 : 1)
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frame in
                        reported.frame = frame
                        registry?.report(moment.id, frame: frame)
                    }
                    .onDisappear {
                        guard let frame = reported.frame else { return }
                        registry?.forget(moment.id, ifAt: frame)
                        reported.frame = nil
                    }
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: description))
        .modifier(SlotFocus(focus: slotFocus, id: moment.id))
        .accessibilitySortPriority(1)
        // Also VoiceOver's "Delete" action, so the slot names no action of its own.
        .pebblesContextMenu([.delete { context.onDelete(moment) }])
    }

    // MARK: - Presses

    private func pressStone() {
        TapHaptics.play(.selection)
        press = .next(after: press, still: reduceMotion, now: .now)
        let face: CardFace = moment.pebble == nil ? .recto : .verso
        if reduceMotion {
            pendingOpen = nil
            context.onOpen(moment, face)
        } else {
            pendingOpen = PendingOpen(seq: press?.seq ?? 0, face: face)
        }
    }

    private func pressSlot() {
        press = .next(after: press, still: reduceMotion, now: .now)
        pendingOpen = nil
        context.onOpen(moment, .recto)
    }

    /// A contents tap's press (D8): played, never opened, and only by the claim that wins.
    private func claim() {
        guard let request = context.pressRequests.pending, request.momentId == moment.id,
              context.pressRequests.claim(request) else { return }
        press = .next(after: press, still: reduceMotion, now: .now)
    }
}

/// `.accessibilityFocused` only where a focus binding is given.
private struct SlotFocus: ViewModifier {
    let focus: AccessibilityFocusState<UUID?>.Binding?
    let id: UUID

    func body(content: Content) -> some View {
        if let focus {
            content.accessibilityFocused(focus, equals: id)
        } else {
            content
        }
    }
}
