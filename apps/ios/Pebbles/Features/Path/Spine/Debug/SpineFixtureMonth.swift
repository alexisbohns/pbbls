#if DEBUG
import Foundation

/// The lab's and the references' month (#1226): the harness's verbatim October
/// (`month-full.json`, embedded here; a test keeps it equal to the harness's
/// file) with lookups that give its ids faces: its souls become the fixture
/// week's souls in rank order, its fine emotions the fixture's emotions of the
/// same family (with their palettes), its domains named ones, and its pebbles
/// named moments. The numbers, orders and allocations stay exactly the server's.
@MainActor
enum SpineFixtureMonth {
    // swiftlint:disable:next type_name
    typealias F = SpineFixtureWeek

    /// October 2026 as `path_month_wrap` returned it to the harness (minified).
    static var october: MonthWrap? {
        try? WrapDecoding.decode(MonthWrapPayload.self, from: Data(json.utf8)).model
    }

    static func lookups(_ wrap: MonthWrap) -> WrapLookups {
        // Souls: in order of appearance, then the persons', mapped onto the fixture's ranked souls.
        var soulIds: [String] = []
        for id in (wrap.cover?.soulsInOrder ?? []) + (wrap.persons ?? []).map(\.soulId) where !soulIds.contains(id) {
            soulIds.append(id)
        }
        let faces = Dictionary(uniqueKeysWithValues: zip(soulIds, F.personsOrder))
        // Emotions: each fine emotion takes its family's fixture palette.
        var emotions: [String: EmotionWithPalette] = [:]
        for fine in wrap.fineEmotions ?? [] {
            if let row = emotion(fine.emotionId, family: fine.family) { emotions[fine.emotionId] = row }
        }
        let domains = Dictionary(uniqueKeysWithValues: (wrap.domains ?? []).enumerated().map { index, stat in
            let name = domainNames[index % domainNames.count]
            return (stat.domainId, Domain(id: UUID(uuidString: stat.domainId) ?? UUID(), slug: name.lowercased(),
                                          name: name, label: name))
        })
        let moments = Self.moments(wrap, emotions: emotions)
        let families = Dictionary(uniqueKeysWithValues: F.families.map { ($0.slug, $0) })
        return WrapLookups(
            moment: { moments[$0] },
            family: { families[$0] },
            domain: { domains[$0] },
            emotion: { emotions[$0] },
            palette: { moment in moment.pebble?.emotion.flatMap { emotions[$0.id.uuidString.lowercased()]?.palette } },
            soulName: { faces[$0].flatMap { F.soulNames[$0] } },
            soulGlyph: { faces[$0].flatMap { F.soulGlyphs[$0] } }
        )
    }

    private static let emotionNames = ["joy": "Joyful", "peace": "Peaceful", "pride": "Proud", "anger": "Angry",
                                       "sadness": "Sad", "fear": "Scared", "shame": "Ashamed"]

    private static let domainNames = ["Family", "Friends", "Identity", "Travel", "Hobbies", "Partner", "Work",
                                      "Health", "Home", "Learning", "Nature"]

    private static let momentNames = ["Flag march with Nadia", "Forest walk, all of us", "Rooftop birthday",
                                      "Picnic on the square", "Grandma's call went quiet", "Coffee by the fountain",
                                      "Mara on the seawall", "Rent went up again", "Couldn't sleep"]

    /// Every pebble on the cover as a named moment of October, at ten in the morning.
    private static func moments(_ wrap: MonthWrap, emotions: [String: EmotionWithPalette]) -> [String: MomentSummary] {
        var moments: [String: MomentSummary] = [:]
        var index = 0
        for day in wrap.cover?.days ?? [] {
            for pebble in day.pebbles {
                let happened = CalendarDay(year: wrap.month.year, month: wrap.month.month, day: day.day + 1)
                    .date(in: F.calendar.timeZone)?.addingTimeInterval(10 * 3600) ?? .distantPast
                let id = UUID(uuidString: pebble.momentId) ?? UUID()
                let emotion = pebble.emotionId.flatMap { emotions[$0] }.map {
                    EmotionRef(id: $0.id, slug: $0.slug, name: $0.name)
                }
                let positiveness = pebble.polarity == .highlight ? 1 : pebble.polarity == .lowlight ? -1 : 0
                moments[pebble.momentId] = MomentSummary(
                    id: id, name: momentNames[index % momentNames.count], happenedAt: happened, createdAt: happened,
                    firstSnapPath: nil,
                    pebble: MomentSummary.PebbleSummary(id: id, intensity: pebble.intensity, positiveness: positiveness,
                                                        visibility: .private, emotion: emotion, glyph: nil, souls: [])
                )
                index += 1
            }
        }
        return moments
    }

    /// A `v_emotions_with_palette` row for an emotion of `family`, in the fixture's palette.
    private static func emotion(_ id: String, family: String) -> EmotionWithPalette? {
        guard let category = F.families.first(where: { $0.slug == family }) else { return nil }
        let palette = category.palette
        let json = """
        {"id":"\(id)","slug":"\(family)-fine","name":"\(emotionNames[family] ?? family)","emoji":"🙂",
         "category_id":"\(category.id.uuidString)","category_slug":"\(family)","category_name":"\(category.name)",
         "primary_color":"\(palette.primaryHex)","secondary_color":"\(palette.secondaryHex)",
         "light_color":"\(palette.lightHex)","surface_color":"\(palette.surfaceHex)",
         "dark_color":"\(palette.darkHex)","shaded_color":"\(palette.shadedHex)"}
        """
        return try? JSONDecoder().decode(EmotionWithPalette.self, from: Data(json.utf8))
    }

    // swiftlint:disable line_length
    static let json = ##"""
{"gem":{"lead":{"zero":["shame"],"order":["joy","peace","pride","anger","sadness","fear"]},"main":"joy","facets":{"kite_0":"joy","kite_1":"joy","kite_2":"pride","kite_3":"anger","kite_4":"sadness","kite_5":"peace","kite_6":"joy","star_0":"joy","star_1":"pride","star_2":"anger","star_3":"fear","star_4":"peace","star_5":"peace","star_6":"joy","girdle_0":"joy","girdle_1":"joy","girdle_2":"joy","girdle_3":"pride","girdle_4":"pride","girdle_5":"pride","girdle_6":"anger","girdle_7":"fear","girdle_8":"sadness","girdle_9":"sadness","girdle_10":"peace","girdle_11":"peace","girdle_12":"peace","girdle_13":"joy"},"shares":{"joy":0.341463414634146,"fear":0.0487804878048781,"anger":0.121951219512195,"peace":0.195121951219512,"pride":0.170731707317073,"shame":0,"sadness":0.121951219512195},"worths":{"joy":14,"fear":2,"anger":5,"peace":8,"pride":7,"sadness":5},"rotation":0},"tone":{"days":[[0,0,3],[0,0,0],[0,0,0],[0,0,0],[5,0,0],[3,0,2],[0,1,1],[3,0,0],[0,2,0],[2,0,0],[0,1,2],[0,0,0],[0,1,0],[0,0,0],[2,0,0],[0,0,0],[0,0,0],[0,0,1],[1,0,0],[0,0,0],[2,0,2],[0,0,0],[0,0,0],[0,1,0],[0,0,0],[2,0,0],[0,0,1],[0,0,0],[2,0,0],[0,2,0],[3,0,0]],"last":1,"first":-1,"verdict":"bright_two_to_one"},"count":24,"cover":{"days":[{"day":0,"pebbles":[{"polarity":-1,"intensity":3,"moment_id":"872ccf91-a434-4286-a0f0-4d54d2ae9308","emotion_id":"f76b1274-04ab-4a9c-858d-4da26f8d124d"}]},{"day":4,"pebbles":[{"polarity":1,"intensity":3,"moment_id":"65d2f10c-af98-43ca-ad14-21ea6048a29e","emotion_id":"70e70f6a-237b-4e2d-8f4c-8043df889b36"},{"polarity":1,"intensity":2,"moment_id":"0950310a-769c-4d4d-88a6-1691cb04da57","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"}]},{"day":5,"pebbles":[{"polarity":-1,"intensity":2,"moment_id":"576b0ee3-9db0-4ef9-94ec-32fcc8d35bf6","emotion_id":"332cabe9-c76d-d737-bba6-f2d7024de808"},{"polarity":1,"intensity":3,"moment_id":"e8aa5e4d-6ba9-49a3-bc8e-e3b6b0f0317a","emotion_id":"5ff5cd1c-6739-6143-537f-85a2dbbd8841"}]},{"day":6,"pebbles":[{"polarity":-1,"intensity":1,"moment_id":"a4bf75d2-1743-4bd4-aedf-f38c11ccdc19","emotion_id":"b4216f65-f0a6-2a26-ca1a-9dfa839fbbeb"},{"polarity":0,"intensity":1,"moment_id":"d54ac603-e7a5-45ab-8b64-f2546f8dff97","emotion_id":"5ff5cd1c-6739-6143-537f-85a2dbbd8841"}]},{"day":7,"pebbles":[{"polarity":1,"intensity":3,"moment_id":"39e2a0a4-4ef0-4937-9a73-de97b37c38b2","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"}]},{"day":8,"pebbles":[{"polarity":0,"intensity":2,"moment_id":"dd45df2b-41a4-46e6-bae5-9a2e52e28513","emotion_id":"70e70f6a-237b-4e2d-8f4c-8043df889b36"}]},{"day":9,"pebbles":[{"polarity":1,"intensity":2,"moment_id":"76ac4594-2bc5-465e-8da3-1f374a88fa17","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"}]},{"day":10,"pebbles":[{"polarity":-1,"intensity":2,"moment_id":"86ec7e9f-fa84-4c55-b962-5aef1d0325df","emotion_id":"f76b1274-04ab-4a9c-858d-4da26f8d124d"},{"polarity":0,"intensity":1,"moment_id":"20d5b609-8d56-46c4-a9a7-c49409dff596","emotion_id":"5ff5cd1c-6739-6143-537f-85a2dbbd8841"}]},{"day":12,"pebbles":[{"polarity":0,"intensity":1,"moment_id":"49f35741-9828-45e8-850b-2ade3f322107","emotion_id":"5ff5cd1c-6739-6143-537f-85a2dbbd8841"}]},{"day":14,"pebbles":[{"polarity":1,"intensity":2,"moment_id":"a873a7f7-bee3-42e7-b3e6-7830cda44ce9","emotion_id":"70e70f6a-237b-4e2d-8f4c-8043df889b36"}]},{"day":17,"pebbles":[{"polarity":-1,"intensity":1,"moment_id":"aa947530-e2f2-438d-8c5e-709511adf5ee","emotion_id":"b4216f65-f0a6-2a26-ca1a-9dfa839fbbeb"}]},{"day":18,"pebbles":[{"polarity":1,"intensity":1,"moment_id":"d7960b78-2a57-479e-bf2a-f8d7a72d6cec","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"}]},{"day":20,"pebbles":[{"polarity":1,"intensity":2,"moment_id":"5b252a96-4687-4a60-87be-5364a0587e18","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"},{"polarity":-1,"intensity":2,"moment_id":"4312be9c-1453-4d39-b3eb-93697f9f2488","emotion_id":"332cabe9-c76d-d737-bba6-f2d7024de808"}]},{"day":23,"pebbles":[{"polarity":0,"intensity":1,"moment_id":"b9c0730f-71ef-439f-8f55-30fd52e03a1e","emotion_id":"5ff5cd1c-6739-6143-537f-85a2dbbd8841"}]},{"day":25,"pebbles":[{"polarity":1,"intensity":2,"moment_id":"cb0219b3-4321-48dc-99eb-1a249ad4e154","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"}]},{"day":26,"pebbles":[{"polarity":-1,"intensity":1,"moment_id":"3ed43c11-f5c0-4ae9-ad89-4ff238676b82","emotion_id":"332cabe9-c76d-d737-bba6-f2d7024de808"}]},{"day":28,"pebbles":[{"polarity":1,"intensity":2,"moment_id":"5342ea7e-518e-4106-8ce1-efd9b97e47f2","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"}]},{"day":29,"pebbles":[{"polarity":0,"intensity":2,"moment_id":"2aa9326b-37e9-4082-a4c4-5e62ca8ce059","emotion_id":"70e70f6a-237b-4e2d-8f4c-8043df889b36"}]},{"day":30,"pebbles":[{"polarity":1,"intensity":3,"moment_id":"a6dcf44d-3525-4539-b2c1-f7d8c03ac0ee","emotion_id":"5ff5cd1c-6739-6143-537f-85a2dbbd8841"}]}],"inks":["joy","peace","pride","anger","sadness","fear"],"papers":["bb427d8c-3597-4e94-b18b-acc3fea8d723","2d8e9624-5602-45d3-90f0-5eb16ba0fc0d","66994e63-de24-443d-149b-7b28d9af7ee3"],"souls_in_order":["00870877-d906-4bfd-b34e-a7459d695a1c","87d30f52-4ef8-47e4-8867-9c478fdde4e6","a951a6fb-c74d-43d9-bb97-607140468417","e21eb93f-df2d-4bd7-b8f1-3d56e38c589d","108ddf28-e01b-473d-b502-0219eeb623f4","1990917e-53e5-492d-879b-f27736e8e434"]},"hours":{"items":[{"happened":19.0833,"collected":23.861,"moment_id":"872ccf91-a434-4286-a0f0-4d54d2ae9308"},{"happened":10.25,"collected":23.861,"moment_id":"65d2f10c-af98-43ca-ad14-21ea6048a29e"},{"happened":19.6667,"collected":23.861,"moment_id":"0950310a-769c-4d4d-88a6-1691cb04da57"},{"happened":8.0333,"collected":23.861,"moment_id":"576b0ee3-9db0-4ef9-94ec-32fcc8d35bf6"},{"happened":16.5,"collected":23.861,"moment_id":"e8aa5e4d-6ba9-49a3-bc8e-e3b6b0f0317a"},{"happened":3.1667,"collected":23.8611,"moment_id":"a4bf75d2-1743-4bd4-aedf-f38c11ccdc19"},{"happened":12.0833,"collected":23.8611,"moment_id":"d54ac603-e7a5-45ab-8b64-f2546f8dff97"},{"happened":21,"collected":23.8611,"moment_id":"39e2a0a4-4ef0-4937-9a73-de97b37c38b2"},{"happened":11.3333,"collected":23.8611,"moment_id":"dd45df2b-41a4-46e6-bae5-9a2e52e28513"},{"happened":17.75,"collected":23.8611,"moment_id":"76ac4594-2bc5-465e-8da3-1f374a88fa17"},{"happened":14,"collected":23.8611,"moment_id":"86ec7e9f-fa84-4c55-b962-5aef1d0325df"},{"happened":22.5,"collected":23.8612,"moment_id":"20d5b609-8d56-46c4-a9a7-c49409dff596"},{"happened":7.8333,"collected":23.8612,"moment_id":"49f35741-9828-45e8-850b-2ade3f322107"},{"happened":18.5,"collected":23.8612,"moment_id":"a873a7f7-bee3-42e7-b3e6-7830cda44ce9"},{"happened":20.1667,"collected":23.8612,"moment_id":"aa947530-e2f2-438d-8c5e-709511adf5ee"},{"happened":9,"collected":23.8612,"moment_id":"d7960b78-2a57-479e-bf2a-f8d7a72d6cec"},{"happened":13.25,"collected":23.8612,"moment_id":"5b252a96-4687-4a60-87be-5364a0587e18"},{"happened":23.6667,"collected":23.8613,"moment_id":"4312be9c-1453-4d39-b3eb-93697f9f2488"},{"happened":11,"collected":23.8613,"moment_id":"b9c0730f-71ef-439f-8f55-30fd52e03a1e"},{"happened":8.5,"collected":23.8613,"moment_id":"cb0219b3-4321-48dc-99eb-1a249ad4e154"},{"happened":18.1667,"collected":23.8613,"moment_id":"3ed43c11-f5c0-4ae9-ad89-4ff238676b82"},{"happened":12.6667,"collected":23.8613,"moment_id":"5342ea7e-518e-4106-8ce1-efd9b97e47f2"},{"happened":22.25,"collected":23.8613,"moment_id":"2aa9326b-37e9-4082-a4c4-5e62ca8ce059"},{"happened":16,"collected":23.8614,"moment_id":"a6dcf44d-3525-4539-b2c1-f7d8c03ac0ee"}],"busiest":{"count":24,"start":20}},"month":"2026-10-01","weeks":[{"to":3,"days":[[0,0],[0,0],[0,0],[1,3],[0,0],[0,0],[0,0]],"from":0,"count":1,"soul_ids":[],"week_start":"2026-09-28"},{"to":10,"days":[[2,3],[2,3],[2,1],[1,3],[1,2],[1,2],[2,2]],"from":4,"count":11,"soul_ids":["00870877-d906-4bfd-b34e-a7459d695a1c","87d30f52-4ef8-47e4-8867-9c478fdde4e6","a951a6fb-c74d-43d9-bb97-607140468417","e21eb93f-df2d-4bd7-b8f1-3d56e38c589d","108ddf28-e01b-473d-b502-0219eeb623f4"],"week_start":"2026-10-05"},{"to":17,"days":[[0,0],[1,1],[0,0],[1,2],[0,0],[0,0],[1,1]],"from":11,"count":3,"soul_ids":[],"week_start":"2026-10-12"},{"to":24,"days":[[1,1],[0,0],[2,2],[0,0],[0,0],[1,1],[0,0]],"from":18,"count":4,"soul_ids":["1990917e-53e5-492d-879b-f27736e8e434"],"week_start":"2026-10-19"},{"to":30,"days":[[1,2],[1,1],[0,0],[1,2],[1,2],[1,3],[0,0]],"from":25,"count":5,"soul_ids":["a951a6fb-c74d-43d9-bb97-607140468417","108ddf28-e01b-473d-b502-0219eeb623f4","87d30f52-4ef8-47e4-8867-9c478fdde4e6"],"week_start":"2026-10-26"}],"domains":[{"days":[0,4,5,10,25,30],"count":7,"domain_id":"bb427d8c-3597-4e94-b18b-acc3fea8d723"},{"days":[4,8,17,20,29],"count":5,"domain_id":"2d8e9624-5602-45d3-90f0-5eb16ba0fc0d"},{"days":[6,7,20,28],"count":4,"domain_id":"66994e63-de24-443d-149b-7b28d9af7ee3"},{"days":[8,10,18],"count":3,"domain_id":"2c282fad-4fe5-41e3-b1a7-2d634ec84c77"},{"days":[12,23,26],"count":3,"domain_id":"4f1fec32-318e-4fc1-b095-7ed793ebcaac"},{"days":[14,28,29],"count":3,"domain_id":"574216e8-8a96-9059-b3de-cfa568b76016"},{"days":[0,6],"count":2,"domain_id":"4f0f40fc-d7e6-372f-070b-44900032a23f"},{"days":[4],"count":1,"domain_id":"55a005c8-e2af-4f40-ad19-bd6914bee484"},{"days":[5],"count":1,"domain_id":"122b15ed-d1ea-4764-8a4e-63b3f79f0ae3"},{"days":[5],"count":1,"domain_id":"d1778780-e668-4bff-a70c-06ba3e5ffec1"},{"days":[9],"count":1,"domain_id":"6286b53e-a528-4151-94f1-10cf943a61b3"}],"persons":[{"days":[4,5,7,9,30],"count":5,"soul_id":"87d30f52-4ef8-47e4-8867-9c478fdde4e6","new_since_previous":false},{"days":[4,5,25,30],"count":4,"soul_id":"a951a6fb-c74d-43d9-bb97-607140468417","new_since_previous":false},{"days":[6,7,28],"count":3,"soul_id":"108ddf28-e01b-473d-b502-0219eeb623f4","new_since_previous":true},{"days":[4,7],"count":2,"soul_id":"00870877-d906-4bfd-b34e-a7459d695a1c","new_since_previous":false},{"days":[5,10],"count":2,"soul_id":"e21eb93f-df2d-4bd7-b8f1-3d56e38c589d","new_since_previous":true},{"days":[20],"count":1,"soul_id":"1990917e-53e5-492d-879b-f27736e8e434","new_since_previous":true}],"sections":["weeks","milestones","persons","gem","domains","tone","hours"],"milestones":["65d2f10c-af98-43ca-ad14-21ea6048a29e","e8aa5e4d-6ba9-49a3-bc8e-e3b6b0f0317a","39e2a0a4-4ef0-4937-9a73-de97b37c38b2","a6dcf44d-3525-4539-b2c1-f7d8c03ac0ee","872ccf91-a434-4286-a0f0-4d54d2ae9308"],"people_count":6,"days_in_month":31,"fine_emotions":[{"days":[4,7,9,18,20,25,28],"count":7,"family":"joy","emotion_id":"50a68890-f6bb-e67a-6655-e84ff0b71f49"},{"days":[5,6,10,12,23,30],"count":6,"family":"peace","emotion_id":"5ff5cd1c-6739-6143-537f-85a2dbbd8841"},{"days":[4,8,14,29],"count":4,"family":"pride","emotion_id":"70e70f6a-237b-4e2d-8f4c-8043df889b36"},{"days":[5,20,26],"count":3,"family":"anger","emotion_id":"332cabe9-c76d-d737-bba6-f2d7024de808"},{"days":[0,10],"count":2,"family":"sadness","emotion_id":"f76b1274-04ab-4a9c-858d-4da26f8d124d"},{"days":[6,17],"count":2,"family":"fear","emotion_id":"b4216f65-f0a6-2a26-ca1a-9dfa839fbbeb"}],"first_weekday":3,"previous_month":{"count":3,"soul_ids":["87d30f52-4ef8-47e4-8867-9c478fdde4e6","a951a6fb-c74d-43d9-bb97-607140468417","00870877-d906-4bfd-b34e-a7459d695a1c"]}}
"""##
    // swiftlint:enable line_length
}
#endif
