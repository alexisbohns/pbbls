import CoreGraphics
import Testing
@testable import Pebbles

private typealias Img = FilterTestImages

@Suite("PhotoLookModel") @MainActor
struct PhotoLookModelTests {
    private func cropped() throws -> (CGImage, CropGeometry) {
        let crop = PhotoCropModel(source: CropSource(image: Img.ramp(size: 512), captureDate: nil))
        return (try PhotoCropRenderer.render(crop.source.image, geometry: crop.geometry), crop.geometry)
    }

    @Test("prepare renders the preview with the current look and one thumbnail per preset")
    func prepare() async throws {
        let (image, geometry) = try cropped()
        let model = PhotoLookModel()
        model.look.select(.mono)
        await model.prepare(image, for: geometry)
        #expect(model.preparedFor == geometry)
        #expect(model.thumbnails.count == FilterPreset.allCases.count)
        #expect(model.originalThumbnail.map { max($0.width, $0.height) } == PhotoLookModel.thumbnailEdge)
        let preview = try #require(model.preview)
        #expect(try Img.meanChroma(preview) < 0.02)
    }

    @Test("Done with an untouched look uploads the crop itself")
    func identityPassesThrough() async throws {
        let (image, geometry) = try cropped()
        let model = PhotoLookModel()
        await model.prepare(image, for: geometry)
        let out = await model.apply(to: image, sameCrop: true)
        #expect(out === image)
    }

    @Test("Done applies the look at upload size")
    func appliesLook() async throws {
        let (image, geometry) = try cropped()
        let model = PhotoLookModel()
        await model.prepare(image, for: geometry)
        model.look.select(.monoDeep)
        let out = await model.apply(to: image, sameCrop: true)
        #expect(out.width == image.width && out.height == image.height)
        #expect(try Img.meanChroma(out) < 0.02)
    }

    @Test("masks load only once refocus moves, and a picture with no subject says so")
    func masksAreLazy() async throws {
        let (image, geometry) = try cropped()
        let model = PhotoLookModel()
        await model.prepare(image, for: geometry)
        #expect(model.masksState == .notLoaded)
        model.look.adjust.refocus = 0.5
        #expect(model.masksState == .loading)
        await model.waitForMasks()
        #expect(model.masksState == .ready)
        // A ramp has no subject: refocus has nothing to act on, so it goes back to 0.
        #expect(!model.subjectFound)
        #expect(model.look.adjust.refocus == 0)
    }
}
