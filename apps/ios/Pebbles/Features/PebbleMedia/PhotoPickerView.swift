import PhotosUI
import SwiftUI
import os

/// Presents the system photo picker (single image), and returns `(Data, UTI)`
/// on selection. The caller wires this to its sheet presentation state.
struct PhotoPickerView: UIViewControllerRepresentable {

    /// Called on the main actor with the selected payload, or `nil` if the
    /// user cancels or selection fails.
    let onPicked: @MainActor (PickedPhoto?) -> Void

    struct PickedPhoto {
        let data: Data
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

    @MainActor
    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        private let onPicked: @MainActor (PickedPhoto?) -> Void
        private let logger = Logger(subsystem: "app.pbbls.ios", category: "photo-picker")

        init(onPicked: @escaping @MainActor (PickedPhoto?) -> Void) {
            self.onPicked = onPicked
        }

        nonisolated func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            Task { @MainActor in
                picker.dismiss(animated: true)

                guard let result = results.first else {
                    self.onPicked(nil)
                    return
                }
                let provider = result.itemProvider
                let candidate = provider.registeredTypeIdentifiers
                    .first(where: ImageFormatValidator.isSupported)

                guard let uti = candidate else {
                    self.logger.warning("no supported UTI in picker result; identifiers: \(provider.registeredTypeIdentifiers, privacy: .public)")
                    self.onPicked(nil)
                    return
                }

                provider.loadDataRepresentation(forTypeIdentifier: uti) { [weak self] data, error in
                    Task { @MainActor in
                        guard let self else { return }
                        guard let data, error == nil else {
                            self.logger.error("picker load failed: \(error?.localizedDescription ?? "no data", privacy: .private)")
                            self.onPicked(nil)
                            return
                        }
                        self.onPicked(PickedPhoto(data: data, uti: uti))
                    }
                }
            }
        }
    }
}
