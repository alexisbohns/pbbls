import SwiftUI

/// One row of a `PebblesMenu`.
struct PebblesMenuItem: Identifiable {
    let id = UUID()
    let title: LocalizedStringResource
    let systemImage: String
    let isDestructive: Bool
    let action: () -> Void

    init(
        title: LocalizedStringResource,
        systemImage: String,
        isDestructive: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.isDestructive = isDestructive
        self.action = action
    }
}

/// The menu every orange Delete lives in (D9, decision log 2026-10-21, #921).
/// iOS draws `Menu` and `.contextMenu` items through UIMenu, which ignored a
/// SwiftUI tint on the title in the A2 trial, so the error role needs a
/// surface of its own. This is a popover kept as a popover on iPhone. That
/// gives outside-tap dismissal and the system's focus return to the trigger.
/// The list adds the VoiceOver modal trait, moves focus to its first item and
/// dismisses on the escape gesture. No call site rolls its own menu.
struct PebblesMenu<Trigger: View>: View {
    let items: [PebblesMenuItem]
    @ViewBuilder let trigger: () -> Trigger

    @State private var isPresented = false

    var body: some View {
        Button { isPresented = true } label: { trigger() }
            .popover(isPresented: $isPresented) {
                PebblesMenuList(items: items) { isPresented = false }
            }
    }
}

extension View {
    /// `.contextMenu` for rows whose actions include an orange Delete. A long
    /// press opens the same list, and VoiceOver gets the items as actions.
    func pebblesContextMenu(_ items: [PebblesMenuItem]) -> some View {
        modifier(PebblesContextMenu(items: items))
    }
}

private struct PebblesContextMenu: ViewModifier {
    let items: [PebblesMenuItem]
    @State private var isPresented = false

    func body(content: Content) -> some View {
        content
            // High priority: a simultaneous long press also fired the row Button's
            // tap, and `.onLongPressGesture` lost to the Button entirely. A short
            // tap fails this gesture and still reaches the Button.
            .highPriorityGesture(LongPressGesture(minimumDuration: 0.5).onEnded { _ in isPresented = true })
            .accessibilityActions {
                ForEach(items) { item in
                    Button(action: item.action) { Text(item.title) }
                }
            }
            .popover(isPresented: $isPresented) {
                PebblesMenuList(items: items) { isPresented = false }
            }
    }
}

private struct PebblesMenuList: View {
    let items: [PebblesMenuItem]
    let dismiss: () -> Void

    @AccessibilityFocusState private var focusedItem: UUID?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(items) { item in
                Button {
                    dismiss()
                    item.action()
                } label: {
                    Label { Text(item.title) } icon: { Image(systemName: item.systemImage) }
                        .labelStyle(.titleAndIcon)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, Spacing.md)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(item.isDestructive ? Color.pebbles.error : Color.pebbles.onSurface)
                .accessibilityFocused($focusedItem, equals: item.id)
                if item.id != items.last?.id {
                    Rectangle().fill(Color.pebbles.rule).frame(height: 1)
                }
            }
        }
        .frame(minWidth: 220)
        .background(Color.pebbles.surfaceContainer)
        .presentationCompactAdaptation(.popover)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, dismiss)
        .onAppear { focusedItem = items.first?.id }
    }
}
