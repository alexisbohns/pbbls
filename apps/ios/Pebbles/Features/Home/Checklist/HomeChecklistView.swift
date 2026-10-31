import SwiftUI

/// Home's "Your first five" (spec §1.4, #1382 D12): one box holding the head
/// ("2 of 5"), a stone cut in five that lights a piece per task done, and the
/// five tasks; once all five are ticked, a sixth row claims the blue pebble.
/// Draws nothing when the checklist is done or unavailable.
struct HomeChecklistView: View {
    let checklist: HomeChecklist
    let isClaiming: Bool
    let onTask: (HomeChecklist.Task) -> Void
    let onClaim: () -> Void

    private var locale: Locale { SpineStrings.appLocale }

    /// What VoiceOver reads for the head's figure: "2 of 5 done".
    static func figureLabel(done: Int, locale: Locale) -> String {
        SpineStrings.format("home.firstFive.a11y", locale: locale, done)
    }

    var body: some View {
        if checklist.state == .inProgress || checklist.state == .claimable {
            let title = String(localized: "home.firstFive.title")
            VStack(alignment: .leading, spacing: 0) {
                let of = SpineStrings.string("home.firstFive.of", locale: locale)
                PebblesSectionHead(title, figure: .ratio(done: checklist.doneCount, total: checklist.rows.count, word: of))
                    .accessibilityLabel(Text(verbatim:
                        "\(title), \(Self.figureLabel(done: checklist.doneCount, locale: locale))"))
                stepper
                    .padding(.top, 18)
                    .padding(.bottom, 8)
                ForEach(checklist.rows, id: \.task) { row in
                    ChecklistTaskRow(row: row) { onTask(row.task) }
                }
                if checklist.canClaim { claimRow }
            }
            .pebblesBox()
        }
    }

    /// One thin stone cut in five: the tasks done lit in `primary`, in row order.
    private var stepper: some View {
        let fills = checklist.rows.map { $0.isDone ? Color.pebbles.primary : Color.pebbles.surfaceContainerHighest }
        return SplitStone(count: fills.count, seed: "first-five",
                          options: StoneOptions(corner: 3, lean: 0.9, gutter: 5, fillet: 2),
                          fills: fills, minHeight: 6) { _ in
            Color.clear.frame(height: 6)
        }
        .accessibilityHidden(true)
    }

    private var claimRow: some View {
        Button(action: onClaim) {
            // The blue pebble's mark, in the button's own ink: the capsule is the primary fill.
            HStack(spacing: Spacing.sm) {
                PbblsIconImage(.pebbleRoundLit1, .row, style: AnyShapeStyle(Color.pebbles.onPrimary))
                    .frame(width: 22, height: 22)
                Text("home.firstFive.claim")
                    .spineType(.checklistTitle)
            }
            .padding(.horizontal, Spacing.lg)
        }
        .buttonStyle(PebblesPrimaryButtonStyle(isLoading: isClaiming))
        .disabled(isClaiming)
        .padding(.vertical, Spacing.sm)
    }
}

/// One task: its mark, title and line, and a chevron while it is still
/// to do. A done row is disabled and reads as such through its mark and ink.
private struct ChecklistTaskRow: View {
    let row: HomeChecklist.Row
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: 12) {
                PbblsIconImage(row.isDone ? .checkCircle : .pebbleRound, .row,
                               style: AnyShapeStyle(row.isDone ? Color.pebbles.primary : Color.pebbles.outline))
                    .frame(width: 22, height: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.task.title)
                        .spineType(row.isDone ? .rowTitleDone : .rowTitle)
                        .foregroundStyle(row.isDone ? Color.pebbles.onSurfaceVariant : Color.pebbles.onSurface)
                    Text(row.task.line)
                        .spineType(.rowLine)
                        .foregroundStyle(row.isDone ? Color.pebbles.outline : Color.pebbles.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !row.isDone {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.pebbles.outline)
                        .accessibilityHidden(true)
                }
            }
            .padding(.vertical, 13)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(row.isDone)
        .accessibilityElement(children: .combine)
        .accessibilityValue(row.isDone ? Text("home.firstFive.done") : Text(verbatim: ""))
    }
}
