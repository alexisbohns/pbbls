import Foundation

/// `get_achievements` → `AchievementsPage` (#1306): pure and one-to-one. It
/// renames, types and clamps (a level to 0…2 and a progress to 0…1, the
/// drawing's bounds) and puts each unknown enum on its fallback (D17): an unknown
/// month state is `quiet`, an unknown tier says nothing, an unknown ring, step
/// or seen card is left out, and an item with an unknown task, stone kind or
/// subject kind is kept with that field nil, so no seat ever shifts. A year
/// without exactly 12 months is dropped (the disc can't be drawn) and a listed
/// month outside 1…12 is left out. It never counts, compares, sorts or picks.
enum AchievementsMapping {
    typealias Wire = AchievementsPayload
    typealias Page = AchievementsPage

    static func page(_ payload: Wire) -> Page {
        Page(since: payload.since,
             daysKept: payload.daysKept,
             explorer: payload.explorer.map(explorer),
             emotions: payload.emotions.map(emotions),
             domains: payload.domains.map(domains),
             pebbles: payload.pebbles.map(ladder),
             glyphsCarved: payload.glyphsCarved.map(ladder),
             glyphSales: payload.glyphSales.map(ladder),
             years: (payload.years ?? []).filter { $0.months.count == 12 }.map(year),
             seen: Dictionary(uniqueKeysWithValues: payload.seen.compactMap { key, date in
                 AchievementCard(rawValue: key).map { ($0, date) }
             }),
             shelf: (payload.shelf ?? []).map {
                 Page.ShelfItem(slug: $0.slug, family: $0.family, threshold: $0.threshold, unlockedOn: $0.unlockedOn)
             },
             shelfCount: payload.shelfCount)
    }

    static func pebble(_ pebble: Wire.Pebble) -> Page.Pebble {
        Page.Pebble(momentId: pebble.momentId, name: pebble.name, happenedAt: pebble.happenedAt,
                    emotionId: pebble.emotionId,
                    valence: Valence.from(positiveness: pebble.positiveness, intensity: pebble.intensity)
                        ?? .neutralMedium)
    }

    static func completion(_ completion: Wire.Completion?, _ stack: Page.CeremonyStack) -> Page.Completion? {
        completion.map {
            Page.Completion(stack: stack, completedAt: $0.completedAt, offeredFrom: $0.offeredFrom,
                            isReady: $0.ready, pulledAt: $0.pulledAt, pebble: $0.pebble.map(pebble))
        }
    }

    static func explorer(_ wire: Wire.Explorer) -> Page.Explorer {
        Page.Explorer(
            firstPebble: wire.firstPebble.map(pebble),
            firstFive: wire.firstFive.map { item in
                Page.FirstFive(task: HomeChecklist.Task.allCases.first { $0.slug == item.slug },
                               done: item.done, on: item.on, subjectName: item.subject?.name,
                               subjectKind: item.subject.flatMap { Page.SubjectKind(rawValue: $0.kind) },
                               subjectId: item.subject?.id, isNew: item.isNew)
            },
            sixFirsts: wire.sixFirsts.map {
                Page.SixFirst(kind: Page.StoneKind(rawValue: $0.kind), done: $0.done, on: $0.on,
                              pebble: $0.pebble.map(pebble), isNew: $0.isNew)
            },
            twentyFive: wire.twentyFive.map {
                Page.TwentyFive(count: $0.count, done: $0.done, on: $0.on, pebble: $0.pebble.map(pebble),
                                isNew: $0.isNew)
            },
            ringsDone: Set(wire.ringsDone.compactMap(Page.Ring.init(rawValue:))),
            completion: completion(wire.completion, .sixFirsts))
    }

    static func emotions(_ wire: Wire.Emotions) -> Page.Emotions {
        Page.Emotions(
            count: wire.count, of: wire.of,
            seats: wire.seats.map {
                Page.Seat(seat: $0.seat, emotionId: $0.emotionId, family: $0.family, met: $0.met, on: $0.on,
                          pebble: $0.pebble.map(pebble), isNew: $0.isNew)
            },
            families: wire.families.map { Page.EmotionFamily(family: $0.family, met: $0.met, notYet: $0.notYet) },
            since: wire.since,
            completion: completion(wire.completion, .emotions))
    }

    static func domains(_ wire: Wire.Domains) -> Page.Domains {
        Page.Domains(
            count: wire.count, of: wire.of,
            coverage: Page.Coverage(rawValue: wire.coverage) ?? .unknown,
            entered: wire.entered.map {
                Page.DomainEntry(domainId: $0.domainId, on: $0.on, pebble: $0.pebble.map(pebble), isNew: $0.isNew)
            },
            notYet: wire.notYet,
            completion: completion(wire.completion, .domains))
    }

    static func ladder(_ wire: Wire.Ladder) -> Page.Ladder {
        Page.Ladder(
            count: wire.count,
            levels: wire.levels.map {
                Page.Level(threshold: $0.threshold, on: $0.on, pebble: $0.pebble.map(pebble), isNew: $0.isNew)
            },
            next: wire.next.map { Page.Next(threshold: $0.threshold, left: $0.left, progress: unit($0.progress)) },
            ladder: wire.ladder,
            thenEvery: wire.thenEvery,
            since: wire.since)
    }

    static func month(_ wire: Wire.Month) -> Page.Month {
        Page.Month(month: wire.month, state: Page.MonthState(rawValue: wire.state) ?? .quiet, family: wire.family,
                   momentDays: wire.momentDays, pebbleDays: wire.pebbleDays, fullWeeks: wire.fullWeeks,
                   weeksFilled: wire.weeksFilled, level: min(max(wire.level, 0), 2), progress: unit(wire.progress),
                   done: Page.MonthDone(moments: wire.done.moments, pebbles: wire.done.pebbles, weeks: wire.done.weeks))
    }

    static func year(_ wire: Wire.Year) -> Page.Year {
        Page.Year(
            year: wire.year, isCurrent: wire.current, daysTarget: wire.daysTarget, months: wire.months.map(month),
            listed: wire.listed.filter { (1...12).contains($0) },
            nextStep: wire.nextStep.flatMap { step in
                Page.Needs(rawValue: step.needs).map {
                    Page.NextStep(month: step.month, needs: $0, daysLeft: step.daysLeft, reachable: step.reachable)
                }
            })
    }

    /// Clamped to [0, 1]: a fraction the drawing can place.
    private static func unit(_ value: Double) -> Double {
        min(max(value, 0), 1)
    }
}

extension AchievementsPayload {
    /// The page the views draw.
    var model: AchievementsPage { AchievementsMapping.page(self) }
}
