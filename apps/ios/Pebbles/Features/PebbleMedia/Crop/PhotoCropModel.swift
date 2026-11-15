import CoreGraphics
import Observation

enum CropTool: CaseIterable, Identifiable {
    case ratio, rotate
    var id: Self { self }
}

/// The crop editor's state. Gestures arrive as whole-gesture totals
/// (translation, magnification) applied to the geometry the gesture started
/// from, so pan and pinch together never fight over one value.
@Observable @MainActor
final class PhotoCropModel {
    let source: CropSource
    private(set) var geometry: CropGeometry
    var tool: CropTool = .ratio
    /// True while a finger is on the canvas: the thirds grid shows.
    private(set) var isInteracting = false
    /// True while the ruler is dragged: the finer grid shows.
    var isRotating = false

    @ObservationIgnored private var gestureStart: CropGeometry?
    private let initial: CropGeometry

    init(source: CropSource) {
        self.source = source
        let start = CropGeometry.initial(imageSize: CGSize(width: source.image.width, height: source.image.height))
        self.initial = start
        self.geometry = start
    }

    var hasChanges: Bool { geometry != initial }

    func select(_ ratio: CropRatio) { geometry = geometry.withRatio(ratio) }
    func setAngle(_ degrees: Double) { geometry = geometry.withAngle(degrees) }
    func turnQuarter() { geometry = geometry.turnedQuarter() }
    func reset() { geometry = initial }

    /// Accessibility zoom: one step of 1.25×, clamped.
    func zoom(by factor: CGFloat) { geometry = geometry.zoomed(by: factor).normalized() }

    func gestureChanged(translation: CGSize, magnification: CGFloat, windowHeight: CGFloat) {
        let start = gestureStart ?? geometry
        gestureStart = start
        isInteracting = true
        let units = CGSize(width: translation.width / windowHeight, height: translation.height / windowHeight)
        geometry = start.zoomed(by: magnification).panned(by: units).rubberBanded()
    }

    func gestureEnded() {
        gestureStart = nil
        isInteracting = false
        geometry = geometry.normalized()
    }

    func render() async throws -> CGImage {
        let image = source.image, g = geometry
        return try await Task.detached(priority: .userInitiated) {
            try PhotoCropRenderer.render(image, geometry: g)
        }.value
    }
}
