import SwiftUI
import UIKit

/// The system camera for Home's camera moment. The shutter time is read in the
/// delegate callback, the instant the photo comes back (spec D12).
struct CameraPicker: UIViewControllerRepresentable {
    let onCapture: (UIImage, Date) -> Void
    let onCancel: () -> Void

    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraCaptureMode = .photo
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Delegate { Delegate(onCapture: onCapture, onCancel: onCancel) }

    final class Delegate: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onCapture: (UIImage, Date) -> Void
        let onCancel: () -> Void

        init(onCapture: @escaping (UIImage, Date) -> Void, onCancel: @escaping () -> Void) {
            self.onCapture = onCapture
            self.onCancel = onCancel
        }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            let shutter = Date()
            guard let image = info[.originalImage] as? UIImage else {
                onCancel()
                return
            }
            onCapture(image, shutter)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { onCancel() }
    }
}
