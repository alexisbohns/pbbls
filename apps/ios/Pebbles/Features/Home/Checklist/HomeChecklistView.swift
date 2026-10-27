import SwiftUI

/// Home's "Your first five" (spec §1.4): a header with five dots and the
/// count, then the five tasks in a hairline card; once all five are ticked, a
/// sixth row claims the blue pebble. Draws nothing when the checklist is done
/// or unavailable.
struct HomeChecklistView: View {
    let checklist: HomeChecklist
    let isClaiming: Bool
    let onTask: (HomeChecklist.Task) -> Void
    let onClaim: () -> Void

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        if checklist.state == .inProgress || checklist.state == .claimable {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                SpineSectionHeader(String(localized: "home.firstFive.title")) {
                    HStack(alignment: .center, spacing: 8) {
                        HStack(spacing: 4) {
                            ForEach(checklist.rows, id: \.task) { row in
                                dot(done: row.isDone)
                            }
                        }
                        .accessibilityHidden(true)
                        Text(verbatim: SpineStrings.format("home.firstFive.count", locale: locale, checklist.doneCount))
                            .spineType(.sectionLabel)
                            .foregroundStyle(Color.pebbles.outline)
                            .lineLimit(1)
                            .fixedSize()
                    }
                }
                VStack(spacing: 0) {
                    ForEach(Array(checklist.rows.enumerated()), id: \.element.task) { index, row in
                        if index > 0 { Divider().overlay(Color.pebbles.rule) }
                        ChecklistTaskRow(row: row) { onTask(row.task) }
                    }
                    if checklist.canClaim {
                        Divider().overlay(Color.pebbles.rule)
                        claimRow
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: Spacing.lg))
                .overlay {
                    RoundedRectangle(cornerRadius: Spacing.lg)
                        .strokeBorder(Color.pebbles.rule, lineWidth: 1)
                }
            }
        }
    }

    @ViewBuilder
    private func dot(done: Bool) -> some View {
        if done {
            Circle().fill(Color.pebbles.onSurface).frame(width: 6, height: 6)
        } else {
            Circle().strokeBorder(Color.pebbles.outline, lineWidth: 1).frame(width: 6, height: 6)
        }
    }

    private var claimRow: some View {
        Button(action: onClaim) {
            // The blue pebble's mark, in the button's own ink: the capsule is the primary fill.
            HStack(spacing: Spacing.sm) {
                Image(systemName: "drop.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(Color.pebbles.onPrimary)
                    .frame(width: 22, height: 22)
                    .accessibilityHidden(true)
                Text("home.firstFive.claim")
                    .pebblesFont(.headline)
            }
            .padding(.horizontal, Spacing.lg)
        }
        .buttonStyle(PebblesPrimaryButtonStyle(isLoading: isClaiming))
        .disabled(isClaiming)
        .padding(Spacing.sm)
        .background(Color.pebbles.surfaceContainer)
    }
}

/// One task: its mark, title and italic line, and a chevron while it is still
/// to do. A done row is disabled and reads as such through its mark and ink.
private struct ChecklistTaskRow: View {
    let row: HomeChecklist.Row
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: Spacing.md) {
                Image(systemName: row.isDone ? "checkmark.circle.fill" : "circle.dashed")
                    .font(.system(size: 20))
                    .foregroundStyle(row.isDone ? Color.pebbles.onSurface : Color.pebbles.outline)
                    .frame(width: 22, height: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.task.title)
                        .pebblesFont(.headline)
                        .foregroundStyle(row.isDone ? Color.pebbles.outline : Color.pebbles.onSurface)
                    Text(row.task.line)
                        .spineType(.summary)
                        .italic()
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !row.isDone {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.pebbles.outline)
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(row.isDone)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(row.isDone ? .isSelected : [])
    }
}
