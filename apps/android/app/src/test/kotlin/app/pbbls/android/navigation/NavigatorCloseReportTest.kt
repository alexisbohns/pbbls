package app.pbbls.android.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.pbbls.android.core.model.ReportTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** A finished report closes once, however many times its exit fires (#839). */
class NavigatorCloseReportTest {
    private fun navigatorOn(vararg path: PebblesKey): Pair<Navigator, NavigationState> {
        val backStacks =
            PebblesKey.tabs.associateWith { tab ->
                NavBackStack<NavKey>(*(if (tab == PebblesKey.People) path.toList() else listOf(tab)).toTypedArray())
            }
        val state =
            NavigationState(
                startRoute = PebblesKey.Home,
                topLevelRoute = mutableStateOf(PebblesKey.People),
                backStacks = backStacks,
            )
        return Navigator(state) to state
    }

    private val connection = PebblesKey.Report(ReportTarget.Connection("c1", "Lea"))
    private val invite = PebblesKey.Report(ReportTarget.Invite("tok", "Lea"))

    private fun Pair<Navigator, NavigationState>.stack() = second.backStacks.getValue(PebblesKey.People).toList()

    @Test
    fun `a finished connection report pops only itself`() {
        val nav = navigatorOn(PebblesKey.People, PebblesKey.Connections, connection)

        nav.first.closeReport(connection)

        assertEquals(listOf<NavKey>(PebblesKey.People, PebblesKey.Connections), nav.stack())
    }

    @Test
    fun `a finished invite report pops the invite beneath it too`() {
        val nav = navigatorOn(PebblesKey.People, PebblesKey.AcceptInvite("tok"), invite)

        nav.first.closeReport(invite)

        assertEquals(listOf<NavKey>(PebblesKey.People), nav.stack())
    }

    /** The regression: Done twice, or Done and Back, used to pop the invite and then the page under it. */
    @Test
    fun `a second finish does not pop what is beneath`() {
        val nav = navigatorOn(PebblesKey.People, PebblesKey.Connections, PebblesKey.AcceptInvite("tok"), invite)

        nav.first.closeReport(invite)
        nav.first.closeReport(invite)

        assertEquals(listOf<NavKey>(PebblesKey.People, PebblesKey.Connections), nav.stack())
    }

    @Test
    fun `an invite report leaves an unrelated page beneath alone`() {
        val nav = navigatorOn(PebblesKey.People, PebblesKey.Connections, invite)

        nav.first.closeReport(invite)

        assertEquals(listOf<NavKey>(PebblesKey.People, PebblesKey.Connections), nav.stack())
    }

    @Test
    fun `closing a report that is not on top does nothing`() {
        val nav = navigatorOn(PebblesKey.People, connection, PebblesKey.Connections)

        nav.first.closeReport(connection)

        assertEquals(listOf<NavKey>(PebblesKey.People, connection, PebblesKey.Connections), nav.stack())
    }
}
