import Foundation

/// The calendar the Spine's views pass to its logic (D10): ISO weeks, in the
/// device's zone. The logic never reads `.current` itself; the views do, here,
/// once. The B2 references pin the zone with `TEST_RUNNER_TZ=Europe/Paris`.
enum SpineCalendar {
    static var current: Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = .current
        return calendar
    }
}
