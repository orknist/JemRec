package com.jemcik.jemrec.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jemcik.jemrec.R
import com.jemcik.jemrec.calls.CallRules
import com.jemcik.jemrec.playback.PlayThrough
import com.jemcik.jemrec.recordings.FileNames
import com.jemcik.jemrec.recordings.RecordingQuality
import com.jemcik.jemrec.recordings.Retention
import com.jemcik.jemrec.recordings.Trash
import com.jemcik.jemrec.setup.Setup
import com.jemcik.jemrec.system.AutoStart
import com.jemcik.jemrec.ui.theme.JemRecColors
import com.jemcik.jemrec.ui.theme.LocalLookChoice
import com.jemcik.jemrec.ui.theme.lookMono

/** Which of Settings' dialogs is open. Saved by name, so a rotation keeps it. */
private enum class SettingsDialog { FOLDER, FILE_NAMES, QUALITY, OLD, SHORT, TRACKING_OFF, PLAY_THROUGH }

/**
 * The things that are not the answer to "would a call be recorded right now".
 *
 * ONE SCREEN THAT CAN BE READ AT A GLANCE. It was fourteen cards, each a
 * paragraph with its control somewhere inside, about eight screens of
 * scrolling with the current values buried in the prose. Now it is Android's
 * settings list (source.android.com/docs/core/settings/settings-guidelines):
 * four groups, every row its name and what it is set to, a switch where on and
 * off is the whole question, and anything longer one tap away - a dialog for a
 * choice, a screen of its own (Whose calls to keep, Messenger calls,
 * Diagnostics) where the explanation is worth keeping, in a footer there.
 *
 * Configuration only. Deleting recordings briefly lived here and should not
 * have: settings are where you change how the app behaves, not where you act
 * on its contents. Recently deleted is reached from here, beside the
 * auto-delete rules that fill it, because it is somewhere you go now and then
 * to take something back - not part of the list you use every day.
 */
@Composable
fun SettingsScreen(
    state: UiState,
    vm: MainViewModel,
    askForCallLog: () -> Unit,
    pickFolder: (android.net.Uri?) -> Unit,
    modifier: Modifier = Modifier,
) {

    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    // Read with the rest of the state, on IO (Reading): both mean resolving
    // a dozen activities through the package manager.
    val autoStart = state.autoStartLabel
    // The background row brought into view for the "stopped in the
    // background" warning's tap, which opens Settings: at its top it showed
    // nothing of it (review round 9).
    val background = remember { BringIntoViewRequester() }
    // Once its row is drawn - only on a phone with such a screen, its name
    // read on IO, which may land after the tap - and laid out: asked before
    // the first layout, the request does nothing (review round 10; the
    // narrowing row waits a frame the same way).
    androidx.compose.runtime.LaunchedEffect(state.revealBackground, autoStart) {
        if (state.revealBackground && autoStart != null) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { background.bringIntoView() }
            vm.backgroundRevealed()
        }
    }
    // Left before then - no such screen on this phone: not revealed the
    // next time Settings opens.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.backgroundRevealed() } }


    if (state.confirmingReset) {
        AlertDialog(
            onDismissRequest = vm::cancelReset,
            title = { Text(stringResource(R.string.settings_start_fresh_title)) },
            text = {
                Text(startFreshWords(context, handsBack = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU))
            },
            confirmButton = {
                TextButton(onClick = { haptics.confirm(); vm.confirmReset() }) {
                    Text(stringResource(R.string.settings_start_fresh))
                }
            },
            dismissButton = { TextButton(onClick = vm::cancelReset) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }

    when (dialog) {
        SettingsDialog.FOLDER -> FolderDialog(
            state,
            change = { dialog = null; pickFolder(vm.folderPickerStart()) },
            useDefault = { dialog = null; vm.useDefaultFolder() },
            dismiss = { dialog = null },
        )
        SettingsDialog.FILE_NAMES -> FileNamesDialog(
            state, vm,
            allow = askForCallLog,
            chooseHonorFolder = { pickFolder(FileNames.honorFolderUri) },
            dismiss = { dialog = null },
        )
        SettingsDialog.QUALITY -> ChoiceDialog(
            title = stringResource(R.string.settings_quality),
            intro = stringResource(R.string.settings_quality_intro),
            choices = RecordingQuality.entries,
            selected = state.quality,
            label = { qualityLabel(context, it) },
            detail = { qualityDetail(context, it) },
            detailIsExample = false,
            onChoose = vm::setQuality,
            onDismiss = { dialog = null },
        )
        SettingsDialog.OLD -> ChoiceDialog(
            title = stringResource(R.string.settings_auto_delete_old),
            intro = keptAndTrashIntro(),
            choices = Retention.AGE_CHOICES,
            selected = state.autoDeleteAfterDays,
            label = { ageChoice(context, it) },

            onChoose = { vm.setAutoDelete(afterDays = it) },
            onDismiss = { dialog = null },
            enabled = !state.busy,
        )
        SettingsDialog.SHORT -> ChoiceDialog(
            title = stringResource(R.string.settings_auto_delete_short),
            intro = keptAndTrashIntro(),
            choices = Retention.SHORT_CHOICES,
            selected = state.autoDeleteShorterThan,
            label = {
                if (it == 0) context.getString(R.string.am_off)
                else context.getString(R.string.settings_shorter_than_seconds, it)
            },
            onChoose = { vm.setAutoDelete(shorterThan = it) },
            onDismiss = { dialog = null },
            enabled = !state.busy,
        )
        SettingsDialog.PLAY_THROUGH -> ChoiceDialog(
            title = stringResource(R.string.settings_play_recordings),
            intro = stringResource(R.string.settings_play_intro),
            choices = PlayThrough.entries.filter { it != PlayThrough.LOUDSPEAKER_AND_EAR || state.raiseAvailable },
            selected = state.playThrough.takeIf { it != PlayThrough.LOUDSPEAKER_AND_EAR || state.raiseAvailable }
                ?: PlayThrough.LOUDSPEAKER,
            label = { playThroughLabel(context, it, state.raiseAvailable) },
            detail = { playThroughDetail(context, it) },
            detailIsExample = false,
            onChoose = vm::setPlayThrough,
            onDismiss = { dialog = null },
        )
        // Exact call tracking switched off with the answering machine on: the
        // machine goes off with it, and did without a word (the user found it,
        // 2026-10-24). Asked first - Material's dialog "to provide
        // confirmation of a choice before committing to it" - with the
        // question itself as the headline and a confirm named for what it
        // does. Cancel leaves both on.
        SettingsDialog.TRACKING_OFF -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_tracking_off_title)) },
            text = {
                Text(stringResource(R.string.settings_tracking_off_body))
            },
            confirmButton = {
                TextButton(onClick = { haptics.confirm(); dialog = null; vm.setCallTracking(false) }) { Text(stringResource(R.string.settings_turn_both_off)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
        null -> Unit
    }

    Column(modifier) {

        // Only while it is worth offering. Once granted there is nothing to
        // do here, and the recordings list shows the result better than a
        // settings row saying "on" ever could.
        if (!state.canReadCallLog) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.settings_show_who_called), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_show_who_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(
                        onClick = { haptics.tap(); askForCallLog() },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.settings_allow)) }
                }
            }
        }

        // The four settings the header marks carry its mark here, where their
        // icon would be: one function decides both (headerMark).
        val answer by vm.answer.state.collectAsState()
        val headerFacts = marks(state, answer.enabled)
        val status = recorderStatus(state)
        SettingsGroup(stringResource(R.string.recording)) {
            row {
                SwitchRow(
                    title = stringResource(R.string.settings_record_every_call),
                    summary = if (state.automatic) {
                        stringResource(R.string.settings_every_call_without_asking)
                    } else {
                        stringResource(R.string.settings_asks_notification)
                    },
                    mark = headerMark(context, Marked.MODE, headerFacts, status),
                    checked = state.automatic,
                    onCheckedChange = vm::setAutomatic,
                )
            }
            row {
                val rules = rulesSummary(context, state)
                SettingsRow(
                    title = stringResource(R.string.settings_whose_calls),
                    summary = rules.text,
                    summaryColor = if (rules.nothing) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    icon = Icons.Filled.Person,
                    onClick = { vm.showScreen(Screen.WHOSE_CALLS) },
                )
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_answering_machine),
                    summary = answerSummary(context, answer),
                    mark = headerMark(context, Marked.ANSWERING, headerFacts, status),
                    onClick = { vm.showScreen(Screen.ANSWERING_MACHINE) },
                )
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_messenger_calls),
                    summary = messengerSummary(context, state),
                    mark = headerMark(context, Marked.MESSENGERS, headerFacts, status),
                    onClick = { vm.showScreen(Screen.MESSENGER_CALLS) },
                )
            }
            // Telecom's own account of each call. Its switch is on unless turned
            // off, and off is exactly how JemRec worked before it. See CallTracking.
            row {
                SwitchRow(
                    title = stringResource(R.string.settings_exact_call_tracking),
                    summary = if (state.callTracking) {
                        stringResource(R.string.settings_tracking_on_summary)
                    } else {
                        stringResource(R.string.settings_tracking_off_summary)
                    },
                    icon = Icons.Filled.Phone,
                    checked = state.callTracking,
                    // Off with the answering machine on turns that off too:
                    // asked first (SettingsDialog.TRACKING_OFF).
                    onCheckedChange = { on ->
                        if (!on && vm.trackingOffTakesTheMachine()) dialog = SettingsDialog.TRACKING_OFF else vm.setCallTracking(on)
                    },
                )
            }
        }

        SettingsGroup(stringResource(R.string.look)) {
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_theme),
                    summary = LocalLookChoice.current.let {
                        "${stringResource(lookNameRes(it.look))} · ${stringResource(lightDarkSummaryRes(it.lightDark))}"
                    },
                    icon = SettingsIcons.Palette,
                    onClick = { vm.showScreen(Screen.THEME) },
                )
            }
        }

        // Where recordings play (AtTheEar): only on a phone with an earpiece -
        // a tablet has none - and the earpiece at the ear only with a
        // proximity sensor to know when it is there. Both sides in both ears
        // everywhere: headphones are everywhere.
        SettingsGroup(stringResource(R.string.playing)) {
            if (state.earpieceAvailable) {
                row {
                    SettingsRow(
                        title = stringResource(R.string.settings_play_recordings),
                        summary = playThroughLabel(context, state.playThrough, state.raiseAvailable),
                        icon = EarpieceIcons.Ear,
                        onClick = { dialog = SettingsDialog.PLAY_THROUGH },
                    )
                }
            }
            row {
                SwitchRow(
                    title = stringResource(R.string.settings_both_ears),
                    summary = if (state.bothEars) {
                        stringResource(R.string.settings_both_ears_on)
                    } else {
                        stringResource(R.string.settings_both_ears_off)
                    },
                    icon = SettingsIcons.Headset,
                    checked = state.bothEars,
                    onCheckedChange = vm::setBothEars,
                )
            }
        }

        SettingsGroup(stringResource(R.string.saving)) {
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_saving_to),
                    summary = state.folder,
                    icon = SettingsIcons.Folder,
                    onClick = { dialog = SettingsDialog.FOLDER },
                )
            }
            row {
                val problem = fileNamesProblem(context, state)
                SettingsRow(
                    title = stringResource(R.string.settings_file_names),
                    summary = problem ?: fileNamesLabel(context, state.fileNames),
                    summaryColor = if (problem != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    icon = Icons.Filled.Edit,
                    onClick = { dialog = SettingsDialog.FILE_NAMES },
                )
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_quality),
                    summary = context.getString(
                        R.string.settings_quality_row,
                        qualityLabel(context, state.quality),
                        state.quality.mbPerHour,
                    ),
                    icon = SettingsIcons.GraphicEq,
                    onClick = { dialog = SettingsDialog.QUALITY },
                )
            }
            // Both rules off until chosen. Nothing they remove is gone: it waits
            // in Recently deleted, said in their dialogs so nobody picks a rule
            // fearing it.
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_auto_delete_old),
                    summary = if (state.autoDeleteAfterDays == 0) {
                        stringResource(R.string.settings_never)
                    } else {
                        stringResource(R.string.settings_older_than, ageChoice(context, state.autoDeleteAfterDays))
                    },
                    mark = headerMark(context, Marked.AUTO_DELETE, headerFacts, status),
                    onClick = { dialog = SettingsDialog.OLD },
                )
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_auto_delete_short),
                    summary = if (state.autoDeleteShorterThan == 0) {
                        stringResource(R.string.am_off)
                    } else {
                        stringResource(R.string.settings_shorter_than_seconds, state.autoDeleteShorterThan)
                    },
                    icon = SettingsIcons.Timer,
                    onClick = { dialog = SettingsDialog.SHORT },
                )
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.screen_trash),
                    summary = when (val count = state.trashed.size) {
                        0 -> stringResource(R.string.settings_empty)
                        1 -> stringResource(R.string.settings_one_recording_restorable, Trash.DAYS)
                        else -> stringResource(R.string.settings_n_recordings_restorable, count, Trash.DAYS)
                    },
                    icon = Icons.Filled.Delete,
                    onClick = vm::showTrash,
                )
            }
        }

        SettingsGroup(stringResource(R.string.settings_group_notifications)) {
            // The notification that follows each call. Its switch is only about
            // the everyday one: a recording that came out silent is reported
            // either way, because that is the news nobody should miss.
            row {
                SwitchRow(
                    title = stringResource(R.string.settings_show_each_call),
                    summary = if (state.afterCallNotice) {
                        stringResource(R.string.settings_after_call_on)
                    } else {
                        stringResource(R.string.settings_after_call_silent_only)
                    },
                    icon = Icons.Filled.Notifications,
                    checked = state.afterCallNotice,
                    onCheckedChange = vm::setAfterCallNotice,
                )
            }
            // The tile is the same switch as the one in the header. Android 13
            // and later can add it for the user; before that, all this row can
            // do is say where it is. Once it is in the panel the row says so -
            // it used to go on offering to add it, and a tap then met nothing,
            // because Android answers a tile already added without a word. The
            // row stays tappable: if the tile was taken out since, Android asks
            // again; if not, its answer is said back.
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_quick_settings_tile),
                    summary = when {
                        state.tileAdded -> stringResource(R.string.settings_tile_added)
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                            stringResource(R.string.settings_tile_add_tiramisu)
                        else -> stringResource(R.string.settings_tile_legacy)
                    },
                    icon = SettingsIcons.Tile,
                    onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        { addTile(context, vm::onTileAddAnswer) }
                    } else {
                        null
                    },
                )
            }
        }

        // Debugging, and what JemRec does with it: Android's reminder that it is
        // on, and what else goes off when JemRec does (issue #37).
        SettingsGroup(stringResource(R.string.settings_group_debugging)) {
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_when_off),
                    summary = offModeTitle(context, state.offMode),
                    icon = SettingsIcons.Power,
                    onClick = { vm.showScreen(Screen.WHEN_OFF) },
                )
            }
            // StartWithoutWifi. What it costs is in the summary whichever way
            // it is set: an open port is not a detail to find out later.
            row {
                SwitchRow(
                    title = stringResource(R.string.settings_start_without_wifi),
                    summary = startWithoutWifiSummary(context, state),
                    summaryColor = if (startWithoutWifiFailed(state)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    icon = SettingsIcons.WifiOff,
                    checked = state.startWithoutWifi,
                    onCheckedChange = vm::setStartWithoutWifi,
                )
            }
            // Setup's debugging question, answerable again. Debugging itself is
            // not a choice while JemRec is on - the recorder needs it - so the
            // summary says what stays on whichever way the switch is set, and
            // the switch is only about Android's reminder of it. See
            // DebugNotificationCleaner.
            row {
                SwitchRow(
                    title = stringResource(R.string.settings_hide_debug_notification),
                    summary = state.debugNotificationProblem?.let { grantProblem(context, it) }
                        ?: stringResource(R.string.settings_hide_debug_summary),
                    summaryColor = if (state.debugNotificationProblem != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    icon = SettingsIcons.Hidden,
                    checked = state.debugNotificationHidden,
                    onCheckedChange = vm::setHideDebugNotification,
                )
            }
            // Setup's pairing question, answerable again (KeepPaired). The
            // switch shows Android's setting as read, whoever set it.
            row {
                SwitchRow(
                    title = stringResource(R.string.settings_keep_paired),
                    summary = keepPairedSummary(context, state),
                    summaryColor = if (state.keepPairedRefused) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    icon = SettingsIcons.Link,
                    checked = state.pairingKept,
                    onCheckedChange = vm::setKeepPaired,
                )
            }
        }

        SettingsGroup(stringResource(R.string.troubleshooting)) {
            // ONLY WHERE THERE IS SOMETHING TO OPEN. On a phone with no launch
            // manager this row would be an offer to fix something that is not
            // broken, which is worse than saying nothing - so AutoStart answers
            // whether the screen exists before the row is drawn at all.
            if (autoStart != null) {
                row {
                    SettingsRow(
                        title = stringResource(R.string.settings_survive_restart),
                        summary = context.getString(R.string.settings_survive_restart_body, autoStart),
                        icon = Icons.Filled.Refresh,
                        onClick = { AutoStart.open(context) },
                        modifier = Modifier.bringIntoViewRequester(background),
                    )
                }
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_diagnostics),
                    summary = if (state.daemonRunning) stringResource(R.string.settings_recorder_running) else stringResource(R.string.settings_recorder_not_running),
                    summaryColor = if (state.daemonRunning) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    icon = Icons.Filled.Build,
                    onClick = { vm.showScreen(Screen.DIAGNOSTICS) },
                )
            }
            // In the destructive colour, so a glance tells it apart, and it
            // still asks before doing anything.
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_start_fresh),
                    summary = stringResource(R.string.settings_start_fresh_summary),
                    titleColor = MaterialTheme.colorScheme.error,
                    iconTint = MaterialTheme.colorScheme.error,
                    icon = SettingsIcons.Restore,
                    // Live while the app checks: it only asks, and the
                    // yes waits its turn (START_FRESH, QUEUED).
                    onClick = vm::askReset,
                )
            }
        }

        // What is installed, and the way to the newest beside it (discussion
        // #91): JemRec asks no server itself.
        SettingsGroup(stringResource(R.string.about)) {
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_version),
                    summary = state.appVersion,
                    icon = Icons.Filled.Info,
                )
            }
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_newest_version),
                    summary = stringResource(R.string.settings_newest_version_summary),
                    icon = SettingsIcons.OpenInNew,
                    onClick = vm::openNewestVersion,
                )
            }
        }
    }
}

@Composable
private fun keptAndTrashIntro(): String =
    stringResource(R.string.settings_kept_and_trash, Trash.DAYS)

/** An age to choose, in the header's words for it (ageWords) - or Never. */
private fun ageChoice(context: android.content.Context, days: Int): String =
    if (days == 0) context.getString(com.jemcik.jemrec.R.string.settings_never) else ageWords(context, days)

/** stringResource(R.string.settings_play_recordings) in Settings' words. Without a proximity sensor the
 *  earpiece at the ear is not offered, and the default reads as the
 *  loudspeaker it is there (AtTheEar watches no sensor). */
internal fun playThroughLabel(context: Context, playThrough: PlayThrough, raiseAvailable: Boolean): String =
    when (playThrough) {
        PlayThrough.LOUDSPEAKER -> context.getString(R.string.loudspeaker)
        PlayThrough.LOUDSPEAKER_AND_EAR ->
            if (raiseAvailable) context.getString(R.string.play_through_loudspeaker_and_ear)
            else context.getString(R.string.loudspeaker)
        PlayThrough.EARPIECE -> context.getString(R.string.earpiece)
    }

/** Each choice's line in the dialog: what holding the phone does. */
private fun playThroughDetail(context: Context, playThrough: PlayThrough): String = when (playThrough) {
    PlayThrough.LOUDSPEAKER -> context.getString(R.string.play_detail_hold_nothing)
    PlayThrough.LOUDSPEAKER_AND_EAR -> context.getString(R.string.play_detail_raise)
    PlayThrough.EARPIECE -> context.getString(R.string.play_detail_earpiece)
}

/**
 * Why a switch that needs notification access was not turned on
 * (Setup.grantNotificationAccess), in one wording for both. It said "it
 * needs Wi-Fi" for every failure, on Wi-Fi too, where what was missing was
 * Wireless debugging, and the recorder grants it now without either.
 */
internal fun grantProblem(context: Context, problem: Setup.GrantProblem): String = when (problem) {
    Setup.GrantProblem.NO_ANSWER -> context.getString(R.string.settings_grant_no_answer)
    Setup.GrantProblem.REFUSED -> context.getString(R.string.settings_grant_refused)
}

/**
 * Keep JemRec paired's summary: what Android does with the pairing as the
 * switch stands, and what keeping it costs - a computer's approval is kept
 * too, and turning it off can cost the pairing at once. A choice no recorder
 * has carried out says when it will be; one the recorder could not, says so,
 * in red, until a try works.
 */
internal fun keepPairedSummary(context: Context, state: UiState): String = when {
    state.keepPairedRefused -> context.getString(R.string.settings_keep_paired_refused)
    state.keepPairedWaiting == true && !state.pairingKept ->
        context.getString(R.string.settings_keep_paired_turns_on)
    state.keepPairedWaiting == false && state.pairingKept ->
        context.getString(R.string.settings_keep_paired_turns_off)
    // Turned off, Android deletes a pairing older than its days at once:
    // its own check runs as the setting changes (AdbDebuggingManager).
    state.pairingKept -> context.getString(R.string.settings_keep_paired_kept)
    else -> context.getString(
        R.string.settings_keep_paired_forgotten,
        pairingDays(context, state.pairingDays),
    )
}

/** Settings' names for [RecordingQuality]: plain words, the rate in the detail. */
internal fun qualityLabel(context: Context, quality: RecordingQuality): String = when (quality) {
    RecordingQuality.SMALLEST -> context.getString(R.string.quality_smallest)
    RecordingQuality.STANDARD -> context.getString(R.string.quality_standard)
    RecordingQuality.HIGH -> context.getString(R.string.quality_high)
    RecordingQuality.HIGHEST -> context.getString(R.string.quality_highest)
}

/** What each choice costs, and the one thing the smallest gives up: in a
 *  blind test its messenger call "sounds in mono" (RecordingQuality). */
private fun qualityDetail(context: Context, quality: RecordingQuality): String {
    val size = context.getString(
        R.string.quality_detail_size,
        quality.bitRate / 1000,
        quality.mbPerHour,
    )
    return when (quality) {
        RecordingQuality.SMALLEST -> context.getString(R.string.quality_detail_smallest, size)
        RecordingQuality.HIGHEST -> context.getString(R.string.quality_detail_highest, size)
        else -> context.getString(R.string.quality_detail_suffix, size)
    }
}

/** A format's name - the same words in the dialog and on the row, so the row
 *  shows the option that was picked. */
private fun fileNamesLabel(context: Context, format: FileNames.Format): String = when (format) {
    FileNames.Format.DATE -> context.getString(R.string.filename_date)
    FileNames.Format.DATE_AND_CALLER -> context.getString(R.string.filename_date_caller)
    FileNames.Format.DATE_AND_NUMBER -> context.getString(R.string.filename_date_number)
    FileNames.Format.HONOR -> context.getString(R.string.filename_honor_label)
}

/** What stops the chosen format from working yet, or null. */
internal fun fileNamesProblem(context: Context, state: UiState): String? = when {
    state.fileNames != FileNames.Format.DATE && !state.canReadCallLog ->
        context.getString(R.string.filename_needs_call_log)
    state.fileNames == FileNames.Format.HONOR && !state.inHonorFolder ->
        context.getString(R.string.filename_not_honor_folder, FileNames.HONOR_FOLDER)
    else -> null
}

/** The rules in a few words, for the row, or as a sentence for the top of
 *  their screen; [nothing] when no call would be kept. Tested, with the others
 *  below, in SettingsSummaryTest. */
internal class RulesSummary(val text: String, val nothing: Boolean = false)

/**
 * What the rules come to, decided once for both the row and the sentence, so
 * the two cannot disagree - they were two copies of this, and a fix to one
 * (all but nobody read "except calls with 0 contacts") had to be made twice.
 */
private enum class RulesCase { EVERY, CONTACTS, ALL_BUT, CONTACTS_BUT, STRANGERS, SOME_AND_STRANGERS, SOME, NOTHING }

private fun rulesCase(state: UiState): RulesCase {
    val n = state.ruleChosen.size
    val other = state.ruleOtherNumbers
    return when (state.ruleContacts) {
        // All but nobody - until someone is added - is all, and is said so.
        CallRules.Contacts.ALL -> if (other) RulesCase.EVERY else RulesCase.CONTACTS
        CallRules.Contacts.ALL_BUT -> when {
            n == 0 -> if (other) RulesCase.EVERY else RulesCase.CONTACTS
            other -> RulesCase.ALL_BUT
            else -> RulesCase.CONTACTS_BUT
        }
        CallRules.Contacts.ONLY -> when {
            n == 0 -> if (other) RulesCase.STRANGERS else RulesCase.NOTHING
            other -> RulesCase.SOME_AND_STRANGERS
            else -> RulesCase.SOME
        }
        CallRules.Contacts.NONE -> if (other) RulesCase.STRANGERS else RulesCase.NOTHING
    }
}

private fun people(context: Context, state: UiState): String =
    state.ruleChosen.size.let { n ->
        if (n == 1) context.getString(R.string.settings_one_contact)
        else context.getString(R.string.settings_n_contacts, n)
    }

internal fun rulesSummary(context: Context, state: UiState): RulesSummary {
    if (!state.canReadCallLog || !state.canReadContacts) {
        return RulesSummary(context.getString(R.string.rules_summary_every_call))
    }
    val who = people(context, state)
    return when (rulesCase(state)) {
        RulesCase.EVERY -> RulesSummary(context.getString(R.string.rules_summary_every_call))
        RulesCase.CONTACTS -> RulesSummary(context.getString(R.string.rules_summary_contacts))
        RulesCase.ALL_BUT -> RulesSummary(context.getString(R.string.rules_summary_everyone_but, who))
        RulesCase.CONTACTS_BUT -> RulesSummary(context.getString(R.string.rules_summary_contacts_but, who))
        RulesCase.STRANGERS -> RulesSummary(context.getString(R.string.rules_summary_strangers))
        RulesCase.SOME_AND_STRANGERS -> RulesSummary(context.getString(R.string.rules_summary_some_strangers, who))
        RulesCase.SOME -> RulesSummary(context.getString(R.string.rules_summary_some, who))
        RulesCase.NOTHING -> RulesSummary(context.getString(R.string.rules_summary_nothing), nothing = true)
    }
}

/** The rules as a sentence, at the top of their screen. */
internal fun rulesSentence(context: Context, state: UiState): RulesSummary {
    val who = people(context, state)
    return when (rulesCase(state)) {
        RulesCase.EVERY -> RulesSummary(context.getString(R.string.rules_sentence_every))
        RulesCase.CONTACTS -> RulesSummary(context.getString(R.string.rules_sentence_contacts))
        RulesCase.ALL_BUT -> RulesSummary(context.getString(R.string.rules_sentence_all_but, who))
        RulesCase.CONTACTS_BUT -> RulesSummary(context.getString(R.string.rules_sentence_contacts_but, who))
        RulesCase.STRANGERS -> RulesSummary(context.getString(R.string.rules_sentence_strangers))
        RulesCase.SOME_AND_STRANGERS -> RulesSummary(context.getString(R.string.rules_sentence_some_strangers, who))
        RulesCase.SOME -> RulesSummary(context.getString(R.string.rules_sentence_some, who))
        RulesCase.NOTHING -> RulesSummary(context.getString(R.string.rules_sentence_nothing), nothing = true)
    }
}

/**
 * Start without Wi-Fi, as Settings says it: what it does while off, where it
 * stands while on - the port open, or waiting for the Wi-Fi it is opened over
 * - and, open or waiting, that the port is open to every network the phone
 * joins (StartWithoutWifi). Switched off with the port it opened still open
 * - the close waits for a Wi-Fi with Wireless debugging, a call's end, and
 * JemRec back on if its off switched debugging off (CaptureDaemon
 * .portChangeDue, portChangeOver) - it says that, not
 * the off text. An ask that left the port unopened is said too, unless the port is
 * open after all: the switch would otherwise claim a door that is not there,
 * or deny one that is. And while JemRec is off with all debugging, adbd is
 * stopped and nothing listens: a set port is closed until On, which is the
 * point of that choice for a bank. First of all, switched off with a close
 * that did not take (StartWithoutWifi.closeFailedThisBoot), in red: the port
 * stays open until a restart, which no other text would say.
 */
internal fun startWithoutWifiSummary(context: Context, state: UiState): String = when {
    !state.startWithoutWifi && state.portSet && state.portOurs && state.portCloseFailed ->
        if (state.debuggingOff) {
            context.getString(R.string.swifi_close_failed_debug)
        } else {
            context.getString(R.string.swifi_close_failed)
        }
    !state.startWithoutWifi && state.portSet && state.portOurs && state.debuggingOff ->
        context.getString(R.string.swifi_closed_reopen_wifi)
    !state.startWithoutWifi && state.portSet && state.portOurs ->
        context.getString(R.string.swifi_still_open)
    !state.startWithoutWifi ->
        context.getString(R.string.swifi_on_no_wifi)
    state.portSet && state.debuggingOff ->
        context.getString(R.string.swifi_closed_reopen_any)
    state.portSet ->
        context.getString(R.string.swifi_open_anywhere)
    state.portFailed -> context.getString(R.string.swifi_open_failed)
    else -> context.getString(R.string.swifi_opens_on_wifi)
}

/** The summaries said as an error: switched on and the port not opened, or
 *  switched off and JemRec's port not closed. */
internal fun startWithoutWifiFailed(state: UiState): Boolean =
    if (state.startWithoutWifi) {
        state.portFailed && !state.portSet
    } else {
        state.portCloseFailed && state.portSet && state.portOurs
    }

internal fun messengerSummary(context: Context, state: UiState): String = when {
    !state.voipSupported -> context.getString(R.string.settings_needs_android_14)
    state.voipInstalled.isEmpty() -> context.getString(R.string.settings_no_voip_installed)
    else -> state.voipInstalled.filter { it.pkg in state.voipEnabled }
        .joinToString(", ") { it.settingsName }
        .ifEmpty { context.getString(R.string.am_off) }
}

/** Where recordings go, and the way to change it. */
@Composable
private fun FolderDialog(state: UiState, change: () -> Unit, useDefault: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.settings_saving_to)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.folder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    when {
                        state.usingDefaultFolder ->
                            stringResource(R.string.settings_folder_default)
                        state.folderLost -> stringResource(
                            R.string.settings_folder_lost,
                            stringResource(R.string.folder_why_lost),
                        )
                        else -> stringResource(R.string.settings_folder_chosen)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            // Live while the screen is busy: the folder picked is taken at
            // once (FOLDER_CHOSEN bypasses it), as the busy table has it.
            TextButton(onClick = change) { Text(stringResource(R.string.settings_change)) }
        },
        dismissButton = {
            Row {
                if (!state.usingDefaultFolder) {
                    TextButton(onClick = useDefault, enabled = !state.busy) { Text(stringResource(R.string.settings_use_default)) }
                }
                TextButton(onClick = dismiss) { Text(stringResource(R.string.settings_cancel)) }
            }
        },
    )
}

/**
 * How new recordings are named (see FileNames), with an example under each so
 * nobody has to imagine it. The Honor format only on a phone with Honor's
 * Phone app, and with the one folder that app looks in a tap away.
 *
 * It stays open after a choice: what the choice needs - the call log, Honor's
 * folder - appears under it, with the button that gives it.
 */
@Composable
private fun FileNamesDialog(
    state: UiState,
    vm: MainViewModel,
    allow: () -> Unit,
    chooseHonorFolder: () -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val formats = FileNames.Format.entries.filter {
        it != FileNames.Format.HONOR || state.honorDialer || state.fileNames == FileNames.Format.HONOR
    }
    val needsCallLog = state.fileNames != FileNames.Format.DATE && !state.canReadCallLog
    val needsHonorFolder = state.fileNames == FileNames.Format.HONOR && !state.inHonorFolder
    ChoiceDialog(
        title = stringResource(R.string.settings_file_names),
        choices = formats,
        selected = state.fileNames,
        label = { fileNamesLabel(context, it) },
        detail = { format ->
            val example = when (format) {
                FileNames.Format.DATE -> "20261020_165705_out.ogg"
                FileNames.Format.DATE_AND_CALLER -> "20261020_165705_out_Priya Raman.ogg"
                FileNames.Format.DATE_AND_NUMBER -> "20261020_165705_out_+447700900123.ogg"
                FileNames.Format.HONOR -> "Priya Raman@+447700900123_20261020165705.ogg"
            }
            // Free to wrap after a _ or an @, between the name's parts: with
            // no space in it, the number's example broke as "...123.o" over
            // "gg" (the screen tests, 360 dp, 2026-11-01).
            example.replace("_", "_\u200B").replace("@", "@\u200B")
        },
        onChoose = vm::setFileNames,
        onDismiss = dismiss,
        closeOnChoose = false,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (state.fileNames) {
                        FileNames.Format.DATE ->
                            stringResource(R.string.settings_filename_date_desc)
                        FileNames.Format.DATE_AND_CALLER ->
                            stringResource(R.string.settings_filename_caller_desc)
                        FileNames.Format.DATE_AND_NUMBER ->
                            stringResource(R.string.settings_filename_number_desc)
                        FileNames.Format.HONOR ->
                            stringResource(R.string.settings_filename_honor_desc, FileNames.HONOR_FOLDER)
                    } + stringResource(R.string.settings_filename_new_only_suffix),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (needsCallLog) {
                    Text(
                        stringResource(R.string.settings_filename_needs_call_log),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (needsHonorFolder) {
                    Text(
                        stringResource(R.string.settings_filename_honor_folder, FileNames.HONOR_FOLDER),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        action = when {
            needsCallLog -> {
                { TextButton(onClick = allow) { Text(stringResource(R.string.settings_allow)) } }
            }
            needsHonorFolder -> {
                { TextButton(onClick = chooseHonorFolder) { Text(stringResource(R.string.settings_choose_folder)) } }
            }
            else -> null
        },
    )
}

/**
 * Whose calls to keep (see CallRules): which contacts, and other numbers.
 *
 * Both questions need to know who a call was with, which only the call log
 * and the contacts can say - so without them the screen says what it would do
 * and offers the permission, rather than showing switches that cannot work.
 */
@Composable
fun WhoseCallsScreen(
    state: UiState,
    vm: MainViewModel,
    askForCallLog: () -> Unit,
    pickContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    Column(modifier) {
        if (!state.canReadCallLog || !state.canReadContacts) {
            Text(
                stringResource(R.string.settings_whose_needs_permissions),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FilledTonalButton(
                onClick = { haptics.tap(); askForCallLog() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.settings_allow)) }
            return@Column
        }

        val sentence = rulesSentence(context, state)
        Text(
            sentence.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (sentence.nothing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SettingsHeading(stringResource(R.string.your_contacts), top = 8.dp)
        ConnectedChoices(
            choices = CallRules.Contacts.entries,
            selected = state.ruleContacts,
            label = { contacts ->
                when (contacts) {
                    CallRules.Contacts.ALL -> context.getString(R.string.all)
                    CallRules.Contacts.ALL_BUT -> context.getString(R.string.all_but)
                    CallRules.Contacts.ONLY -> context.getString(R.string.only)
                    CallRules.Contacts.NONE -> context.getString(R.string.none)
                }
            },
            onChoose = vm::setRuleContacts,
        )

        if (state.ruleContacts == CallRules.Contacts.ALL_BUT || state.ruleContacts == CallRules.Contacts.ONLY) {
            val leaveOut = state.ruleContacts == CallRules.Contacts.ALL_BUT
            Text(
                when {
                    state.ruleChosen.isEmpty() && leaveOut -> stringResource(R.string.settings_add_contacts_leave_out)
                    state.ruleChosen.isEmpty() -> stringResource(R.string.settings_add_contacts_keep)
                    leaveOut -> stringResource(R.string.settings_contacts_not_kept)
                    else -> stringResource(R.string.settings_contacts_kept)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            SettingsGroup {
                state.ruleChosen.forEach { chosen ->
                    row(key = chosen.key) {
                        SettingsRow(
                            title = chosen.name,
                            leading = { CallerAvatar(caller = chosen.name, photo = chosen.photo) },
                            trailing = {
                                IconButton(
                                    onClick = { haptics.tap(); vm.removeRuleContact(chosen.key) },
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.settings_remove_contact_cd, chosen.name),
                                    )
                                }
                            },
                        )
                    }
                }
                row {
                    SettingsRow(
                        title = stringResource(R.string.settings_add_contact),
                        titleColor = MaterialTheme.colorScheme.primary,
                        iconTint = MaterialTheme.colorScheme.primary,
                        icon = Icons.Filled.Person,
                        onClick = { pickContact() },
                    )
                }
            }
        }

        SettingsGroup(stringResource(R.string.settings_everyone_else)) {
            row {
                SwitchRow(
                    title = stringResource(R.string.settings_numbers_not_in_contacts),
                    summary = stringResource(R.string.settings_and_hidden_numbers),
                    checked = state.ruleOtherNumbers,
                    onCheckedChange = vm::setRuleOtherNumbers,
                )
            }
        }

        SettingsFooter(
            buildString {
                append(stringResource(R.string.footer_whose_calls))
                if (!state.automatic) append(stringResource(R.string.footer_whose_ask_first))
            },
        )
    }
}

/**
 * Messenger calls (see VoipApps): one switch per supported app that is
 * installed, all off until turned on, and caller names from their call
 * notifications - which needs notification access, said plainly.
 */
@Composable
fun MessengerCallsScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        if (!state.voipSupported) {
            Text(
                stringResource(R.string.messenger_needs_14),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            return@Column
        }
        if (state.voipInstalled.isEmpty()) {
            Text(
                stringResource(R.string.settings_no_messenger_installed),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            return@Column
        }

        SettingsGroup(stringResource(R.string.settings_record_calls_in)) {
            state.voipInstalled.forEach { app ->
                row {
                    SwitchRow(
                        title = app.settingsName,
                        checked = app.pkg in state.voipEnabled,
                        onCheckedChange = { vm.setVoipApp(app.pkg, it) },
                        leading = { AppIcon(app.pkg) },
                    )
                }
            }
        }

        if (state.voipEnabled.isNotEmpty()) {
            SettingsGroup(stringResource(R.string.settings_group_caller_names)) {
                row {
                    SwitchRow(
                        title = stringResource(R.string.settings_show_who_called),
                        summary = state.voipNamesProblem?.let { grantProblem(context, it) }
                            ?: stringResource(R.string.settings_voip_names_default),
                        summaryColor = if (state.voipNamesProblem != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        checked = state.voipNames,
                        enabled = !state.busy,
                        onCheckedChange = vm::setVoipNames,
                    )
                }
            }
        }

        SettingsFooter(
            stringResource(R.string.footer_messenger),
            stringResource(R.string.settings_messenger_footer_notifications),
        )
    }
}

/**
 * The installed app's own icon, so each switch is recognisable at a glance.
 * Falls back to a blank tile of the same size if the icon cannot be read.
 */
@Composable
private fun AppIcon(pkg: String) {
    val bitmap = rememberAppIcon(listOf(pkg), 40.dp)
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

/**
 * What a bug report needs, and nothing that only looks like it.
 *
 * This was the whole app while the transport was being proven, and it kept the
 * shape of that: two text fields for a pairing port and code, a Disconnect
 * button, and a line reading "ADB not connected" above all of it.
 *
 * Every one of those was misleading by the time anyone else saw it.
 *
 * "ADB NOT CONNECTED" was the worst, because it is the NORMAL state and it read
 * as a fault. ADB is a bootstrap here, not a transport: it exists to start the
 * shell-side daemon, and once that daemon is up it serves loopback and needs no
 * adbd, no mDNS and no Wi-Fi. A phone recording calls perfectly well on mobile
 * data has no ADB session and should not be told it is missing one. So the
 * screen answers the question that matters - is the recorder running - and
 * describes the ADB session as what it is.
 *
 * THE PORT AND CODE FIELDS ARE GONE. Typing a code into the app cannot work:
 * leaving Settings closes the pairing dialog, which is what generates the code
 * and listens for it. That is exactly why pairing moved to the notification
 * shade. A control that cannot work is worse than no control, and worse still
 * on a screen people open when something is already wrong.
 *
 * What is left is what someone would actually use: the state, the things that
 * test and repair it, and the log. Its own screen now, reached from Settings'
 * Troubleshooting group, where it used to sit at the foot of the one long page.
 */
@Composable
fun DiagnosticsScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup {
            row {
                SettingsRow(
                    title = if (state.daemonRunning) stringResource(R.string.settings_recorder_running) else stringResource(R.string.settings_recorder_not_running),
                    titleColor = if (state.daemonRunning) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    summary = if (state.connected) {
                        stringResource(R.string.adb_connected_summary)
                    } else {
                        stringResource(R.string.adb_normal_summary)
                    },
                    leading = {
                        StatusDot(if (state.daemonRunning) JemRecColors.play else MaterialTheme.colorScheme.error)
                    },
                )
            }
        }

        // Two related actions, one language: tonal buttons sharing the row at
        // equal width, not a loud one beside a faint one.
        ButtonPair(listOf(stringResource(R.string.after_self_test), stringResource(R.string.settings_start_recorder))) { i, modifier ->
            if (i == 0) {
                FilledTonalButton(
                    onClick = { haptics.tap(); vm.selfTest() },
                    enabled = !state.busy,
                    modifier = modifier,
                ) { ButtonLabel(stringResource(R.string.after_self_test)) }
            } else {
                // Off when there is nothing to start: against a running
                // recorder it short-circuits on the first ping and reports
                // success, which is indistinguishable from doing nothing -
                // because it is.
                FilledTonalButton(
                    onClick = { haptics.tap(); vm.bootstrapDaemon() },
                    enabled = !state.busy && !state.daemonRunning,
                    modifier = modifier,
                ) { ButtonLabel(stringResource(R.string.settings_start_recorder)) }
            }
        }

        // The answering machine's report row, for the last call: on when the
        // recorder is not, as that one is, and saying so in the report.
        SettingsGroup {
            row {
                SettingsRow(
                    title = stringResource(R.string.settings_copy_report),
                    summary = stringResource(R.string.settings_copy_report_diag),
                    icon = Icons.Filled.Share,
                    onClick = { haptics.tap(); vm.copyCallReport() },
                )
            }
        }

        if (state.log.isNotEmpty()) {
            // A block of its own: the log is output, set apart on the rows'
            // tone the way a code block is. Not the lowest container it wore
            // inside the old card: on the bare screen that is white on white.
            Text(
                text = state.log,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(20.dp),
                    )
                    .padding(12.dp),
                fontFamily = lookMono(),
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Android 13 and later: ask the system to add the tile, in its own dialog,
 *  and hand its answer to [answered]. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun addTile(context: Context, answered: (Int) -> Unit) {
    runCatching {
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(context, RecorderTile::class.java),
            "JemRec",
            Icon.createWithResource(context, R.drawable.ic_tile),
            context.mainExecutor,
        ) { result ->
            Log.i("JemRec", "tile: add request answered $result")
            answered(result)
        }
    }.onFailure { Log.w("JemRec", "tile: could not ask to add it", it) }
}

/**
 * What Start fresh does, and what it cannot: the permissions allowed are
 * handed back where Android lets an app do that ([handsBack]: Android 13 and
 * later), and what setup granted over debugging stays (Setup.reset says why).
 * It used to promise the permissions back on every version, and handed back
 * one.
 */
internal fun startFreshWords(context: Context, handsBack: Boolean): String {
    val permissions = if (handsBack) {
        context.getString(R.string.start_fresh_perm_hands_back)
    } else {
        context.getString(R.string.start_fresh_perm_no_hand)
    }
    val stays = if (handsBack) {
        ""
    } else {
        context.getString(R.string.start_fresh_android12)
    }
    return permissions + context.getString(R.string.start_fresh_mid) + stays +
        context.getString(R.string.start_fresh_debug_stays) +
        context.getString(R.string.start_fresh_close) +
        context.getString(R.string.start_fresh_recordings_kept)
}
