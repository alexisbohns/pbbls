import SwiftUI

/// Step 0 — the picture the flow starts from (D2).
///
/// Does **not** auto-advance on pick (D3): the upload runs in the background
/// and its state belongs on screen while the user is still looking at the
/// photo. Picking swaps `Skip` for `Done` and waits.
struct RecordPhotoStep: View {
    let snap: FormSnap?
    let onPick: () -> Void
    let onRetry: () -> Void
    let onRemove: () -> Void

    var body: some View {
        VStack(spacing: Spacing.lg) {
            switch snap {
            case .none:
                addTile
            case .pending(let attached):
                picked(thumb: attached.localThumb, state: attached.state)
            case .existing:
                // Only reachable when resuming a draft that already carries a
                // snap; the bytes live in Storage, so the step just confirms
                // one is attached rather than re-rendering it.
                attachedWithoutThumb
            }
        }
    }

    private var addTile: some View {
        Button(action: onPick) {
            VStack(spacing: Spacing.sm) {
                Image(systemName: "photo.badge.plus")
                    .font(.system(size: 40))
                    .foregroundStyle(Color.system.secondary)
                Text("Add a photo")
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.system.secondary)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 280)
            .background(
                RoundedRectangle(cornerRadius: Spacing.xxl, style: .continuous)
                    .strokeBorder(Color.system.muted, style: StrokeStyle(lineWidth: 2, dash: [10, 10]))
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Add a photo")
    }

    @ViewBuilder
    private func picked(thumb: Data, state: AttachedSnap.UploadState) -> some View {
        VStack(spacing: Spacing.md) {
            if let image = UIImage(data: thumb) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(maxWidth: .infinity)
                    .frame(height: 280)
                    .clipShape(RoundedRectangle(cornerRadius: Spacing.xxl, style: .continuous))
                    .accessibilityHidden(true)
            }

            switch state {
            case .uploading:
                HStack(spacing: Spacing.sm) {
                    ProgressView()
                    Text("Uploading…")
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                }
            case .uploaded:
                Button("Choose another", action: onPick)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.secondary)
            case .failed:
                VStack(spacing: Spacing.sm) {
                    Text("That photo didn't upload.")
                        .pebblesFont(.subhead)
                        .foregroundStyle(.red)
                    HStack(spacing: Spacing.lg) {
                        Button("Retry", action: onRetry)
                        Button("Remove", role: .destructive, action: onRemove)
                    }
                    .pebblesFont(.subhead)
                }
            }
        }
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
        .frame(maxWidth: .infinity)
        .frame(height: 280)
    }
}
