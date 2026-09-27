import Foundation

/// Options that belong to the stone as a whole rather than to one material:
/// what the carving is scaled to, whether the engine outline is drawn, the
/// contact shadow and the carved line along the silhouette's edge.
/// `standard` is the maintainer's pick from the stone lab (2026-09-27).
struct StoneLook: Equatable, Codable {
    /// Draw the engine's outline as a carving. Off means the stone's own edge
    /// is the only edge, carried by the rim and the edge line.
    var showOutline: Bool = false
    /// Multiplier on the engine's carving inset (1 is the engine's 12%).
    var carvingScale: Double = 1.22
    /// Contact shadow under the stone, 0..1; 0 draws none.
    var shadowStrength: Double = 0.8
    /// A carved line following the silhouette's own edge, in points; 0 is off.
    var edgeWidth: Double = 1.88
    /// How far inside the edge that line sits, as a fraction of the stone.
    var edgeInset: Double = 0.03

    static let standard = StoneLook()

    /// The feed's rendering: no shadow, no edge line, no outline.
    static let flat = StoneLook(showOutline: false, carvingScale: 1.22, shadowStrength: 0, edgeWidth: 0, edgeInset: 0.03)
}
