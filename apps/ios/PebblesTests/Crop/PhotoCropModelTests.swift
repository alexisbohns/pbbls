import CoreGraphics
import Testing
@testable import Pebbles

@Suite("PhotoCropModel") @MainActor
struct PhotoCropModelTests {
    private func model(width: Int = 1536, height: Int = 2048) throws -> PhotoCropModel {
        PhotoCropModel(source: CropSource(image: try ImagePipelineCroppedTests.solid(width: width, height: height),
                                          captureDate: nil))
    }

    @Test("opens unchanged on the closest format and the Ratio tool")
    func opens() throws {
        let m = try model()
        #expect(m.geometry.ratio == .threeFour)
        #expect(m.tool == .ratio)
        #expect(!m.hasChanges)
    }

    @Test("picking a format, turning, straightening all count as changes; reset undoes them")
    func changesAndReset() throws {
        let m = try model()
        m.select(.square)
        #expect(m.hasChanges)
        m.reset()
        #expect(!m.hasChanges)
        m.turnQuarter()
        #expect(m.geometry.ratio == .fourThree)
        m.setAngle(12)
        #expect(m.geometry.angle == 12)
        m.reset()
        #expect(m.geometry == CropGeometry.initial(imageSize: m.geometry.imageSize))
    }

    @Test("a drag past the edge rubber-bands, and settles inside on release")
    func dragSettles() throws {
        let m = try model(width: 2048, height: 1152)
        m.gestureChanged(translation: CGSize(width: 5000, height: 0), magnification: 1, windowHeight: 400)
        let during = m.geometry
        m.gestureEnded()
        #expect(m.geometry == m.geometry.normalized())
        #expect(during != m.geometry)
        #expect(!m.isInteracting)
    }

    @Test("a pinch below covering springs back to covering")
    func pinchClamps() throws {
        let m = try model()
        m.gestureChanged(translation: .zero, magnification: 0.3, windowHeight: 400)
        m.gestureEnded()
        #expect(abs(m.geometry.scale - m.geometry.minScale) < 1e-12)
    }

    @Test("render gives the window's shape")
    func renders() async throws {
        let m = try model()
        m.select(.fourFive)
        let image = try await m.render()
        #expect(abs(CGFloat(image.width) / CGFloat(image.height) - 0.8) < 0.01)
    }
}
