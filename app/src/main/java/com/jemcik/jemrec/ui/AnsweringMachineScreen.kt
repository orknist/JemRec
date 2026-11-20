package com.jemcik.jemrec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jemcik.jemrec.R
import com.jemcik.jemrec.answering.AnswerDiagnostics
import com.jemcik.jemrec.answering.AnsweringMachine
import com.jemcik.jemrec.answering.EmergencyPause
import com.jemcik.jemrec.answering.Greeting
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which of the answering machine's dialogs is open. Saved by name. */
private enum class AnswerDialog { DELAY, DND, LONGEST, NO_LIMIT, SIM, TEST, CARRIER, RESUME }

/**
 * THE ANSWERING MACHINE: it answers the phone calls you do not take, plays your own
 * greeting to the caller with your microphone muted, and records their
 * message. Off until switched on, and it can be switched on only once the
 * no-call checks pass and a greeting is recorded. The test call underneath is
 * how a phone goes from "possible" to "works".
 */
@Composable
fun AnsweringMachineScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val a by vm.answer.state.collectAsState()
    // A take holding the microphone, this screen's or another window's: the
    // greeting takes no tap - a preview would go into the take - and Record
    // and Delete wait, as for this screen's own take.
    val holder by Greeting.holder.collectAsState()
    val listening = a.takeListening || holder != null
    // A take being made a greeting of, here or in another window: Record
    // would overwrite the file being read, and the saving undo a Delete.
    val making by Greeting.making.collectAsState()
    val processing = a.processingTake || making != null
    // A take made a greeting of past its screen - closed meanwhile - is
    // shown once saved: the screen opened since said "No greeting yet".
    // How a take ended, kept for the screen in front - this one, if it is
    // (readTakeEnding): a window behind another leaves it for that one.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(making) {
        if (making == null) {
            vm.answer.reload()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.answer.readTakeEnding()
        }
    }
    val haptics = LocalHapticFeedback.current
    var dialog by rememberSaveable { mutableStateOf<AnswerDialog?>(null) }

    LaunchedEffect(Unit) { vm.answeringMachineOpened() }
    // Back at the front: where a test kept when its watch ended unanswered,
    // or still under way, stands; and how a take made past its screen ended.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.answer.readPendingTest()
        vm.answer.readTakeEnding()
    }
    val now = LocalNow.current

    when (dialog) {
        AnswerDialog.DELAY -> ChoiceDialog(
            title = stringResource(R.string.am_answer_after),
            intro = stringResource(R.string.am_delay_intro),
            choices = AnsweringMachine.DELAYS,
            selected = a.delay,
            label = { context.getString(R.string.am_seconds, it) },
            onChoose = vm.answer::setDelay,
            onDismiss = { dialog = null },
        )
        AnswerDialog.DND -> ChoiceDialog(
            title = stringResource(R.string.am_dnd),
            intro = stringResource(R.string.am_dnd_intro),
            choices = listOf(true, false),
            selected = a.dndShort,
            label = {
                if (it) {
                    context.getString(R.string.am_dnd_after_2_rings, AnsweringMachine.DND_DELAY)
                } else {
                    context.getString(R.string.am_same_delay)
                }
            },
            onChoose = vm.answer::setDndShort,
            onDismiss = { dialog = null },
        )
        AnswerDialog.LONGEST -> ChoiceDialog(
            title = stringResource(R.string.am_longest_message),
            intro = stringResource(R.string.am_longest_intro),
            choices = AnsweringMachine.LONGEST,
            selected = a.longest,
            label = { longestLabel(context, it) },
            // No limit is asked about first, in a dialog of its own.
            onChoose = {
                if (it == AnsweringMachine.NO_LIMIT) dialog = AnswerDialog.NO_LIMIT else vm.answer.setLongest(it)
            },
            onDismiss = { if (dialog == AnswerDialog.LONGEST) dialog = null },
        )
        AnswerDialog.NO_LIMIT -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.am_no_limit_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.am_no_limit_body,
                        AnsweringMachine.SILENCE_HANGUP_SECONDS,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    vm.answer.setLongest(AnsweringMachine.NO_LIMIT)
                    dialog = null
                }) { Text(stringResource(R.string.am_no_limit)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
        AnswerDialog.SIM -> ChoiceDialog(
            title = stringResource(R.string.am_answer_on),
            choices = a.sims.map { it.first } + AnsweringMachine.SIM_BOTH,
            selected = when (a.sim) {
                AnsweringMachine.SIM_DEFAULT -> a.defaultSim ?: AnsweringMachine.SIM_BOTH
                else -> a.sim
            },
            label = { id ->
                if (id == AnsweringMachine.SIM_BOTH) context.getString(R.string.am_both_sims) else simLabel(context, a, id)
            },
            onChoose = vm.answer::setSim,
            onDismiss = { dialog = null },
        )
        AnswerDialog.TEST -> TestDialog(
            pausedUntil = a.pausedUntil,
            start = { incoming, number -> dialog = null; vm.answer.startTest(incoming, number) },
            dismiss = { dialog = null },
        )
        AnswerDialog.CARRIER -> CarrierDialog(a, vm, dismiss = { dialog = null })
        AnswerDialog.RESUME -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.am_answer_again_title)) },
            text = {
                Text(
                    buildString {
                        append(stringResource(R.string.am_emergency_resume_body))
                        a.pausedUntil?.let {
                            append(stringResource(R.string.am_emergency_pause_ends, pauseEnd(it)))
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    vm.answer.resumeAfterEmergency()
                    dialog = null
                }) { Text(stringResource(R.string.am_answer_again)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.am_keep_paused)) } },
        )
        null -> Unit
    }

    a.askHeard?.let { type ->
        AlertDialog(
            onDismissRequest = vm.answer::dismissHeard,
            title = { Text(stringResource(R.string.am_heard_title)) },
            text = { Text(stringResource(R.string.am_heard_body)) },
            confirmButton = { TextButton(onClick = { haptics.confirm(); vm.answer.heard(type, true) }) { Text(stringResource(R.string.am_yes)) } },
            dismissButton = { TextButton(onClick = { vm.answer.heard(type, false) }) { Text(stringResource(R.string.am_no)) } },
        )
    }

    Column(modifier) {
        SettingsGroup {
            row {
                val blocked = a.blocked(LocalContext.current.resources)
                val paused = a.paused
                SwitchRow(
                    title = stringResource(R.string.settings_answering_machine),
                    summary = when {
                        blocked != null && !a.enabled -> blocked
                        paused != null -> context.getString(R.string.am_summary_paused_until, pauseEnd(paused))
                        a.enabled -> context.getString(R.string.am_summary_enabled, a.delay)
                        else -> context.getString(R.string.am_off)
                    },
                    summaryColor = if (blocked != null && a.stageOne?.possible == false || paused != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    // ANS, as on the Settings row that leads here.
                    mark = headerMark(context, Marked.ANSWERING, marks(state, a.enabled), recorderStatus(state)),
                    checked = a.enabled,
                    enabled = a.enabled || blocked == null,
                    onCheckedChange = vm.answer::setEnabled,
                )
            }
            // A day after a call to an emergency number it stands down, so a
            // callback rings on (EmergencyPause). Ending that early is the
            // user's to choose, warned.
            if (a.paused != null) {
                row {
                    SettingsRow(
                        title = stringResource(R.string.am_answer_again_now),
                        summary = stringResource(R.string.am_stands_down_day),
                        icon = Icons.Filled.Warning,
                        onClick = { dialog = AnswerDialog.RESUME },
                    )
                }
            }
        }

        SettingsGroup(
            stringResource(R.string.am_group_when),
            note = stringResource(R.string.am_when_note),
        ) {
            row {
                SettingsRow(
                    title = stringResource(R.string.am_answer_after),
                    summary = context.getString(R.string.am_seconds, a.delay) + (a.carrierSeconds?.takeIf { it <= a.delay }
                        ?.let { context.getString(R.string.am_voicemail_carrier_first, it) } ?: ""),
                    summaryColor = if (a.carrierSeconds?.let { it <= a.delay } == true) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    icon = SettingsIcons.Timer,
                    onClick = { dialog = AnswerDialog.DELAY },
                )
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.am_dnd),
                    summary = if (a.dndShort) {
                        stringResource(R.string.am_after_2_rings)
                    } else {
                        stringResource(R.string.am_same_delay)
                    },
                    icon = Icons.Filled.Notifications,
                    onClick = { dialog = AnswerDialog.DND },
                )
            }
            if (a.sims.size >= 2) {
                row {
                    SettingsRow(
                        title = stringResource(R.string.am_answer_on),
                        summary = when (a.sim) {
                            AnsweringMachine.SIM_BOTH -> stringResource(R.string.am_both_sims)
                            AnsweringMachine.SIM_DEFAULT -> a.defaultSim?.let { simLabel(context, a, it) }
                                ?: stringResource(R.string.am_both_sims)
                            else -> simLabel(context, a, a.sim)
                        },
                        icon = Icons.Filled.Call,
                        onClick = { dialog = AnswerDialog.SIM },
                    )
                }
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.am_longest_message),
                    summary = if (a.longest == AnsweringMachine.NO_LIMIT) {
                        context.getString(
                            R.string.am_longest_no_limit_summary,
                            AnsweringMachine.SILENCE_HANGUP_SECONDS,
                        )
                    } else {
                        context.getString(R.string.am_longest_then_hangup, seconds(context, a.longest))
                    },
                    icon = SettingsIcons.Timer,
                    onClick = { dialog = AnswerDialog.LONGEST },
                )
            }
        }

        SettingsGroup(stringResource(R.string.am_group_greeting)) {
            row {
                SettingsRow(
                    title = when {
                        a.recordingTake -> stringResource(R.string.am_recording_greeting)
                        a.greetingSeconds != null -> stringResource(R.string.am_your_greeting)
                        else -> stringResource(R.string.am_no_greeting)
                    },
                    summary = when {
                        a.recordingTake -> stringResource(
                            R.string.am_greeting_speak_now,
                            com.jemcik.jemrec.answering.Greeting.MAX_SECONDS,
                        )
                        // Asked to start, or to stop, and not yet answered:
                        // no "Tap to play it" on a row that takes no tap.
                        listening -> stringResource(R.string.am_waiting_recorder)
                        a.greetingProblem != null -> a.greetingProblem
                        a.greetingSeconds != null ->
                            "${seconds(context, (a.greetingSeconds ?: 0.0).toInt().coerceAtLeast(1))}. " +
                                if (a.playing) {
                                    stringResource(R.string.am_playing_tap_stop)
                                } else {
                                    stringResource(R.string.am_tap_to_play)
                                }
                        else -> stringResource(R.string.am_record_before_beep)
                    },
                    // Red only for the problem itself: "Waiting for the
                    // recorder..." over an earlier take's problem showed red.
                    summaryColor = if (a.greetingProblem != null && !a.recordingTake && !listening) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    icon = if (a.recordingTake) SettingsIcons.Mic else Icons.Filled.PlayArrow,
                    // Not while the recorder's microphone may be open for a
                    // take: the greeting would go into it.
                    onClick = if (a.greetingSeconds != null && !listening) vm.answer::togglePreview else null,
                )
            }
        }
        val recordLabel = if (a.greetingSeconds == null) {
            stringResource(R.string.am_record)
        } else {
            stringResource(R.string.am_record_again)
        }
        val labels = when {
            a.recordingTake -> listOf(stringResource(R.string.am_stop))
            a.greetingSeconds != null -> listOf(recordLabel, stringResource(R.string.am_delete))
            else -> listOf(recordLabel)
        }
        ButtonPair(labels, Modifier.padding(top = 8.dp)) { i, modifier ->
            when {
                a.recordingTake -> FilledTonalButton(
                    onClick = { haptics.tap(); vm.answer.stopTake() },
                    modifier = modifier,
                ) { ButtonLabel(stringResource(R.string.am_stop)) }
                a.greetingSeconds != null && i == 1 -> FilledTonalButton(
                    onClick = { haptics.tap(); vm.answer.deleteGreeting() },
                    enabled = a.test == null && !processing && !listening,
                    modifier = modifier,
                ) { ButtonLabel(stringResource(R.string.am_delete)) }
                else -> FilledTonalButton(
                    onClick = { haptics.tap(); vm.answer.startTake() },
                    enabled = state.daemonRunning && !processing && !listening,
                    modifier = modifier,
                ) { ButtonLabel(recordLabel) }
            }
        }
        if (a.recordingTake) TakeTimer(a.takeStartedAt)

        SettingsGroup(stringResource(R.string.am_group_test)) {
            row {
                val stage = a.stageOne
                SettingsRow(
                    title = stringResource(R.string.am_checks_title),
                    summary = when {
                        a.checking -> stringResource(R.string.am_checking)
                        stage == null -> stringResource(R.string.am_checks_not_run)
                        stage.possible -> stringResource(R.string.am_checks_passed)
                        else -> stage.problemText(context.resources)
                    },
                    summaryColor = if (stage?.possible == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    icon = Icons.Filled.Build,
                    onClick = vm.answer::runChecks,
                    enabled = !a.checking && state.daemonRunning,
                )
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.am_test_call),
                    summary = when (a.test) {
                        "arming" -> if (a.testIncoming) {
                            stringResource(R.string.am_getting_recorder_ready)
                        } else {
                            stringResource(R.string.am_calling)
                        }
                        "armed" -> when {
                            // Read late, its window over: not "until" a time
                            // gone by.
                            a.testUntil < now() -> stringResource(R.string.am_asking_recorder)
                            a.testIncoming -> context.getString(
                                R.string.am_waiting_call_until,
                                timeOfDay(a.testUntil),
                            )
                            else -> stringResource(R.string.am_calling)
                        }
                        "running" -> stringResource(R.string.am_testing_greeting)
                        else -> testedSummary(a, context.resources)
                    },
                    icon = Icons.Filled.Call,
                    onClick = if (a.test == null) ({ dialog = AnswerDialog.TEST }) else null,
                    enabled = a.stageOne?.possible == true && a.greetingSeconds != null && state.daemonRunning,
                )
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.am_carrier_vm),
                    summary = when {
                        a.checkingCarrier -> stringResource(R.string.am_asking_carrier)
                        a.carrier == null -> stringResource(R.string.am_tap_ask_carrier_vm)
                        a.carrier?.startsWith("off") == true -> stringResource(R.string.am_carrier_vm_off)
                        a.carrierSeconds != null -> context.getString(
                            R.string.am_carrier_answers_after,
                            a.carrierSeconds,
                        )
                        else -> stringResource(R.string.am_carrier_no_answer)
                    },
                    icon = SettingsIcons.Voicemail,
                    onClick = { dialog = AnswerDialog.CARRIER },
                    enabled = state.daemonRunning,
                )
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_copy_report),
                    summary = stringResource(R.string.settings_copy_report_am),
                    icon = Icons.Filled.Share,
                    onClick = { haptics.tap(); vm.answer.copyReport() },
                )
            }
        }

        SettingsFooter(
            stringResource(R.string.am_footer_how),
            stringResource(R.string.am_footer_limits),
            stringResource(R.string.am_footer_experimental),
        )
    }
}

@Composable
private fun TakeTimer(since: Long) {
    // The app's clock, which the screen tests fix.
    val clock = LocalNow.current
    var now by androidx.compose.runtime.remember { mutableLongStateOf(clock()) }
    LaunchedEffect(since) {
        while (true) {
            now = clock()
            delay(250)
        }
    }
    Text(
        takeSeconds(now - since),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun TestDialog(pausedUntil: Long?, start: (Boolean, String) -> Unit, dismiss: () -> Unit) {
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf("") }
    // Sent as a USSD code, not dialled: no call would come of it, and the
    // next call the user made would be taken as the test.
    val code = number.endsWith("#")
    // Never dialled for a test: the call it made would be an emergency call.
    val emergency = remember(number) { EmergencyPause.isEmergencyNumber(context, number) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.am_test_call)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (pausedUntil == null) {
                        stringResource(R.string.am_test_best_incoming)
                    } else {
                        // Whoever calls in its three minutes is answered: in
                        // the day after an emergency call, that may be them.
                        context.getString(R.string.am_test_emergency_wait, pauseEnd(pausedUntil))
                    },
                )
                Text(
                    stringResource(R.string.am_test_or_auto),
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { v -> number = v.filter { it.isDigit() || it in "+*#" }.take(32) },
                    label = { Text(stringResource(R.string.am_number_label)) },
                    singleLine = true,
                    isError = code || emergency,
                    supportingText = when {
                        code -> {
                            { Text(stringResource(R.string.am_not_a_call)) }
                        }
                        emergency -> {
                            { Text(stringResource(R.string.am_never_emergency)) }
                        }
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { start(true, "") }, enabled = pausedUntil == null) { Text(stringResource(R.string.am_someone_calls)) }
        },
        dismissButton = {
            TextButton(onClick = { start(false, number) }, enabled = number.length >= 3 && !code && !emergency) {
                Text(stringResource(R.string.am_call_number))
            }
        },
    )
}

@Composable
private fun CarrierDialog(a: AnswerUi, vm: MainViewModel, dismiss: () -> Unit) {
    val longer = a.carrierSeconds?.let { it < 30 } == true
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.am_carrier_vm)) },
        text = {
            Text(
                stringResource(R.string.am_carrier_vm_footer) +
                    if (longer) {
                        stringResource(R.string.am_carrier_vm_footer_longer)
                    } else {
                        "."
                    },
            )
        },
        confirmButton = {
            if (longer) {
                TextButton(onClick = { dismiss(); vm.answer.carrierWaitLonger() }) { Text(stringResource(R.string.am_wait_30)) }
            } else {
                TextButton(onClick = { dismiss(); vm.answer.checkCarrier() }) { Text(stringResource(R.string.am_ask_carrier)) }
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.am_close)) } },
    )
}

private fun simLabel(context: android.content.Context, a: AnswerUi, id: String): String =
    a.sims.firstOrNull { it.first == id }?.second ?: context.getString(R.string.am_sim_label)

private fun seconds(context: android.content.Context, s: Int): String = when {
    s < 60 -> context.getString(R.string.am_seconds, s)
    s == 60 -> context.getString(R.string.am_one_minute)
    s % 60 == 0 -> context.getString(R.string.am_n_minutes, s / 60)
    else -> context.getString(R.string.am_seconds, s)
}

private fun longestLabel(context: android.content.Context, s: Int): String =
    if (s == AnsweringMachine.NO_LIMIT) context.getString(R.string.am_no_limit) else seconds(context, s)

/** How long the take has run, in whole seconds. */
internal fun takeSeconds(elapsedMs: Long): String = "%d s".format(Locale.US, (elapsedMs / 1000).coerceAtLeast(0))

/** A time of day, as the screen writes it: in Latin digits in any
 *  language, as the rest of the app's times are (clock) - the phone's own
 *  made Arabic-Indic digits in Arabic or Persian, which the looks' fonts do
 *  not have. */
internal fun timeOfDay(millis: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

/** When a pause ends, with its day: a pause is a day long, so "14:30" alone
 *  would read as today. The month in English, as the words around it are,
 *  and Latin digits (timeOfDay). */
internal fun pauseEnd(millis: Long): String =
    SimpleDateFormat("d MMM 'at' HH:mm", Locale.US).format(Date(millis))

/** The Test call row with no test under way: how the tests came out, and
 *  why an update cleared any (AnswerDiagnostics.Cleared), until a test is
 *  filed again. Tested in AnswerSummaryTest. */
internal fun testedSummary(a: AnswerUi, res: android.content.res.Resources): String {
    val results = AnswerDiagnostics.summary(a.results, res)
    val why = when (a.cleared) {
        AnswerDiagnostics.Cleared.VOLTE -> res.getString(R.string.am_cleared_volte)
        AnswerDiagnostics.Cleared.ROUTES -> res.getString(R.string.am_cleared_routes)
        null -> null
    }
    return when {
        why == null -> results ?: res.getString(R.string.am_tested_none)
        results == null -> why
        else -> "$results. $why"
    }
}

/** The answering machine in a few words, for its row in Settings. Tested in
 *  AnswerSummaryTest. */
internal fun answerSummary(context: android.content.Context, a: AnswerUi): String = when {
    a.paused != null -> context.getString(R.string.am_summary_paused)
    a.enabled -> context.getString(R.string.am_summary_after, a.delay) + when {
        a.cleared != null -> context.getString(R.string.am_summary_test_update)
        a.results.isEmpty() -> context.getString(R.string.am_summary_not_tested)
        else -> ""
    }
    a.stageOne?.possible == false -> context.getString(R.string.am_summary_unavailable)
    // Why it is off, where the user looks: Android's settings guide, a
    // dependent setting with "a brief explanation of why" it is unavailable.
    !a.callTracking -> context.getString(R.string.am_summary_off_tracking)
    else -> context.getString(R.string.am_off)
}

/** The icon the row and the list share. */
internal val messageIcon = SettingsIcons.Voicemail
