import Charts
import SwiftUI

/// The tone of the month (#1226, D11, decision B1): for each day, a bar up for
/// bright and a bar down for heavy, as tall as their summed intensities, and a
/// circle on the line for even, growing with its own; a quiet dot on a still
/// day. The 1st, the Mondays and the last day are numbered. The sentence reads
/// the server's verdict and the month's first and last pebbles.
///
/// Drawn with Swift Charts, so VoiceOver walks it day by day (each day with
/// pebbles one element: its date, then its bright, even and heavy sums) and
/// Audio Graphs can play it. Its geometry is Android's to the point: the axes
/// are hidden and the scales map one unit of intensity to `ToneMetrics.unit`.
/// Android: `ToneFigure`.
struct ToneFigure: View {
    let wrap: MonthWrap

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        if let tone = wrap.tone, !tone.days.isEmpty {
            let line = WrapCopy.toneLine(tone.verdict, first: tone.first, last: tone.last, locale: locale)
            VStack(alignment: .leading, spacing: 0) {
                SpineSectionHeader(WrapCopy.toneTitle(locale: locale), hint: WrapCopy.toneHint(locale: locale))
                    .introReveal(.header)
                Text(verbatim: line)
                    .spineType(.still)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 6)
                    .padding(.bottom, 8)
                    .introReveal(.lead)
                IntroSweep { ToneChart(wrap: wrap, tone: tone, summary: line) }
            }
            .padding(.horizontal, MonthLayout.sectionPadX)
        }
    }
}

extension ToneFigure {
    /// The tone's intro (#1241), with the insights head's when it leads them; none when nothing shows.
    static func introBeats(wrap: MonthWrap, insights: Bool) -> [IntroBeat] {
        let days = wrap.tone?.days ?? []
        guard !days.isEmpty else { return insights ? IntroScore.insightsHead() : [] }
        return IntroScore.tone(days: days, insights: insights)
    }
}

/// The tone chart under the intro's sweep (#1241): hidden before it, masked
/// from the leading edge while it runs, untouched once it has passed. The
/// chart is a stored value, so only the mask follows the frames.
private struct IntroSweep<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        IntroReader { reveal in
            let sweep = reveal(.sweep)
            if sweep >= 1 {
                content
            } else if sweep <= 0 {
                content.hidden()
            } else {
                content.mask(alignment: .leading) {
                    GeometryReader { geometry in
                        Rectangle()
                            .frame(width: ToneMetrics.sweepWidth(IntroMotion.easeInOut(sweep),
                                                                 width: geometry.size.width))
                    }
                }
            }
        }
        // The chart's days run left to right whatever the language; the mask follows them.
        .environment(\.layoutDirection, .leftToRight)
    }
}

/// The figure's scale (Android's constants): one unit of intensity is `unit`
/// points, at most 8, shrinking in a heavy month so the bars never pass 64.
struct ToneMetrics: Equatable {
    static let top: CGFloat = 14
    static let axis: CGFloat = 30
    static let bar: CGFloat = 5.2
    static let barGap: CGFloat = 3
    static let evenBase: CGFloat = 2.4
    static let evenStep: CGFloat = 1.25
    static let quiet: CGFloat = 1
    static let inset: CGFloat = 10
    static let lineInset: CGFloat = 4
    private static let unitMax: CGFloat = 8
    private static let maxSide: CGFloat = 64
    private static let minScale = 3

    let maxUp: Int
    let maxDown: Int
    let unit: CGFloat

    init(days: [ToneDay]) {
        maxUp = max(Self.minScale, days.map(\.up).max() ?? 0)
        maxDown = max(Self.minScale, days.map(\.down).max() ?? 0)
        unit = min(Self.unitMax, Self.maxSide / CGFloat(max(maxUp, maxDown)))
    }

    var height: CGFloat { Self.top + unit * CGFloat(maxUp) + unit * CGFloat(maxDown) + Self.axis }
    /// The line's distance from the top.
    var axisY: CGFloat { Self.top + unit * CGFloat(maxUp) }
    /// The bars' gap from the line, in units.
    var gap: Double { Double(Self.barGap / unit) }
    /// The whole frame in units, so the plot fills it at `unit` points per unit.
    var domain: ClosedRange<Double> {
        -(Double(maxDown) + Double(Self.axis / unit))...(Double(maxUp) + Double(Self.top / unit))
    }

    /// An even day's circle: its radius grows with its summed intensity.
    static func evenRadius(_ even: Int) -> CGFloat { evenBase + evenStep * CGFloat(even) }
}

extension ToneMetrics {
    /// How much of the chart the intro's sweep shows at eased progress
    /// `eased`: the days run from `inset` to `width - inset`, and the sweep
    /// leads the day it reaches by half a bar.
    static func sweepWidth(_ eased: Double, width: CGFloat) -> CGFloat {
        inset + CGFloat(eased) * (width - 2 * inset) + bar / 2
    }
}

/// The chart and its words; its own view so the references draw it alone.
struct ToneChart: View {
    let wrap: MonthWrap
    let tone: Tone
    let summary: String

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        let metrics = ToneMetrics(days: tone.days)
        let days = Array(tone.days.enumerated())
        Chart {
            ForEach(days, id: \.offset) { index, day in
                if day.up > 0 {
                    BarMark(x: .value("day", index), yStart: .value("tone", metrics.gap),
                            yEnd: .value("tone", metrics.gap + Double(day.up)), width: .fixed(ToneMetrics.bar))
                        .foregroundStyle(Color.pebbles.onSurface)
                        .clipShape(Capsule())
                        .accessibilityHidden(true)
                }
                if day.down > 0 {
                    BarMark(x: .value("day", index), yStart: .value("tone", -metrics.gap),
                            yEnd: .value("tone", -metrics.gap - Double(day.down)), width: .fixed(ToneMetrics.bar))
                        .foregroundStyle(Color.pebbles.outline)
                        .clipShape(Capsule())
                        .accessibilityHidden(true)
                }
                if day.even > 0 {
                    PointMark(x: .value("day", index), y: .value("tone", 0))
                        .symbol {
                            let radius = ToneMetrics.evenRadius(day.even)
                            Circle().stroke(Color.pebbles.onSurfaceVariant, lineWidth: 1)
                                .frame(width: radius * 2, height: radius * 2)
                        }
                        .accessibilityHidden(true)
                } else if day.up == 0 && day.down == 0 {
                    PointMark(x: .value("day", index), y: .value("tone", 0))
                        .symbol {
                            Circle().fill(Color.pebbles.rule)
                                .frame(width: ToneMetrics.quiet * 2, height: ToneMetrics.quiet * 2)
                        }
                        .accessibilityHidden(true)
                }
                if day.up + day.even + day.down > 0 {
                    PointMark(x: .value("day", index), y: .value("tone", 0))
                        .opacity(0)
                        .accessibilityLabel(Text(verbatim: dayName(index)))
                        .accessibilityValue(Text(verbatim: dayValue(day)))
                }
            }
        }
        .chartXScale(domain: 0...max(1, tone.days.count - 1), range: .plotDimension(padding: ToneMetrics.inset))
        .chartYScale(domain: metrics.domain, range: .plotDimension(padding: 0))
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .chartLegend(.hidden)
        .chartBackground { proxy in
            GeometryReader { geometry in
                let plot = proxy.plotFrame.map { geometry[$0] } ?? geometry.frame(in: .local)
                let y = plot.minY + (proxy.position(forY: 0.0) ?? metrics.axisY)
                Path { path in
                    path.move(to: CGPoint(x: ToneMetrics.lineInset, y: y))
                    path.addLine(to: CGPoint(x: geometry.size.width - ToneMetrics.lineInset, y: y))
                }
                .stroke(Color.pebbles.rule, lineWidth: 1)
            }
        }
        .chartOverlay { proxy in
            GeometryReader { geometry in
                let plot = proxy.plotFrame.map { geometry[$0] } ?? geometry.frame(in: .local)
                words(proxy: proxy, plot: plot, metrics: metrics)
                pulses(proxy: proxy, plot: plot)
            }
            .accessibilityHidden(true)
        }
        .accessibilityChartDescriptor(ToneChartDescriptor(wrap: wrap, tone: tone, summary: summary, locale: locale))
        // Days run left to right whatever the language, as on the dial.
        .environment(\.layoutDirection, .leftToRight)
        .frame(height: metrics.height)
    }

    /// The intro's pulse on each even day as the sweep reaches it (#1241): a
    /// ring growing out of the circle and fading. Nothing when settled.
    private func pulses(proxy: ChartProxy, plot: CGRect) -> some View {
        IntroReader { reveal in
            ZStack(alignment: .topLeading) {
                Color.clear
                ForEach(tone.days.indices.filter { tone.days[$0].even > 0 }, id: \.self) { index in
                    let t = reveal(.day(index))
                    if t > 0 && t < 1 {
                        let radius = ToneMetrics.evenRadius(tone.days[index].even)
                        Circle()
                            .stroke(Color.pebbles.onSurfaceVariant, lineWidth: 1)
                            .frame(width: radius * 2, height: radius * 2)
                            .scaleEffect(CGFloat(1 + t))
                            .opacity(1 - t)
                            .position(x: plot.minX + (proxy.position(forX: index) ?? 0),
                                      y: plot.minY + (proxy.position(forY: 0.0) ?? 0))
                    }
                }
            }
        }
    }

    /// The numbered days along the foot, and the two words at the start of each side.
    private func words(proxy: ChartProxy, plot: CGRect, metrics: ToneMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            Color.clear
            // Every place from the proxy, so the words follow the marks whatever the plot's frame.
            ForEach(wrap.numberedDays.filter { $0 < tone.days.count }, id: \.self) { day in
                let x = plot.minX + (proxy.position(forX: day) ?? 0)
                Text(verbatim: "\(day + 1)")
                    .spineType(.dayLetter)
                    .foregroundStyle(Color.pebbles.outline)
                    .fixedSize()
                    .alignmentGuide(.leading) { $0[HorizontalAlignment.center] - x }
                    .alignmentGuide(.top) { $0[.bottom] - (plot.maxY - 2) }
            }
            word(.highlight)
                .alignmentGuide(.leading) { _ in -ToneMetrics.lineInset }
                .alignmentGuide(.top) { _ in -plot.minY }
            word(.lowlight)
                .alignmentGuide(.leading) { _ in -ToneMetrics.lineInset }
                .alignmentGuide(.top) { _ in
                    -(plot.minY + (proxy.position(forY: -Double(metrics.maxDown)) ?? metrics.axisY))
                }
        }
        .dynamicTypeSize(...DynamicTypeSize.large)
    }

    private func word(_ polarity: ValencePolarity) -> some View {
        Text(verbatim: WrapCopy.polarity(polarity, locale: locale))
            .spineType(.dayLetter)
            .foregroundStyle(Color.pebbles.outline)
            .fixedSize()
    }

    /// "Monday 5 October", for VoiceOver.
    private func dayName(_ index: Int) -> String {
        let day = CalendarDay(year: wrap.month.year, month: wrap.month.month, day: index + 1)
        guard let date = day.date(in: SpineCalendar.current.timeZone) else { return "\(index + 1)" }
        let style = Date.FormatStyle(locale: SpineStrings.dateLocale, timeZone: SpineCalendar.current.timeZone)
        return date.formatted(style.weekday(.wide).day().month(.wide))
    }

    /// "bright 3, heavy 1": the day's sums, by polarity, those it has.
    private func dayValue(_ day: ToneDay) -> String {
        [(ValencePolarity.highlight, day.up), (.neutral, day.even), (.lowlight, day.down)]
            .filter { $0.1 > 0 }
            .map { "\(WrapCopy.polarity($0.0, locale: locale)) \($0.1)" }
            .joined(separator: ", ")
    }
}

/// The month's tone for Audio Graphs: the days along x, bright up and heavy
/// down along y, and even on its own series.
struct ToneChartDescriptor: AXChartDescriptorRepresentable {
    let wrap: MonthWrap
    let tone: Tone
    let summary: String
    let locale: Locale

    func makeChartDescriptor() -> AXChartDescriptor {
        let count = Double(tone.days.count)
        let maxUp = Double(tone.days.map { max($0.up, $0.even) }.max() ?? 0)
        let maxDown = Double(tone.days.map(\.down).max() ?? 0)
        let xAxis = AXNumericDataAxisDescriptor(title: WrapCopy.monthName(wrap.month, locale: locale),
                                                range: 1...max(2, count), gridlinePositions: []) { "\(Int($0))" }
        let yAxis = AXNumericDataAxisDescriptor(title: WrapCopy.toneTitle(locale: locale),
                                                range: -max(1, maxDown)...max(1, maxUp),
                                                gridlinePositions: [0]) { "\(Int(abs($0)))" }
        func series(_ polarity: ValencePolarity, _ value: (ToneDay) -> Double) -> AXDataSeriesDescriptor {
            AXDataSeriesDescriptor(name: WrapCopy.polarity(polarity, locale: locale), isContinuous: false,
                                   dataPoints: tone.days.enumerated().map {
                                       AXDataPoint(x: Double($0 + 1), y: value($1))
                                   })
        }
        return AXChartDescriptor(title: WrapCopy.toneTitle(locale: locale), summary: summary, xAxis: xAxis,
                                 yAxis: yAxis, additionalAxes: [],
                                 series: [series(.highlight) { Double($0.up) }, series(.neutral) { Double($0.even) },
                                          series(.lowlight) { -Double($0.down) }])
    }
}
