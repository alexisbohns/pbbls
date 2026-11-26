import SwiftUI

/// The second, far shadow of a stone that is lifting (#1691), drawn live under its image:
/// see `LitStonePainter.paintLiftShadow`. Laid out like `LitStoneImage`, with the room the
/// lifted shadow needs, so it falls past the box without being cut.
struct LitStoneLiftShadow: View {
    let spec: LitStoneSpec
    let lift: Double

    init(spec: LitStoneSpec, lift: Double) {
        self.spec = spec
        self.lift = lift
    }

    var body: some View {
        LitStoneLiveLayer(
            spec: spec,
            margin: { live, box in
                let reach = LitStonePainter.liftShadowMargin(size: live.size, box: box)
                return EdgeInsets(top: ceil(reach.top), leading: ceil(reach.leading),
                                  bottom: ceil(reach.bottom), trailing: ceil(reach.trailing))
            },
            draw: { live, context, box, _ in
                LitStonePainter.paintLiftShadow(live, in: &context, box: box, lift: lift)
            })
    }
}
