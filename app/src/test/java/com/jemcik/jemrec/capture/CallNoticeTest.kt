package com.jemcik.jemrec.capture

import androidx.test.core.app.ApplicationProvider
import com.jemcik.jemrec.R
import com.jemcik.jemrec.capture.CallNotice.Notice
import com.jemcik.jemrec.capture.CallNotice.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** What the call service's notification says, in its order of precedence. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36])
class CallNoticeTest {

    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun notice(titleRes: Int, textRes: Int, deferred: Boolean, ticket: String?) =
        Notice(titleRes, textRes, deferred, ticket)

    private fun resolved(n: Notice) = notice(
        n.titleRes, n.textRes, n.deferred, n.buttonTicket,
    ).let { it to (res.getString(it.titleRes) to res.getString(it.textRes)) }

    private val idle = State(
        callUp = false, recording = false, notRecording = false, messageTicket = null,
        testing = false, checkingRecorder = false, warningOnly = false,
    )

    private val call = notice(R.string.notice_call_progress, R.string.notice_handling, false, null)
    private val recording = notice(R.string.notice_recording_call, R.string.notice_saving, false, null)
    private val notRecording = notice(R.string.notice_not_recording, R.string.notice_tap_why, false, null)
    private val message = notice(R.string.notice_taking_message, R.string.notice_taking_message_body, false, "t")
    private val test = notice(R.string.notice_testing_am, R.string.notice_testing_am_body, false, "t")
    private val cable = notice(R.string.notice_checking_recorder, R.string.notice_usb_cable, true, null)
    private val warning = notice(R.string.notice_checking_jemrec, R.string.notice_keeps_running, true, null)

    @Test fun eachOnItsOwn() {
        assertEquals(call, CallNotice.of(idle))
        assertEquals(call, CallNotice.of(idle.copy(callUp = true)))
        assertEquals(recording, CallNotice.of(idle.copy(recording = true)))
        assertEquals(notRecording, CallNotice.of(idle.copy(notRecording = true)))
        assertEquals(message, CallNotice.of(idle.copy(messageTicket = "t")))
        assertEquals(test, CallNotice.of(idle.copy(messageTicket = "t", testing = true)))
        assertEquals(cable, CallNotice.of(idle.copy(checkingRecorder = true)))
        assertEquals(warning, CallNotice.of(idle.copy(warningOnly = true)))
        // Testing without a message being taken says nothing of it.
        assertEquals(call, CallNotice.of(idle.copy(testing = true)))
    }

    @Test fun aNoticeReadsOutAsItIs() {
        val notice = CallNotice.of(idle.copy(messageTicket = "t"))
        assertEquals("Taking a message", res.getString(notice.titleRes))
        assertEquals("Your greeting is playing; your microphone is muted", res.getString(notice.textRes))
        assertEquals(false, notice.deferred)
        assertEquals("t", notice.buttonTicket)
        assertEquals(true, CallNotice.of(idle.copy(checkingRecorder = true)).deferred)
    }

    @Test fun aRecordingStoppedMidCallSaysSoAndWhetherItIsTriedAgain() {
        val trying = CallNotice.of(idle.copy(callUp = true, recording = true, stopped = CallNotice.Stopped.TRYING))
        assertEquals("Recording stopped", res.getString(trying.titleRes))
        assertEquals("Trying again - this part of the call is not recorded", res.getString(trying.textRes))
        val forGood = CallNotice.of(idle.copy(callUp = true, recording = true, stopped = CallNotice.Stopped.FOR_GOOD))
        assertEquals("Recording stopped", res.getString(forGood.titleRes))
        assertEquals("The rest of this call is not recorded", res.getString(forGood.textRes))
        assertFalse(forGood.deferred)
        // A message's notice keeps its buttons over it: the machine hangs up.
        assertEquals(message, CallNotice.of(idle.copy(messageTicket = "t", recording = true, stopped = CallNotice.Stopped.TRYING)))
        // Not recording at all, nothing stopped.
        assertEquals(call, CallNotice.of(idle.copy(callUp = true, stopped = CallNotice.Stopped.TRYING)))
    }

    @Test fun aMessageOutranksARecordingWhichOutranksNotRecording() {
        assertEquals(message, CallNotice.of(idle.copy(messageTicket = "t", recording = true, notRecording = true)))
        assertEquals(recording, CallNotice.of(idle.copy(recording = true, notRecording = true)))
    }

    @Test fun theChecksAreTheirOwnOnlyWithNothingElseUp() {
        val ups = listOf(idle.copy(callUp = true), idle.copy(recording = true), idle.copy(messageTicket = "t"))
        ups.forEach { up ->
            assertEquals(CallNotice.of(up), CallNotice.of(up.copy(checkingRecorder = true)))
            assertEquals(CallNotice.of(up), CallNotice.of(up.copy(warningOnly = true)))
        }
        // A cable watch's notice comes before the warning's.
        assertEquals(cable, CallNotice.of(idle.copy(checkingRecorder = true, warningOnly = true)))
        // And both before a failed save's "Not recording this call".
        assertEquals(warning, CallNotice.of(idle.copy(warningOnly = true, notRecording = true)))
        assertEquals(cable, CallNotice.of(idle.copy(checkingRecorder = true, notRecording = true)))
    }
}