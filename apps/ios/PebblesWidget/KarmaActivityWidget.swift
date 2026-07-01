import ActivityKit
import SwiftUI
import WidgetKit

/// Live Activity for the "+N karma" flash. On Dynamic Island hardware the
/// system momentarily expands on request/update (the Opal grow), then settles
/// to the compact `+N`; the controller ends it after ~2.5s. The Lock Screen
/// view is kept minimal because we end immediately and it is rarely seen.
struct KarmaActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: KarmaActivityAttributes.self) { context in
            HStack(spacing: 8) {
                Image(systemName: "sparkle").foregroundStyle(Color("AccentPrimary"))
                Text("+\(context.state.amount) karma").font(.headline)
                Spacer()
            }
            .padding()
            .activityBackgroundTint(Color.black.opacity(0.2))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "sparkle").foregroundStyle(Color("AccentPrimary"))
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("+\(context.state.amount)").font(.title3.bold())
                }
                DynamicIslandExpandedRegion(.center) {
                    Text("karma").font(.caption).foregroundStyle(.secondary)
                }
            } compactLeading: {
                Image(systemName: "sparkle").foregroundStyle(Color("AccentPrimary"))
            } compactTrailing: {
                Text("+\(context.state.amount)").font(.caption.bold())
            } minimal: {
                Image(systemName: "sparkle").foregroundStyle(Color("AccentPrimary"))
            }
        }
    }
}
