import SwiftUI

/// One checklist task, explained (spec §1.4): its picture, two sentences and
/// the one way to do it. The call to action dismisses, then hands over to
/// Home, which opens the flow once the sheet is gone.
struct ChecklistTaskSheet: View {
    let task: HomeChecklist.Task
    let onGo: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ChecklistTaskContent(task: task) {
                dismiss()
                onGo()
            }
            .navigationBarTitleDisplayMode(.inline)
            .navigationTitle(Text(task.title))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
        .pebblesScreen()
    }
}

/// The sheet's content without its environment, so a reference can pin it.
struct ChecklistTaskContent: View {
    let task: HomeChecklist.Task
    let onGo: () -> Void

    var body: some View {
        VStack(spacing: Spacing.lg) {
            ChecklistIllustration(task: task)
            Text(task.body)
                .spineType(.summary)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, Spacing.lg)
            Button(action: onGo) { Text(task.cta) }
                .buttonStyle(PebblesPrimaryButtonStyle())
                .padding(.horizontal, Spacing.lg)
        }
        .padding(.top, Spacing.md)
        .frame(maxHeight: .infinity, alignment: .top)
    }
}
