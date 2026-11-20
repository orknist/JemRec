package com.jemcik.jemrec.capture

import androidx.annotation.StringRes
import com.jemcik.jemrec.R

/**
 * What the call service's own notification says (CallMonitorService), from
 * what the service knows at that moment. Pure — words are resource ids, resolved
 * in the service with [android.content.Context.getString].
 */
internal object CallNotice {

    data class State(
        /** The recorder's wakes say a call is up. */
        val callUp: Boolean,
        /** The recorder is recording it. */
        val recording: Boolean,
        /** A save failed: the user is told why, by tapping. */
        val notRecording: Boolean,
        /** The recording stopped mid-call, and whether it is tried again. */
        val stopped: Stopped? = null,
        /** The ticket of the message the answering machine is taking, if one is. */
        val messageTicket: String?,
        /** That message is a test call of the answering machine. */
        val testing: Boolean,
        /** Watching the recorder after a USB cable went in or came out. */
        val checkingRecorder: Boolean,
        /** Up only to post the background-kill warning. */
        val warningOnly: Boolean,
    )

    data class Notice(
        @StringRes val titleRes: Int,
        @StringRes val textRes: Int,
        /** Shown after Android's ten seconds, if still up, rather than at once. */
        val deferred: Boolean,
        /** The ticket Talk to them and Hang up carry; no buttons without one. */
        val buttonTicket: String?,
    )

    enum class Stopped {
        /** Being tried again: the recorder's capture, failed too often,
         *  every 10 s (RestartPolicy), or stalled - nothing reached the
         *  recording for 5 s, no read failing (StallWatch) - or the recorder
         *  itself, found gone mid-call, every 30 s (RecorderWatch). */
        TRYING,

        /** The encoder or its file ended: nothing more of this call. */
        FOR_GOOD,
    }

    fun of(s: State): Notice {
        val ticket = s.messageTicket
        // Only the cable watch, no call: a check that ends in seconds when the
        // cable changed nothing, and so the one notification here that waits.
        val cableOnly = s.checkingRecorder && !s.callUp && !s.recording && ticket == null
        // The background-kill warning's wake, no call and no cable watch: over
        // as soon as the warning is posted, so it waits, and is never seen.
        val warningWake = s.warningOnly && !s.checkingRecorder && !s.callUp && !s.recording && ticket == null
        // This service only exists during a call, so the notification only
        // says what it is doing about THIS call. "Recording call" while it
        // records; "Call in progress" in the gap before recording starts, or
        // while the mid-call prompt waits for an answer in on-demand mode.
        val (titleRes, textRes) = when {
            warningWake -> R.string.notice_checking_jemrec to R.string.notice_keeps_running
            cableOnly -> R.string.notice_checking_recorder to R.string.notice_usb_cable
            ticket != null && s.testing -> R.string.notice_testing_am to R.string.notice_testing_am_body
            ticket != null -> R.string.notice_taking_message to R.string.notice_taking_message_body
            s.recording && s.stopped == Stopped.TRYING ->
                R.string.notice_rec_stopped to R.string.notice_trying_again
            s.recording && s.stopped == Stopped.FOR_GOOD ->
                R.string.notice_rec_stopped to R.string.notice_not_recorded_rest
            s.recording -> R.string.notice_recording_call to R.string.notice_saving
            s.notRecording -> R.string.notice_not_recording to R.string.notice_tap_why
            else -> R.string.notice_call_progress to R.string.notice_handling
        }
        return Notice(titleRes, textRes, deferred = cableOnly || warningWake, buttonTicket = ticket)
    }
}