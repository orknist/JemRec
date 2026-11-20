package com.jemcik.jemrec.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jemcik.jemrec.R
import com.jemcik.jemrec.calls.CallRules
import com.jemcik.jemrec.calls.CallRules.Contacts
import com.jemcik.jemrec.calls.VoipApps
import com.jemcik.jemrec.recorder.OffMode
import com.jemcik.jemrec.recordings.FileNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** What Settings says a setting is set to: the summaries are the overview now,
 *  so a wrong one is a wrong answer at a glance. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36])
class SettingsSummaryTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val backOn = ctx.getString(R.string.off_back_on)

    private val allowed = UiState(canReadCallLog = true, canReadContacts = true)
    private fun chosen(vararg names: String) = names.map { CallRules.Chosen(it, it, null) }

    @Test fun withoutPermissionEveryCallIsKept() {
        // The rules cannot apply without knowing who called - so whatever is
        // stored, the row must not claim a choice is in force.
        val state = UiState(ruleContacts = Contacts.NONE, ruleOtherNumbers = false)
        assertEquals("Every call", rulesSummary(ctx, state).text)
        assertFalse(rulesSummary(ctx, state).nothing)
    }

    @Test fun allButNobodyIsAll() {
        // It read "Every call is kept, except calls with 0 contacts".
        val state = allowed.copy(ruleContacts = Contacts.ALL_BUT)
        assertEquals("Every call", rulesSummary(ctx, state).text)
        assertEquals("Every call is kept.", rulesSentence(ctx, state).text)
        val noStrangers = state.copy(ruleOtherNumbers = false)
        assertEquals("Your contacts, not other numbers", rulesSummary(ctx, noStrangers).text)
    }

    @Test fun allButSomeNamesHowMany() {
        val one = allowed.copy(ruleContacts = Contacts.ALL_BUT, ruleChosen = chosen("A"))
        assertEquals("Everyone but 1 contact", rulesSummary(ctx, one).text)
        val two = one.copy(ruleChosen = chosen("A", "B"))
        assertEquals("Every call is kept, except calls with 2 contacts.", rulesSentence(ctx, two).text)
    }

    @Test fun settingsThatKeepNothingAreFlagged() {
        val none = allowed.copy(ruleContacts = Contacts.NONE, ruleOtherNumbers = false)
        assertTrue(rulesSummary(ctx, none).nothing)
        assertTrue(rulesSentence(ctx, none).nothing)
        val onlyNobody = allowed.copy(ruleContacts = Contacts.ONLY, ruleOtherNumbers = false)
        assertTrue(rulesSummary(ctx, onlyNobody).nothing)
        // Strangers still kept: something is.
        assertFalse(rulesSummary(ctx, onlyNobody.copy(ruleOtherNumbers = true)).nothing)
        assertEquals("Only numbers not in your contacts", rulesSummary(ctx, onlyNobody.copy(ruleOtherNumbers = true)).text)
    }

    @Test fun messengerRowSaysWhyBeforeWhat() {
        assertEquals("Needs Android 14 or later", messengerSummary(ctx, UiState(voipSupported = false)))
        assertEquals("None of the supported apps is installed", messengerSummary(ctx, UiState(voipSupported = true)))
        val installed = UiState(voipSupported = true, voipInstalled = VoipApps.KNOWN.filter { it.tag != "signal" })
        assertEquals("Off", messengerSummary(ctx, installed))
        val on = installed.copy(voipEnabled = setOf("com.viber.voip", "com.whatsapp"))
        // In the order the apps are listed, not the order they were switched on.
        assertEquals("WhatsApp, Viber", messengerSummary(ctx, on))
        assertEquals(
            "WhatsApp, Google Meet",
            messengerSummary(ctx, installed.copy(voipEnabled = setOf("com.google.android.apps.tachyon", "com.whatsapp"))),
        )
        // Two Telegrams on, told apart as on their screen (issue #93).
        assertEquals(
            "Telegram, Telegram (telegram.org)",
            messengerSummary(ctx, installed.copy(voipEnabled = setOf("org.telegram.messenger.web", "org.telegram.messenger"))),
        )
    }

    @Test fun startWithoutWifiAlwaysSaysThePortIsOpenToTheNetwork() {
        // The cost is the summary, whichever way it is set.
        val off = UiState(startWithoutWifi = false)
        val waiting = UiState(startWithoutWifi = true)
        val open = waiting.copy(portSet = true)
        listOf(off, waiting, open).forEach { assertTrue("5555" in startWithoutWifiSummary(ctx, it)) }
        assertTrue("any network" in startWithoutWifiSummary(ctx, off))
        assertTrue("any network" in startWithoutWifiSummary(ctx, open))
        // Open until a reboot, which only Wi-Fi undoes once.
        assertTrue("After a restart, JemRec needs Wi-Fi once" in startWithoutWifiSummary(ctx, open))
        assertTrue("next time JemRec starts on Wi-Fi" in startWithoutWifiSummary(ctx, waiting))
    }

    @Test fun startWithoutWifiSaysWhenThePhoneWouldNotOpenIt() {
        val failed = UiState(startWithoutWifi = true, portFailed = true)
        assertEquals("This phone would not open port 5555. JemRec asks again after a restart", startWithoutWifiSummary(ctx, failed))
        assertTrue(startWithoutWifiFailed(failed))
        // Switched off, a failure from earlier this boot is not news.
        assertFalse("would not" in startWithoutWifiSummary(ctx, failed.copy(startWithoutWifi = false)))
        assertFalse(startWithoutWifiFailed(failed.copy(startWithoutWifi = false)))
    }

    @Test fun anOpenPortOutranksAnEarlierFailure() {
        // An adbd slow to come back was taken for a failure, then the port
        // was there after all: the switch must not deny it, in red.
        val open = UiState(startWithoutWifi = true, portFailed = true, portSet = true)
        assertTrue(startWithoutWifiSummary(ctx, open).startsWith("Debugging port 5555 is open"))
        assertFalse(startWithoutWifiFailed(open))
    }

    @Test fun switchedOffThePortItOpenedIsStillSaid() {
        // The close waits for a Wi-Fi with Wireless debugging - not one
        // Android refuses it on, where the revive comes over the port itself -
        // and for JemRec back on if its off took debugging; until then the
        // port is open.
        val closing = UiState(startWithoutWifi = false, portSet = true, portOurs = true)
        assertEquals(
            "Debugging port 5555 is still open. It closes the next time JemRec is on a Wi-Fi with Wireless debugging, or the phone restarts",
            startWithoutWifiSummary(ctx, closing),
        )
        // A computer's port is not JemRec's to close, nor to speak for.
        assertTrue(startWithoutWifiSummary(ctx, closing.copy(portOurs = false)).startsWith("Turn JemRec back on"))
    }

    @Test fun aCloseThatDidNotTakeIsSaidInRed() {
        // Switched off, JemRec's port still set after it asked to close it:
        // open until a restart, and nothing in the off text would say so.
        val stuck = UiState(startWithoutWifi = false, portSet = true, portOurs = true, portCloseFailed = true)
        assertEquals(
            "This phone would not close debugging port 5555. It stays open until the phone restarts",
            startWithoutWifiSummary(ctx, stuck),
        )
        assertTrue(startWithoutWifiFailed(stuck))
        assertTrue(startWithoutWifiSummary(ctx, stuck.copy(debuggingOff = true)).contains("Shut while debugging is off"))
        // Closed after all, or a computer's port: nothing to say in red.
        assertFalse(startWithoutWifiFailed(stuck.copy(portSet = false)))
        assertFalse(startWithoutWifiFailed(stuck.copy(portOurs = false)))
        // Switched on again, the port it kept is simply open...
        assertTrue(startWithoutWifiSummary(ctx, stuck.copy(startWithoutWifi = true)).startsWith("Debugging port 5555 is open"))
        assertFalse(startWithoutWifiFailed(stuck.copy(startWithoutWifi = true)))
        // ...and, gone meanwhile (a computer's `adb usb`), not blamed on an
        // open that was never tried.
        val reopening = stuck.copy(startWithoutWifi = true, portSet = false)
        assertFalse("would not" in startWithoutWifiSummary(ctx, reopening))
        assertFalse(startWithoutWifiFailed(reopening))
    }

    @Test fun whenOffsFooterAgreesWithItsLinesAboutThePort() {
        val pointer = "switch on Start without Wi-Fi"
        val staysOpen = "JemRec is off"
        // No port: how to come back away from Wi-Fi.
        assertTrue(whenOffFooter(ctx, UiState()).any { pointer in it })
        // The switch on: the cost while off.
        val on = whenOffFooter(ctx, UiState(startWithoutWifi = true))
        assertTrue(on.any { staysOpen in it } && on.none { pointer in it })
        // A port open with the switch off - a computer's, or a close that did
        // not take: Back on says anywhere above, so no pointer to the switch,
        // and the open port said as for the switch.
        val open = whenOffFooter(ctx, UiState(startWithoutWifi = false, portSet = true))
        assertTrue(offModeLines(ctx, OffMode.ALL_DEBUGGING_OFF, portSet = true).toMap().getValue(backOn).startsWith("anywhere"))
        assertTrue(open.any { staysOpen in it } && open.none { pointer in it })
        // Never "is open": with all debugging off nothing listens.
        assertTrue((on + open).none { "5555 is open" in it })
    }

    @Test fun withAllDebuggingOffThePortIsSaidClosed() {
        // adbd is stopped then, so nothing listens on 5555 - what that choice
        // is for. Switched on, and switched off with JemRec's port to close.
        val on = UiState(startWithoutWifi = true, portSet = true, debuggingOff = true)
        val closing = UiState(startWithoutWifi = false, portSet = true, portOurs = true, debuggingOff = true)
        listOf(on, closing).forEach {
            assertTrue(startWithoutWifiSummary(ctx, it).startsWith("Debugging port 5555 is closed while debugging is off"))
            assertFalse(startWithoutWifiFailed(it))
        }
        assertTrue("with or without Wi-Fi" in startWithoutWifiSummary(ctx, on))
        assertTrue("closes once JemRec is on a Wi-Fi with Wireless debugging" in startWithoutWifiSummary(ctx, closing))
        // Not set, the off text is the same with debugging off or on.
        assertEquals(
            startWithoutWifiSummary(ctx, UiState(startWithoutWifi = false)),
            startWithoutWifiSummary(ctx, UiState(startWithoutWifi = false, debuggingOff = true)),
        )
    }

    @Test fun backOnAfterAllDebuggingOffIsAnywhereWithThePort() {
        val back = { portSet: Boolean -> offModeLines(ctx, OffMode.ALL_DEBUGGING_OFF, portSet).toMap().getValue(backOn) }
        assertTrue(back(false).startsWith("needs Wi-Fi"))
        assertTrue(back(true).startsWith("anywhere"))
        // The other two are instant anywhere already.
        assertEquals(
            offModeLines(ctx, OffMode.HIDE_DEVELOPER_OPTIONS, portSet = false),
            offModeLines(ctx, OffMode.HIDE_DEVELOPER_OPTIONS, portSet = true),
        )
    }

    @Test fun fileNamesSayWhatIsMissing() {
        assertNull(fileNamesProblem(ctx, UiState(fileNames = FileNames.Format.DATE)))
        listOf(FileNames.Format.DATE_AND_CALLER, FileNames.Format.DATE_AND_NUMBER).forEach { format ->
            assertEquals(
                "Needs your call log. Named by date and time until then.",
                fileNamesProblem(ctx, UiState(fileNames = format)),
            )
            assertNull(fileNamesProblem(ctx, UiState(fileNames = format, canReadCallLog = true)))
        }
        val honor = UiState(fileNames = FileNames.Format.HONOR, canReadCallLog = true)
        assertEquals("Not saving to ${FileNames.HONOR_FOLDER} yet", fileNamesProblem(ctx, honor))
        assertNull(fileNamesProblem(ctx, honor.copy(inHonorFolder = true)))
    }
}
