import Testing
import Foundation
import CoreGraphics
import simd
@testable import Pebbles

@Suite("StoneMaterial · StoneTones · StoneLight")
struct StoneMaterialTests {

    @Test("each polarity starts on its own material kind")
    func startingKinds() {
        #expect(StoneMaterial.starting(for: .lowlight).kind == .lava)
        #expect(StoneMaterial.starting(for: .neutral).kind == .river)
        #expect(StoneMaterial.starting(for: .highlight).kind == .gem)
    }

    @Test("uniforms are emitted in the declared order")
    func uniformOrder() {
        var material = StoneMaterial.starting(for: .neutral)
        material.scale = 1; material.relief = 2; material.contrast = 3; material.sheen = 4
        material.facetDensity = 5; material.glitter = 6; material.pits = 7; material.banding = 8
        material.rimWidth = 9; material.rimStrength = 10
        #expect(material.uniforms == [1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    }

    @Test("starting tones follow the spec table")
    func startingTones() {
        #expect(StoneTones.starting(for: .lowlight).body.slot == .dark)
        #expect(StoneTones.starting(for: .neutral).body.slot == .shaded)
        #expect(StoneTones.starting(for: .highlight).body.slot == .primary)
        #expect(StoneTones.starting(for: .highlight).ink.slot == .secondary)
        #expect(StoneTones.starting(for: .lowlight).lipShadow.slot == .shaded)
        #expect(StoneTones.starting(for: .neutral).lipShadow.opacity < 1)
    }

    @Test("the rest light comes from the top-left at 55°")
    func restLight() {
        let rest = StoneLight.rest
        #expect(abs(rest.direction.dx + 0.7071) < 0.001)
        #expect(abs(rest.direction.dy + 0.7071) < 0.001)
        #expect(rest.elevationDegrees == 55)
        let v = rest.vector
        #expect(abs(v.z - sin(55 * .pi / 180)) < 0.001)
        #expect(abs(simd_length(v) - 1) < 0.001)
    }

    @Test("a light built from a finger offset is a unit direction")
    func fromOffset() {
        let light = StoneLight(pointingTo: CGVector(dx: 30, dy: -40), elevationDegrees: 40)
        #expect(abs(light.direction.dx - 0.6) < 0.001)
        #expect(abs(light.direction.dy + 0.8) < 0.001)
        #expect(StoneLight(pointingTo: .zero, elevationDegrees: 40).direction == StoneLight.rest.direction)
    }
}
