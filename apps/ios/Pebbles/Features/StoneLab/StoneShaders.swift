#if DEBUG
import SwiftUI

/// Builds the two lab shaders so the views never spell the argument order out.
/// The order here is the `.metal` signature; `StoneMaterial.uniforms` supplies
/// the material tail in the same order (pinned by `StoneMaterialTests`).
enum StoneShaders {

    /// The material over the body. `unit` is points per stone (viewBox) unit.
    static func stone(
        material: StoneMaterial,
        tones: StoneTones,
        palette: StoneLabPalette,
        light: StoneLight,
        unit: CGFloat,
        seed: Int
    ) -> Shader {
        var arguments: [Shader.Argument] = [
            .float(unit),
            .float(Double(seed)),
            .float3(Double(light.vector.x), Double(light.vector.y), Double(light.vector.z)),
            rgb(tones.rgb(tones.body, in: palette)),
            rgb(tones.rgb(tones.ink, in: palette)),
            rgb(tones.rgb(tones.lipLight, in: palette)),
            rgb(tones.rgb(tones.lipShadow, in: palette)),
        ]
        arguments += material.uniforms.map { .float(Double($0)) }
        return Shader(function: ShaderFunction(library: .default, name: "stone"), arguments: arguments)
    }

    /// The groove over the flat ink. `offset` is toward the light, in points.
    static func carve(
        material: StoneMaterial,
        tones: StoneTones,
        palette: StoneLabPalette,
        light: StoneLight
    ) -> Shader {
        let offset = light.lipOffset(width: material.lipWidth)
        return Shader(function: ShaderFunction(library: .default, name: "carve"), arguments: [
            .float2(offset),
            rgb(tones.rgb(tones.ink, in: palette)),
            rgb(tones.rgb(tones.lipLight, in: palette)),
            rgb(tones.rgb(tones.lipShadow, in: palette)),
            .float(material.lipOpacity),
        ])
    }

    private static func rgb(_ v: SIMD3<Float>) -> Shader.Argument {
        .float3(Double(v.x), Double(v.y), Double(v.z))
    }
}
#endif
