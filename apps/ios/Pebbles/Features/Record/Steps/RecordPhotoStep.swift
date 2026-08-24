import PhotosUI
import SwiftUI

/// Step 0 — the picture the flow starts from (D2).
///
/// The library *is* the step: an inline `PhotosPicker` (iOS 17) renders the
/// system picker in place, so the user's own photos are the first thing on
/// screen instead of a tile standing in for them. It still runs out of
/// process — the app asks for no photo-library authorization and sees nothing
/// but the one image that gets tapped.
///
/// Does **not** auto-advance on pick (D3): the upload runs in the background
/// and its state belongs on screen while the user is still looking at the
/// grid. Picking swaps `Skip` for `Done` and waits. Changing your mind is a
/// second tap in the grid — the coordinator swaps the snap and takes the
/// replaced one's bytes with it.
struct RecordPhotoStep: View {
    let snap: FormSnap?
    /// The picker's selection. Owned by `RecordFlowView` so it survives
    /// stepping away and back, and so loading the bytes stays with the
    /// orchestration rather than the view.
    @Binding var selection: PhotosPickerItem?
    let onRetry: () -> Void
    let onRemove: () -> Void

    var body: some View {
        VStack(spacing: Spacing.sm) {
            if case .pending(let attached) = snap {
                statusCard(thumb: attached.localThumb, state: attached.state)
            }

            if case .existing = snap {
                // Only reachable when resuming a draft that already carries a
                // snap; the bytes live in Storage, so the step just confirms
                // one is attached rather than re-rendering it.
                attachedWithoutThumb
            } else {
                picker
            }
        }
    }

    // MARK: - Picker

    private var picker: some View {
        PhotosPicker(
            selection: $selection,
            matching: .images,
            preferredItemEncoding: .current,
            label: { Text("Add a photo") }
        )
        .photosPickerStyle(.inline)
        // Everything the picker wraps around the grid belongs to the modal it
        // is no longer: no search field, no album switcher, no selection bar.
        .photosPickerDisabledCapabilities([.search, .collectionNavigation, .selectionActions, .stagingArea])
        .photosPickerAccessoryVisibility(.hidden, edges: .all)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.xxl, style: .continuous))
    }

    // MARK: - Attached snap

    private func statusCard(thumb: Data, state: AttachedSnap.UploadState) -> some View {
        HStack(spacing: Spacing.sm) {
            thumbnail(thumb)
            stateLabel(state)
            Spacer(minLength: Spacing.sm)
            trailingControl(state)
        }
        .padding(Spacing.sm)
        .background(
            RoundedRectangle(cornerRadius: Spacing.xl, style: .continuous)
                .fill(Color.accent.surface)
        )
    }

    @ViewBuilder
    private func thumbnail(_ thumb: Data) -> some View {
        if let image = UIImage(data: thumb) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: 52, height: 52)
                .clipShape(RoundedRectangle(cornerRadius: Spacing.md, style: .continuous))
                .accessibilityHidden(true)
        }
    }

    @ViewBuilder
    private func stateLabel(_ state: AttachedSnap.UploadState) -> some View {
        switch state {
        case .uploading:
            Text("Uploading…")
                .pebblesFont(.subhead)
                .foregroundStyle(Color.system.secondary)
        case .uploaded:
            Label("Ready", systemImage: "checkmark.circle.fill")
                .pebblesFont(.subhead)
                .foregroundStyle(Color.system.secondary)
        case .failed:
            Text("Upload failed")
                .pebblesFont(.subhead)
                .foregroundStyle(.red)
        }
    }

    @ViewBuilder
    private func trailingControl(_ state: AttachedSnap.UploadState) -> some View {
        switch state {
        case .uploading:
            ProgressView()
        case .uploaded:
            removeButton
        case .failed:
            HStack(spacing: Spacing.md) {
                Button("Retry", action: onRetry)
                removeButton
            }
            .pebblesFont(.subhead)
        }
    }

    private var removeButton: some View {
        Button(role: .destructive, action: onRemove) {
            Image(systemName: "xmark.circle.fill")
                .foregroundStyle(Color.system.muted)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Remove photo")
    }

    private var attachedWithoutThumb: some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: "photo")
                .font(.system(size: 40))
                .foregroundStyle(Color.system.secondary)
            Text("A photo is already attached.")
                .pebblesFont(.subhead)
                .foregroundStyle(Color.system.secondary)
            Button("Remove", role: .destructive, action: onRemove)
                .pebblesFont(.subhead)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
