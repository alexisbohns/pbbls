import PhotosUI
import SwiftUI
import os

/// Presents the system photo picker (single image), and hands the chosen
/// `NSItemProvider` + selected UTI back to the caller. The caller is
/// responsible for the asynchronous data load — keeping that out of the
/// delegate lets the SwiftUI sheet binding dismiss the picker the instant the
/// user taps; otherwise the picker stays visible until bytes finish loading.
///
/// Crucially: we do NOT call `picker.dismiss(animated:)` from the delegate.
/// When PHPicker is presented via `.sheet` in SwiftUI, calling `dismiss` on
/// the picker cascades up the presentation chain and pops the parent sheet
/// too. The SwiftUI binding (`isPhotoPickerPresented = false` in the parent's
/// `onPicked` callback) is the right way to dismiss.
struct PhotoPickerView: UIViewControllerRepresentable {

    /// Called on the main actor with the selected payload, or `nil` if the
    /// user cancels or no supported representation is available.
    let onPicked: @MainActor (PickedItem?) -> Void

    /// Lightweight handle to the picked asset. Bytes are not loaded yet —
    /// the caller drives `loadDataRepresentation` asynchronously after the
    /// picker has dismissed.
    struct PickedItem {
        let itemProvider: NSItemProvider
        /// First UTI from the provider's `registeredTypeIdentifiers` that
        /// passes `ImageFormatValidator.isSupported`.
        let uti: String
    }

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration(photoLibrary: .shared())
        config.filter = .images
        config.selectionLimit = 1
        config.preferredAssetRepresentationMode = .current

        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onPicked: onPicked)
    }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        private let onPicked: @MainActor (PickedItem?) -> Void
        private let logger = Logger(subsystem: "app.pbbls.ios", category: "photo-picker")

        init(onPicked: @escaping @MainActor (PickedItem?) -> Void) {
            self.onPicked = onPicked
        }

        nonisolated func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            // Capture before hopping actors — `results` can't outlive the call site.
            let picked: PickedItem?
            if let result = results.first,
               let uti = result.itemProvider.registeredTypeIdentifiers
                                  .first(where: { ImageFormatValidator.isSupported($0) }) {
                picked = PickedItem(itemProvider: result.itemProvider, uti: uti)
            } else {
                if let identifiers = results.first?.itemProvider.registeredTypeIdentifiers {
                    logger.warning("no supported UTI in picker result; identifiers: \(identifiers, privacy: .public)")
                }
                picked = nil
            }

            Task { @MainActor in
                self.onPicked(picked)
            }
        }
    }
}
