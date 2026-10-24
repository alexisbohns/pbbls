package app.pbbls.android.features.path.wrap.month

import kotlin.math.abs
import kotlin.math.min

/** The dial's arithmetic (#1207, D12): display only, over the server's local decimal hours. */
internal object HoursLayout {
    /** Hours from [from] to [to], clockwise on a day: 22.5 → 1.0 is 2.5. */
    fun forward(
        from: Double,
        to: Double,
    ): Double = ((to - from) % 24 + 24) % 24

    /**
     * Lanes for marks on a ring so none touch (the board's swarm): earliest first, each into the
     * innermost lane where it clears every mark already there. [gapPx] is the length of one hour on
     * a lane's circle, [sizes] each mark's width in px.
     */
    fun lanes(
        hours: List<Double>,
        sizes: List<Float>,
        gapPx: (Int) -> Float,
    ): List<Int> {
        val order = hours.indices.sortedBy { hours[it] }
        val lane = IntArray(hours.size)
        val placed = mutableListOf<Int>()
        order.forEach { i ->
            var l = 0
            while (placed.any { j -> lane[j] == l && circular(hours[i], hours[j]) * gapPx(l) < (sizes[i] + sizes[j]) / 2 + CLEARANCE }) l++
            lane[i] = l
            placed += i
        }
        return lane.toList()
    }

    private fun circular(
        a: Double,
        b: Double,
    ): Float = min(abs(a - b), 24 - abs(a - b)).toFloat()

    private const val CLEARANCE = 1.4f
}
