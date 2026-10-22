import Foundation

extension MomentSummary {
    /// A moment's detail as the Spine reads it (#1146 §Recto): the held card's
    /// recto is the Spine card at the card's width, drawn from this. A
    /// `v_moments_full` soul carries no glyph id; the marks come from the
    /// reference cache by id, as on the Spine.
    init(detail: MomentDetail) {
        let pebble = detail.pebble.map { pebble in
            PebbleSummary(id: pebble.id, intensity: pebble.intensity, positiveness: pebble.positiveness,
                          visibility: pebble.visibility, emotion: pebble.emotion, glyph: pebble.glyph,
                          souls: pebble.souls.map { PathSoul(id: $0.id, name: $0.name, glyphId: nil) })
        }
        self.init(id: detail.id, name: detail.name, happenedAt: detail.happenedAt, createdAt: detail.createdAt,
                  firstSnapPath: detail.snaps.first?.storagePath, pebble: pebble)
    }
}
