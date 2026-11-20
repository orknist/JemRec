package com.jemcik.jemrec.recorder

import android.content.Context
import android.util.Log
import com.jemcik.jemrec.R
import com.jemcik.jemrec.answering.AnsweringMachine
import com.jemcik.jemrec.answering.EmergencyPause
import com.jemcik.jemrec.audio.AudioStream
import com.jemcik.jemrec.calls.CallLogLookup
import com.jemcik.jemrec.calls.CallTracking
import com.jemcik.jemrec.calls.VoipApps
import com.jemcik.jemrec.calls.VoipCallNotes
import com.jemcik.jemrec.capture.CaptureDaemon
import com.jemcik.jemrec.capture.DebugNotificationCleaner
import com.jemcik.jemrec.system.ShellGrant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.jemcik.jemrec.rethrowingCancellation
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Does this phone record calls, right now?
 *
 * WHAT IT REPLACED, AND WHY THAT HAD TO GO
 *
 * The old self-test was the transport's acceptance check: it ran `id` over an
 * ADB shell session and passed only on uid=2000(shell). That was the right
 * question while the transport was the thing being proven, and it stopped being
 * the right question the moment ADB became a bootstrap that closes itself. The
 * app now shuts the ADB session as soon as the recorder is up, so the healthy
 * steady state has no session at all - and the check reported FAIL, "not
 * connected - pair and connect first", directly beneath a panel saying
 * "Recorder running. No ADB session. Normal."
 *
 * A diagnostic that reads FAIL when everything is fine is worse than none. It
 * teaches you to ignore it, and then it is there for the day it matters.
 *
 * WHAT IT CHECKS NOW
 *
 * The chain a call actually travels: the recorder answers, it can open a
 * capture, and audio comes out of it. Over loopback, so it needs no ADB, no
 * Wi-Fi and no permission - the same conditions a real call is recorded under.
 *
 * AND THE LINK THAT CHAIN LEFT OUT: NOTICING THE CALL
 *
 * A diagnostic that reads PASS when calls are being lost is worse still, and
 * this one did. A call is recorded only if the daemon's call watch hears it
 * start, and nothing above asks whether it does. A user's phone showed the gap:
 * "PASS - a call would be recorded" while every call after the first went
 * unrecorded, because the daemon's watch had been garbage-collected (see
 * CallWatch.callWatch). So the daemon is now asked what its watch has heard, and
 * that is held against the phone's own call log - a call the log has and the
 * watch never heard is a FAIL, whatever the capture says. The log needs
 * READ_CALL_LOG, which is optional; without it everything else still runs and
 * the report says what it could not check.
 *
 * It writes no file. The stream is read and dropped, so there is nothing to
 * clean up afterwards and no stray recording to explain.
 */
object SelfTest {

    private const val TAG = "JemRec"

    /** Enough to prove audio is flowing, short enough not to feel like a wait. */
    private const val PACKETS_WANTED = 5
    private const val TIMEOUT_MS = 6_000L

    /** A recording with no call up may be one whose call ended a moment ago:
     *  the daemon takes about two seconds to confirm an end. Asked again after
     *  this long before it counts as stuck. */
    private const val STUCK_RECHECK_MS = 4_000L

    /**
     * Jitter only. The log dates a call when telecom creates it, and the watch
     * can only hear of it after that - it rings or dials once it exists - so a
     * heard call always has an event at or after its log date. This was ten
     * seconds, and that was a blind spot, not a margin: a call placed nine
     * seconds after the last one ended passed as heard on a deaf watch
     * (measured on the emulator, the two calls back to back).
     */
    internal const val HEARD_SLACK_MS = 2_000L

    data class Result(val passed: Boolean, val report: String)

    suspend fun run(context: Context): Result = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        val restart = context.getString(R.string.selftest_restart_wifi)

        val answer = CaptureDaemon.answer(context)
            ?: return@withContext Result(
                passed = false,
                report = buildString {
                    appendLine(context.getString(R.string.selftest_not_answering))
                    appendLine()
                    appendLine(context.getString(R.string.selftest_nothing_listening, CaptureDaemon.PORT))
                    appendLine(context.getString(R.string.selftest_connect_wifi))
                },
            )
        lines += context.getString(R.string.selftest_line_recorder, CaptureDaemon.PORT)
        val current = CaptureDaemon.isCurrent(context, answer)
        if (!current) lines += context.getString(R.string.selftest_line_build, answer.build)

        val errNoConfig = context.getString(R.string.selftest_err_no_config)
        val errNoAudio = context.getString(R.string.selftest_err_no_audio)

        val outcome = timed(TIMEOUT_MS) {
            AudioStream(CaptureDaemon.connect(context)).use { stream ->
                // The first packet carries the OpusHead. Without it there
                // is no container to write, so a capture that cannot
                // produce one cannot record a call either.
                val config = stream.readPacket()
                require(config != null && config.isConfig) { errNoConfig }
                lines += context.getString(R.string.selftest_line_codec, stream.codecId)
                lines += context.getString(R.string.selftest_line_config, config.data.size)

                var packets = 0
                var bytes = 0
                while (packets < PACKETS_WANTED) {
                    val packet = stream.readPacket() ?: break
                    if (packet.isConfig) continue
                    packets++
                    bytes += packet.data.size
                }
                require(packets > 0) { errNoAudio }
                lines += context.getString(R.string.selftest_line_audio, packets, bytes)
            }
        }

        when {
            // Null means the timeout won: the capture opened but audio did
            // not come, or did not come fast enough. During a real call that is
            // expected - the recording has the voice-call source, and a
            // diagnostic must not be allowed to disturb it.
            outcome == null -> return@withContext Result(
                passed = false,
                report = buildString {
                    appendLine(context.getString(R.string.selftest_fail_timeout))
                    appendLine()
                    lines.forEach { appendLine(it) }
                    appendLine()
                    appendLine(context.getString(R.string.selftest_expected_during_call_1))
                    appendLine(context.getString(R.string.selftest_expected_during_call_2))
                },
            )

            outcome.isFailure -> {
                Log.w(TAG, "self-test failed", outcome.exceptionOrNull())
                return@withContext Result(
                    passed = false,
                    report = buildString {
                        appendLine(
                            context.getString(
                                R.string.selftest_fail_with_reason,
                                outcome.exceptionOrNull()?.message,
                            ),
                        )
                        appendLine()
                        lines.forEach { appendLine(it) }
                    },
                )
            }
        }

        // The capture works. Whether anything will ever start it is the
        // daemon's call watch, asked about next.
        val problem = watchProblem(context, current, lines, restart)
        Result(
            passed = problem == null,
            report = buildString {
                appendLine(
                    if (problem != null) {
                        context.getString(R.string.selftest_fail_with_reason, problem)
                    } else {
                        context.getString(
                            R.string.selftest_pass_with_verdict,
                            verdict(context, CaptureDaemon.currentMode(context)),
                        )
                    },
                )
                appendLine()
                lines.forEach { appendLine(it) }
            },
        )
    }

    /**
     * What is wrong with the daemon's call watch, or null if nothing is.
     * Adds what it learns to the report on the way.
     */
    private suspend fun watchProblem(
        context: Context,
        current: Boolean,
        lines: MutableList<String>,
        restart: String,
    ): String? {
        val watch = CaptureDaemon.watch(context)
        if (watch == null) {
            if (current) return context.getString(R.string.selftest_problem_no_hears)
            // Every build too old to give this report is one whose watch could
            // be garbage-collected: it may be deaf right now, and nothing short
            // of a call can tell. A revive replaces it, so one is asked for now
            // rather than at the next keep-alive tick, up to fifteen minutes on.
            RecorderLifecycle.arm(context, Trigger.SELF_TEST)
            return context.getString(R.string.selftest_problem_old_build)
        }
        lines += context.getString(R.string.selftest_line_watching, at(watch.since))
        if (!watch.watching) return context.getString(R.string.selftest_problem_not_watching, restart)
        if (!watch.looperOk) return context.getString(R.string.selftest_problem_looper, restart)
        lines += context.getString(R.string.selftest_line_heard, calls(context, watch.offHooks)) +
            if (watch.lastOffHook > 0) context.getString(R.string.selftest_line_heard_last, at(watch.lastOffHook)) else ""

        // A daemon that missed a push records by the wrong rule - or, since it
        // fails closed, not at all - and still passes everything above. One
        // round trip puts it right.
        val wanted = CaptureDaemon.currentMode(context)
        when {
            watch.mode == wanted -> lines += context.getString(R.string.selftest_line_mode, modeName(context, wanted))
            CaptureDaemon.sendMode(context) ->
                lines += context.getString(
                    R.string.selftest_line_mode_corrected,
                    modeName(context, wanted),
                    modeName(context, watch.mode),
                )
            else -> return context.getString(
                R.string.selftest_problem_wrong_mode,
                modeName(context, watch.mode),
                modeName(context, wanted),
            )
        }

        // A daemon that believes a call is up when none is has lost the end of
        // one - unless it ended a moment ago and the daemon is still confirming
        // it, which takes about two seconds. So it is asked twice.
        if ((watch.inCall || watch.recordingSince > 0) && !CaptureDaemon.callInProgress(context)) {
            delay(STUCK_RECHECK_MS)
            val again = CaptureDaemon.watch(context)
            if (again != null && (again.inCall || again.recordingSince > 0) &&
                !CaptureDaemon.callInProgress(context)
            ) {
                return if (again.recordingSince > 0) {
                    context.getString(R.string.selftest_problem_stuck_recording, at(again.recordingSince), restart)
                } else {
                    context.getString(R.string.selftest_problem_phantom_call, restart)
                }
            }
        }

        messengerCalls(context, watch, lines, restart)?.let { return it }
        callTracking(context, watch, lines, restart)?.let { return it }
        answeringMachine(context, watch, lines, restart)?.let { return it }

        when {
            !CallLogLookup.granted(context) ->
                lines += context.getString(R.string.selftest_line_missed_no_access)
            CaptureDaemon.callInProgress(context) ->
                lines += context.getString(R.string.selftest_line_missed_during_call)
            else -> {
                val starts = runCatching { CallLogLookup.callStartsSince(context, watch.since) }
                    .onFailure { Log.w(TAG, "self-test: could not read the call log", it) }
                    .getOrNull()
                if (starts == null) {
                    lines += context.getString(R.string.selftest_line_missed_unreadable)
                } else {
                    unheardCall(starts, watch.lastEvent)?.let {
                        return context.getString(R.string.selftest_problem_unheard, at(it), restart)
                    }
                    lines += context.getString(R.string.selftest_line_missed_none)
                }
            }
        }
        return null
    }

    /**
     * Messenger calls (VoipApps): switched on here, armed in the daemon? A
     * daemon that was never told, or whose audio policy Android refused,
     * records no messenger call and says nothing about it - the one failure
     * of messenger calls that would otherwise be silent. One push puts a
     * daemon that missed the list right, as for the mode.
     */
    private suspend fun messengerCalls(
        context: Context,
        watch: CaptureDaemon.Watch,
        lines: MutableList<String>,
        restart: String,
    ): String? {
        if (!VoipApps.supported(context)) {
            lines += context.getString(R.string.selftest_line_messenger_impossible)
            return null
        }
        val enabled = VoipApps.enabled(context)
        if (enabled.isEmpty()) {
            lines += context.getString(R.string.selftest_line_messenger_none)
            return null
        }
        val labels = messengerNames(enabled)
        if (watch.voipApps == null) {
            return context.getString(R.string.selftest_problem_messenger_old, restart)
        }
        if (watch.voipApps != enabled.size || !watch.voipArmed) {
            val fixed = CaptureDaemon.setVoipApps(context) &&
                CaptureDaemon.watch(context)?.let { it.voipApps == enabled.size && it.voipArmed } == true
            if (!fixed) {
                return context.getString(R.string.selftest_problem_messenger_not_ready, labels, restart)
            }
            lines += context.getString(R.string.selftest_line_messenger_armed_corrected, labels)
        } else {
            lines += context.getString(R.string.selftest_line_messenger_armed, labels)
        }
        if (VoipCallNotes.enabled(context)) {
            if (!DebugNotificationCleaner.accessGranted(context)) {
                return context.getString(R.string.selftest_problem_show_who_no_access)
            }
            lines += context.getString(R.string.selftest_line_names)
        }
        return null
    }

    /**
     * Exact call tracking (CallTracking): on here, allowed by the daemon, and
     * in force for this app? Off is simply said; a daemon that has not allowed
     * it is told again.
     */
    private suspend fun callTracking(
        context: Context,
        watch: CaptureDaemon.Watch,
        lines: MutableList<String>,
        restart: String,
    ): String? {
        if (!CallTracking.enabled(context)) {
            lines += context.getString(R.string.selftest_line_tracking_off)
            return null
        }
        if (watch.tracking == null) {
            lines += context.getString(R.string.selftest_line_tracking_not_yet)
            return null
        }
        val corrected = !watch.tracking && CaptureDaemon.setTracking(context)
        if (!watch.tracking && !corrected) {
            return context.getString(R.string.selftest_problem_tracking_daemon, trackingFix(context, restart))
        }
        val inForce = runCatching {
            context.getSystemService(android.app.AppOpsManager::class.java).unsafeCheckOpNoThrow(
                "android:manage_ongoing_calls", android.os.Process.myUid(), context.packageName,
            ) == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        if (!inForce) {
            return context.getString(R.string.selftest_problem_tracking_appops, trackingFix(context, restart))
        }
        lines += context.getString(R.string.selftest_line_tracking_on) +
            if (corrected) context.getString(R.string.selftest_suffix_corrected) else ""
        return null
    }

    /**
     * What to do about tracking the recorder could not allow. On an OPPO,
     * OnePlus or realme phone with its switch off the shell may not change an
     * app-op (ShellGrant; CallVault measured `appops set` refused there), so a
     * restart alone would only be refused again.
     */
    private fun trackingFix(context: Context, restart: String): String =
        if (ShellGrant.switchMayBeOff()) {
            context.getString(R.string.selftest_tracking_fix_switch, ShellGrant.switchText(context), restart)
        } else {
            restart
        }

    /**
     * The answering machine: switched on here, and does the recorder know it,
     * and hold the greeting? A recorder that does not is given both again.
     */
    private suspend fun answeringMachine(
        context: Context,
        watch: CaptureDaemon.Watch,
        lines: MutableList<String>,
        restart: String,
    ): String? {
        if (!AnsweringMachine.enabled(context)) {
            lines += context.getString(R.string.selftest_line_answering_off)
            return null
        }
        if (watch.answer == null) {
            lines += context.getString(R.string.selftest_line_answering_not_yet)
            return null
        }
        var ok = watch.answer && watch.greeting == true
        if (!ok) {
            CaptureDaemon.syncGreeting(context)
            CaptureDaemon.setAnswering(context)
            ok = CaptureDaemon.watch(context)?.let { it.answer == true && it.greeting == true } == true
            if (!ok) return context.getString(R.string.selftest_problem_answering, restart)
        }
        lines += context.getString(R.string.selftest_line_answering_on, AnsweringMachine.delay(context)) +
            (EmergencyPause.pausedUntil(context)?.let {
                context.getString(R.string.selftest_line_answering_paused, at(it))
            } ?: "")
        return null
    }

    /**
     * The latest call the log has that the watch did not hear, or null.
     *
     * Every call the log is asked for (see CallLogLookup.callStartsSince) went
     * off-hook here, which reaches the watch as an event at or after the moment
     * the log dates the call. So a watch whose last event is older than the
     * latest call's start, by more than a little slack, never heard it. Only the
     * latest needs asking about: the watch hears in order, so an event after it
     * covers every call before it.
     */
    internal fun unheardCall(callStarts: List<Long>, lastEvent: Long): Long? {
        val latest = callStarts.maxOrNull() ?: return null
        return latest.takeIf { lastEvent < it - HEARD_SLACK_MS }
    }

    private fun verdict(context: Context, mode: Int): String = when (mode) {
        CaptureDaemon.MODE_AUTOMATIC -> context.getString(R.string.selftest_verdict_would_record)
        CaptureDaemon.MODE_ON_DEMAND -> context.getString(R.string.selftest_verdict_ask_first)
        else -> context.getString(R.string.selftest_verdict_recording_off)
    }

    /** In the app's own words for the modes - see MainScreen. */
    private fun modeName(context: Context, mode: Int): String = when (mode) {
        CaptureDaemon.MODE_AUTOMATIC -> context.getString(R.string.selftest_mode_every_call)
        CaptureDaemon.MODE_ON_DEMAND -> context.getString(R.string.selftest_mode_asks_first)
        else -> context.getString(R.string.selftest_mode_off)
    }

    private fun calls(context: Context, n: Int): String =
        if (n == 1) context.getString(R.string.selftest_calls_one)
        else context.getString(R.string.selftest_calls_many, n)

    /** "14:05" today, "3 Oct 14:05" before that. */
    private fun at(millis: Long): String {
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val now = Calendar.getInstance()
        val today = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        return SimpleDateFormat(if (today) "HH:mm" else "d MMM HH:mm", Locale.US).format(Date(millis))
    }
}

/**
 * [block]'s outcome, or null if [timeoutMs] ran out first. The time running
 * out is a cancellation, and was caught with the block's own failures: the
 * self-test said "FAIL - Timed out waiting for 6000 ms" where its own words
 * for a slow recorder were meant (read from the code, 2026-11-14).
 */
internal suspend fun <T> timed(timeoutMs: Long, block: suspend () -> T): Result<T>? =
    withTimeoutOrNull(timeoutMs) { runCatching { block() }.rethrowingCancellation() }

/** The messengers [enabled] (packages), as Settings names them: two
 *  Telegrams are two (issue #93), where their label said "Telegram" twice. */
internal fun messengerNames(enabled: Set<String>): String =
    enabled.mapNotNull { VoipApps.byPackage(it)?.settingsName }.joinToString(", ")