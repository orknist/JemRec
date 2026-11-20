package com.jemcik.jemrec.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.jemcik.jemrec.R
import com.jemcik.jemrec.recorder.OffMode

/** A choice's name: the row in Settings shows it as the setting's value, the
 *  screen as the choice - one wording, so the two cannot drift. */
internal fun offModeTitle(context: Context, mode: OffMode): String = context.getString(
    when (mode) {
        OffMode.KEEP_DEBUGGING -> R.string.off_mode_keep_debugging
        OffMode.HIDE_DEVELOPER_OPTIONS -> R.string.off_mode_hide_developer_options
        OffMode.ALL_DEBUGGING_OFF -> R.string.off_mode_all_debugging_off
    },
)

/**
 * Each choice's two facts, worded the same way for all three so they read
 * across: what goes off with JemRec, and what turning it back on takes. The
 * user read an earlier draft three times and could not tell the second choice
 * from the third (2026-10-26) - one said "Wireless debugging goes off too",
 * the other "All of it" - and the difference is exactly these two lines.
 * [portSet]: adbd is on Start without Wi-Fi's port, so the third comes back
 * with no Wi-Fi too.
 */
internal fun offModeLines(context: Context, mode: OffMode, portSet: Boolean): List<Pair<String, String>> {
    val switches = context.getString(R.string.off_switches_off)
    val back = context.getString(R.string.off_back_on)
    return when (mode) {
        OffMode.KEEP_DEBUGGING -> listOf(
            switches to context.getString(R.string.off_mode_nothing_else),
            back to context.getString(R.string.off_mode_back_on_instant),
        )
        OffMode.HIDE_DEVELOPER_OPTIONS -> listOf(
            switches to context.getString(R.string.off_mode_switches_dev_wireless),
            back to context.getString(R.string.off_mode_back_on_instant),
        )
        OffMode.ALL_DEBUGGING_OFF -> listOf(
            switches to context.getString(R.string.off_mode_switches_all_debug),
            back to context.getString(
                if (portSet) R.string.off_anywhere else R.string.off_needs_wifi_15s,
            ),
        )
    }
}

/**
 * When JemRec is off (DebugSwitchesOff): what else goes off with it, for the
 * apps - banking ones mostly - that will not open while Developer options or
 * debugging is on (issue #37). Picked in place, as Android's settings
 * guidelines allow a single choice on a screen of its own.
 */
@Composable
fun WhenOffScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        // One group to TalkBack, as the choice dialogs' radios are: it then
        // says "1 of 3" with each, so they are heard as one choice.
        Column(Modifier.selectableGroup()) {
            SettingsGroup {
                OffMode.entries.forEach { mode ->
                    row(mode) {
                        ChoiceRow(
                            title = offModeTitle(context, mode),
                            selected = mode == state.offMode,
                            onSelect = { vm.setOffMode(mode) },
                            lines = offModeLines(context, mode, state.portSet),
                        )
                    }
                }
            }
        }
        SettingsFooter(*whenOffFooter(context, state).toTypedArray())
    }
}

/**
 * The When JemRec is off screen's footer. The port is adbd's, and adbd runs
 * while USB debugging is on: the first two choices leave it open - Hide
 * Developer options, the one tried first for a bank, included
 * (StartWithoutWifi). Said with the switch on, and with it off while the port
 * is open all the same - a computer's `adb tcpip`, or a close that did not
 * take - where pointing at the switch would contradict the lines above,
 * which say Back on: anywhere. The choice applies at the next off: On puts
 * back what the last off switched off, whatever this says by then.
 */
internal fun whenOffFooter(context: Context, state: UiState): List<String> = listOfNotNull(
    context.getString(R.string.off_footer_banking),
    when {
        state.startWithoutWifi -> context.getString(R.string.off_footer_port_start_without_wifi)
        state.portSet -> context.getString(R.string.off_footer_port_stays_open)
        else -> context.getString(R.string.off_footer_start_without_wifi_hint)
    },
    context.getString(R.string.off_footer_applies_next_off).takeIf { !state.enabled },
)