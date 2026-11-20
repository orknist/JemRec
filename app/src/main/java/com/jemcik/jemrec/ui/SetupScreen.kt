package com.jemcik.jemrec.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jemcik.jemrec.R
import com.jemcik.jemrec.setup.PairingNotification
import com.jemcik.jemrec.setup.Setup
import com.jemcik.jemrec.setup.SetupStep
import com.jemcik.jemrec.system.AutoStart
import com.jemcik.jemrec.system.DeviceSkin
import com.jemcik.jemrec.system.GlobalSettings
import com.jemcik.jemrec.system.ShellGrant

/**
 * Setup, as three things the user does by hand and nothing else.
 *
 * Allow two ordinary permissions; turn on Wireless debugging; type a six-digit
 * code. The app then grants itself the one PRIVILEGED permission it needs by
 * running `pm grant` over its own ADB session, and starts the recorder. No
 * computer is involved at any point.
 *
 * Before any of it, one question: what stays switched on, and whether to hide
 * Android's notification saying so. See SetupStep.NEEDS_DEBUGGING_CHOICE.
 *
 * The pairing PORT is never asked for. It changes every time the dialog opens
 * and means nothing to anyone, so it is discovered over mDNS instead.
 *
 * STEP 3 OFFERS ONE ROUTE, NOT TWO
 *
 * It used to lead with a code box on this screen and mention the notification
 * shade underneath as the easier option. That had it backwards. Typing the code
 * here means leaving Settings, and leaving Settings closes the pairing dialog
 * and kills the code - so the box worked only in split screen, and a user who
 * did not know that typed six correct digits and was told pairing failed. The
 * shade is not the easier route, it is the one that works, so it is now the
 * only one offered. The code box is gone entirely, diagnostics included: a
 * control that cannot work is worse than no control at all.
 */
@Composable
fun SetupScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    // The Settings screens are opened from HERE - each step's card, with the
    // foreground Activity (LocalContext) - not through the ViewModel's
    // Application context. A brand-new install has no adb yet - that is the
    // very thing this screen sets up - and MagicOS blocks an app that starts
    // an activity from a non-Activity context until it does, so "Open
    // settings" silently did nothing until a cable was plugged in. A launch
    // from the resumed Activity is a foreground launch, always allowed.
    val context = LocalContext.current
    // Whatever the user chooses, re-derive the step from what is actually
    // granted rather than from the result callback - "denied" and "denied
    // permanently" need different words, and only the real state knows.
    val permissionLauncher = rememberPermissionRequest { vm.refreshSetup() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // An install set up before the debugging question existed lands here
        // for that one question, and is not setting anything up - so it is
        // not greeted as if it were.
        val askingAfterSetup = (state.step == SetupStep.NEEDS_DEBUGGING_CHOICE ||
            state.step == SetupStep.NEEDS_KEEP_PAIRED_CHOICE) &&
            remember(state.step) { Setup.isComplete(context) }

        // titleLarge, the same style the app bar gives every other screen's
        // title - setup has no app bar of its own, but its heading should not be
        // a size the rest of the app never uses.
        Text(
            if (askingAfterSetup) stringResource(R.string.setup_one_question) else stringResource(R.string.setup_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (askingAfterSetup) stringResource(R.string.setup_subtitle_after)
            else stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )

        when (state.step) {
            // Full width like every other card here. Wrapping its content left
            // a small box hugging the left edge, which read as a stray label
            // rather than as this step's card.
            SetupStep.CHECKING -> CheckingCard()

            SetupStep.NEEDS_PERMISSIONS -> PermissionsCard(state, permissionLauncher)

            // What setup leaves switched on, said BEFORE anything is switched
            // on, and the one part of it that is the user's to decide. Neither
            // answer is preselected or styled as the expected one: hiding
            // Android's reminder that debugging is on is a real trade, and the
            // app used to make it for everyone. See NEEDS_DEBUGGING_CHOICE.
            SetupStep.NEEDS_DEBUGGING_CHOICE -> DebuggingChoiceCard(state, vm)

            // Keeping the pairing, which turns Android's own timeout off: the
            // user's to decide, the same way. See NEEDS_KEEP_PAIRED_CHOICE.
            SetupStep.NEEDS_KEEP_PAIRED_CHOICE -> KeepPairedCard(state, vm)

            // Only reached on a phone with a vendor auto-start screen (the gate
            // folds in AutoStart.available), whose name and instruction the
            // setup check found off the main thread (UiState). The fallback is
            // for a screen gone between the step's look and that one. See
            // SetupStep.NEEDS_BACKGROUND_CHOICE.
            SetupStep.NEEDS_BACKGROUND_CHOICE -> BackgroundCard(state, vm)

            SetupStep.NEEDS_DEVELOPER_OPTIONS -> DeveloperOptionsCard(state, vm)

            // Both debugging switches AND pairing, on one screen and one trip to
            // Settings - all of it happens in Developer options, so there was
            // never a reason to bounce back to the app between them.
            //
            // THE TWO SWITCHES ARE ONE STEP, AND ORDER DOES NOT MATTER. Both must
            // be on: Wireless debugging is the channel, USB debugging (no cable
            // involved) is what keeps adbd - and with it Wireless debugging -
            // from stopping. The steps once prescribed an order, Wireless first
            // then USB, because one run held that way; the next runs needed
            // several attempts in that order too. The toggles simply do not
            // hold reliably on this phone, in either order. What made pairing
            // land is PairingService: after the code it WATCHES both switches,
            // says which one is off, and connects the moment both are on. So
            // the card says "both, any order" and leaves the sequencing to the
            // service. See Setup.usbDebuggingOn.
            SetupStep.NEEDS_PAIRING -> PairingCard(state, vm)

            // Paired already - adbd trusts our key - so this is NOT the pairing
            // flow again, only the session that went missing: one or both
            // switches dropped (this phone turns them off by itself), and the
            // app connects on its own once both are back. Shorter than the
            // pairing card, because there is much less left to do.
            SetupStep.NEEDS_CONNECTION -> ConnectionCard(state, vm)

            SetupStep.FINISHING -> FinishingCard(state, vm)

            SetupStep.READY -> Unit // HomeScreen takes over.
        }

        if (state.log.isNotEmpty()) {
            Card {
                Text(
                    state.log.trim().lines().takeLast(4).joinToString("\n"),
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // NO DIAGNOSTICS HERE. Setup is the one screen guaranteed to be read by
        // someone who has never seen this app, and "Show diagnostics" asks them
        // to have an opinion about a word that means nothing yet. It is still
        // in Settings, where a person goes looking for it on purpose.
    }
}

@Composable
private fun CheckingCard() {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.setup_checking), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PermissionsCard(state: UiState, permissionLauncher: PermissionRequest) {
    val haptics = LocalHapticFeedback.current
    Card {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.setup_allow_permissions),
                style = MaterialTheme.typography.titleMedium)
            // The first screen of setup, so the first impression of how
            // much work this is going to be. It was four paragraphs to
            // say "tap Allow three times" - a paragraph per permission,
            // each explaining itself to someone who has not agreed to
            // anything yet and only wants to know what happens next.
            //
            // The action goes in the body. Why, and the one fact worth
            // volunteering about a call recorder, go in the small print
            // underneath - same shape as every other card here.
            // No count. It was "both questions", which stopped being
            // true the moment audio access joined them, and is a
            // different number again on an older Android.
            Text(
                stringResource(R.string.setup_permissions_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.setup_permissions_small),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { haptics.tap(); permissionLauncher.launch(Setup.ASK_PERMISSIONS) },
                enabled = !state.busy,
            ) { Text(stringResource(R.string.settings_allow)) }

        }
    }
}

@Composable
private fun DebuggingChoiceCard(state: UiState, vm: MainViewModel) {
    val haptics = LocalHapticFeedback.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.setup_debugging_stays_on),
                style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.setup_debugging_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.setup_debugging_cable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.setup_hide_notification_q),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.setup_hide_notification_a),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ButtonPair(listOf(stringResource(R.string.setup_keep_it), stringResource(R.string.setup_hide_it))) { i, modifier ->
                FilledTonalButton(
                    onClick = { haptics.tap(); vm.setHideDebugNotification(i == 1) },
                    enabled = !state.busy,
                    modifier = modifier,
                ) { ButtonLabel(if (i == 0) stringResource(R.string.setup_keep_it) else stringResource(R.string.setup_hide_it)) }
            }
        }
    }
}

/** [days] as words: stringResource(R.string.s_1_day), "7 days". */
internal fun pairingDays(context: Context, days: Int): String =
    if (days == 1) context.getString(R.string.setup_pairing_days_one)
    else context.getString(R.string.setup_pairing_days, days)

@Composable
private fun KeepPairedCard(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_keep_paired), style = MaterialTheme.typography.titleMedium)
            // What happens without it, in the user's terms: not "the key
            // expires" but a pairing to do again, and when.
            Text(
                stringResource(R.string.setup_pairing_forgotten_body, pairingDays(context, state.pairingDays)),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Its name where a phone's Developer options show it - the
            // Honor's do not (its Settings' resources, 2026-11-15) - and what
            // else it keeps: a computer's approval is not JemRec's to extend
            // without saying so.
            Text(
                stringResource(R.string.setup_keep_paired_timeout),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Two equal buttons, neither styled as the expected one, as the
            // debugging question's.
            ButtonPair(listOf(stringResource(R.string.setup_leave_it), stringResource(R.string.setup_keep_paired_btn))) { i, modifier ->
                FilledTonalButton(
                    onClick = { haptics.tap(); vm.setKeepPaired(i == 1) },
                    enabled = !state.busy,
                    modifier = modifier,
                ) { ButtonLabel(if (i == 0) stringResource(R.string.setup_leave_it) else stringResource(R.string.setup_keep_paired_btn)) }
            }
        }
    }
}

@Composable
private fun BackgroundCard(state: UiState, vm: MainViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        val generic = AutoStart.screenFor(context, null, null)
        val launch = state.backgroundLabel ?: generic.label
        val instruction = state.backgroundInstruction ?: generic.instruction
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.setup_keep_running),
                style = MaterialTheme.typography.titleMedium)
            // What it is, before where to tap: the worry is "will this
            // drain my battery or do something odd", and the answer is
            // that it only lets JemRec come back after the phone puts it
            // to sleep, which is what a call recorder has to do.
            Text(
                stringResource(R.string.setup_background_kill),
                style = MaterialTheme.typography.bodyMedium,
            )
            // The middle step is this phone's own: what to do on the
            // screen the button opens, in its words (AutoStart).
            Steps(
                stringResource(R.string.setup_tap_launch_below, launch),
                instruction,
                stringResource(R.string.setup_come_back_moves),
            )
            Text(
                stringResource(R.string.setup_background_once),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // ONE ROW, TWO EQUAL BUTTONS, as the debugging card just
            // before this one - the user found a lone button over a
            // full-width pair unbalanced (2026-10-25). The destination
            // is the label, as on the Developer options card. There is
            // no stringResource(R.string.done): opening the screen IS the answer (JemRec cannot
            // read the setting either way), so coming back moves setup
            // on, the way Developer options does. stringResource(R.string.setup_not_now) skips; the
            // warning built for a background kill brings the ask back.
            val labels = listOf(stringResource(R.string.setup_not_now), launch)
            ButtonPair(labels) { i, modifier ->
                FilledTonalButton(
                    onClick = {
                        haptics.tap()
                        if (i == 1) vm.openBackgroundSettings() else vm.confirmBackgroundChoice()
                    },
                    enabled = !state.busy,
                    modifier = modifier,
                ) { ButtonLabel(labels[i]) }
            }
        }
    }
}

@Composable
private fun DeveloperOptionsCard(state: UiState, vm: MainViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.setup_developer_options),
                style = MaterialTheme.typography.titleMedium)
            // "Developer options" is the most alarming phrase in this
            // whole flow to someone who is not one. The reassurance goes
            // FIRST, before the instructions, because a person who is
            // worried about breaking their phone does not read step 1.
            // The reassurance earns its line; the explanation of what
            // Developer options are does not. She is not deciding whether
            // to want them, she is deciding whether this is safe.
            Text(
                stringResource(R.string.setup_sounds_technical),
                style = MaterialTheme.typography.bodyMedium,
            )
            Steps(
                stringResource(R.string.setup_tap_about_phone),
                // Where Build number lives is the one instruction here
                // that is certainly wrong on another make of phone: it is
                // loose in About phone on this one, tucked inside Software
                // information on a Samsung, inside Version on a OnePlus -
                // and on a Xiaomi there is no "Build number" row at all,
                // you tap "OS version". Naming the likely places, and the
                // one alternate label, beats naming a single path.
                stringResource(R.string.setup_build_number_step),
                stringResource(R.string.setup_pin_if_asked),
                // No button to tap here any more. The app re-derives the
                // step every time it returns to the foreground, so coming
                // back from Settings is itself what advances this - the old
                // "I have done it" only re-ran that same check, and sat
                // greyed while it ran.
                stringResource(R.string.setup_come_back_notices),
            )
            Text(
                stringResource(R.string.setup_developer_worked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // One button, because there is nothing to confirm - see the
            // last step. The destination is the label; "Open" was doing
            // nothing the button shape does not.
            Button(onClick = { haptics.tap(); Setup.openAboutPhone(context) }) {
                Text(stringResource(R.string.setup_about_phone))
            }
            // Android 17 on some phones will not tell apps whether
            // Developer options are on, so "it notices on its own"
            // cannot happen until Wireless debugging is on as well.
            // The user knows; let them say so. If they are wrong, the
            // next card's first step is opening Developer options,
            // and that is where they find out.
            if (state.developerOptionsHidden) {
                Text(
                    stringResource(R.string.setup_dev_hidden_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { haptics.tap(); vm.confirmDeveloperOptions() },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.setup_dev_on_already)) }
            }
        }
    }
}

@Composable
private fun PairingCard(state: UiState, vm: MainViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    Card {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val bothOn = state.usbDebugging != GlobalSettings.Reading.OFF &&
                state.wirelessDebuggingOn
            Text(
                if (bothOn) stringResource(R.string.setup_pair_title) else stringResource(R.string.setup_pair_title_both),
                style = MaterialTheme.typography.titleMedium,
            )
            // Back here without asking: say why, and that nothing
            // else went with the pairing.
            if (state.pairingLost) {
                // Why, where the timeout is on - the usual cause - and the
                // switch that stops it (KeepPaired).
                val why = if (state.pairingKept) "" else
                    context.getString(R.string.setup_pairing_why_deleted, pairingDays(context, state.pairingDays))
                Text(
                    context.getString(R.string.setup_pairing_lost, why),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Why the code goes into a notification and not back here,
            // said once and plainly: a person told to type a code
            // somewhere other than where it appeared will otherwise
            // assume they have misread the instruction.
            Text(
                stringResource(R.string.setup_code_in_notification),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!state.wirelessDebuggingOn) {
                Text(
                    stringResource(R.string.setup_wifi_first),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Named here, where the user is about to be in Developer
            // options anyway, not once the grant has failed: issues
            // #61 and #64, a OnePlus each (ShellGrant).
            if (state.systemOptimizationSwitch) {
                Text(
                    stringResource(
                        R.string.setup_shell_grant_switch,
                        ShellGrant.switchText(context),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // The one notification state that actually strands the user:
            // with notifications off there is nowhere for the code to
            // land, so say so and offer the fix before the steps that
            // assume a notification can appear. Whether it also POPS as a
            // heads-up is neither knowable nor needed here - the steps
            // say "swipe down".
            if (state.pairingNotificationsBlocked) {
                Text(
                    stringResource(R.string.setup_notif_blocked_pairing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                FilledTonalButton(
                    onClick = {
                        haptics.tap()
                        PairingNotification.openNotificationSettings(context)
                    },
                ) { Text(stringResource(R.string.setup_turn_on_notifications)) }
            }

            val skin = DeviceSkin.current()
            Steps(
                *buildList {
                    if (bothOn) {
                        add(
                            stringResource(R.string.setup_pair_step_open_settings)
                        )
                    } else {
                        add(developerOptionsStep(context, skin, mayBeOff = state.developerOptionsHidden))
                        add(debuggingSwitchesStep(context, state.usbDebugging, state.wirelessDebuggingOn))
                        add(
                            stringResource(R.string.setup_pair_step_wireless)
                        )
                    }
                    add(
                        stringResource(R.string.setup_pair_step_enter_code)
                    )
                    add(
                        stringResource(R.string.setup_pair_step_after_notif)
                    )
                }.toTypedArray()
            )
            // How long the pairing lasts: for good only with Android's
            // timeout off (KeepPaired). Back for a lost pairing, the card
            // has said it above.
            val lasts = when {
                state.pairingKept || state.keepPairedWaiting == true ->
                    context.getString(R.string.setup_pair_remember_once)
                state.pairingLost -> ""
                else -> context.getString(
                    R.string.setup_pair_remember_days,
                    pairingDays(context, state.pairingDays),
                )
            }
            Text(
                context.getString(R.string.setup_notif_waiting) + lasts,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptics.tap()
                        // Post the code field first, then open Settings -
                        // from the Activity, so it opens without adb.
                        vm.showPairingNotification()
                        Setup.openSettings(context)
                    },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.setup_open_settings)) }
                TextButton(
                    onClick = { haptics.tap(); vm.startPairingNotification() },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.setup_show_code_field)) }
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: UiState, vm: MainViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    Card {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.setup_finish_connecting),
                style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.setup_already_paired),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.usbDebugging == GlobalSettings.Reading.OFF || !state.wirelessDebuggingOn) {
                val skin = DeviceSkin.current()
                Steps(
                    developerOptionsStep(context, skin, mayBeOff = state.developerOptionsHidden),
                    debuggingSwitchesStep(
                        context,
                        state.usbDebugging,
                        state.wirelessDebuggingOn,
                        mayLookOn = true,
                    ),
                    stringResource(R.string.setup_stay_on_screen),
                )
                // Start connecting in the BACKGROUND, then open Settings -
                // so the connect lands while the user is still on the
                // Developer options screen (where the switches hold), not
                // after they return here (which can drop them).
                Button(
                    onClick = {
                        haptics.tap()
                        vm.reconnect()
                        Setup.openSettings(context)
                    },
                ) { Text(stringResource(R.string.setup_open_settings)) }
            } else {
                // Both switches on but the session did not open - rarer.
                // Keep trying in the background for a while rather than
                // taking a single shot.
                Text(
                    if (state.usbDebugging == GlobalSettings.Reading.UNKNOWN) {
                        stringResource(R.string.setup_reach_fail_usb)
                    } else {
                        stringResource(R.string.setup_reach_fail_both)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(
                    onClick = { haptics.tap(); vm.reconnect() },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.setup_try_again)) }
            }
        }
    }
}

@Composable
private fun FinishingCard(state: UiState, vm: MainViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    Card {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val failed = state.finishFailed
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Only while it is working: failed, nothing is until
                // Try again, and a spinner then said otherwise.
                if (failed == null || state.busy) CircularProgressIndicator()
                Text(stringResource(R.string.setup_almost_done), style = MaterialTheme.typography.titleMedium)
            }
            if (failed == null) {
                Text(
                    stringResource(R.string.setup_starting_recorder),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    stringResource(R.string.setup_finish_failed, failed),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Most failures are a switch to turn on first (ShellGrant).
                // A FlowRow: at twice the text size the pair does not fit
                // one line, and Open settings went to two inside its button.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(onClick = vm::retryFinish, enabled = !state.busy) { Text(stringResource(R.string.setup_try_again)) }
                    TextButton(
                        onClick = { haptics.tap(); Setup.openSettings(context) },
                    ) { Text(stringResource(R.string.setup_open_settings)) }
                }
            }
        }
    }
}

/**
 * One trip to Developer options.
 *
 * THE SEARCH LEADS AND THE PATH FOLLOWS, because only one of them is always
 * true. Settings' own search finds Developer options on every phone; the menu
 * it sits in is a guess from the maker's name, and a guess stated as an
 * instruction sends someone hunting for a menu their phone does not have -
 * reported from a OnePlus running LineageOS, which was told to look under
 * OxygenOS's "Additional settings". "Usually" is the honest word for it, and
 * DeviceSkin now recognises a custom ROM, but the wording should survive the
 * next phone that fools it.
 *
 * mayBeOff: the phone hides whether Developer options are on (see
 * Setup.developerOptions), so the user may have said "they are on already"
 * when they are not. Then this step is where they find out, and it says what
 * to do about it rather than sending them looking for a screen that is not
 * there.
 */
private fun developerOptionsStep(context: Context, skin: DeviceSkin, mayBeOff: Boolean = false): String =
    context.getString(R.string.setup_dev_search_step, skin.developerOptionsHint) +
        if (mayBeOff) context.getString(R.string.setup_dev_not_there) else ""

/**
 * The two debugging switches as ONE step. Both must be on - Wireless debugging
 * is the channel, USB debugging (no cable involved) is what keeps adbd, and
 * with it Wireless debugging, from stopping - and the order does not matter:
 * on this phone the toggles hold unreliably in either order, and it is
 * PairingService that watches them and connects once both are on. Names the
 * one still off when the other is already on, so nobody is told to turn on a
 * switch that is.
 *
 * mayLookOn: on the reconnect card the phone can SHOW a switch on that is not
 * (measured on this Honor), so the step says to flip it off and on.
 *
 * Where Android 17 hides USB debugging from the app (UNKNOWN), the step asks
 * for it without claiming to know which way it is.
 */
private fun debuggingSwitchesStep(
    context: Context,
    usb: GlobalSettings.Reading,
    wirelessOn: Boolean,
    mayLookOn: Boolean = false,
): String = buildString {
    val usbOff = usb == GlobalSettings.Reading.OFF
    val usbUnknown = usb == GlobalSettings.Reading.UNKNOWN
    append(
        when {
            usbOff && !wirelessOn -> context.getString(R.string.setup_usb_wireless_both)
            usbOff -> context.getString(R.string.setup_usb_too)
            usbUnknown && !wirelessOn -> context.getString(R.string.setup_wireless_and_usb)
            usbUnknown -> context.getString(R.string.setup_check_usb_too)
            else -> context.getString(R.string.setup_wireless_too)
        },
    )
    if (mayLookOn) append(context.getString(R.string.setup_switch_toggle_twice))
    append(context.getString(R.string.setup_usb_off_each_open))
    if (!wirelessOn) append(context.getString(R.string.setup_allow_network))
}

/**
 * Numbered instructions.
 *
 * A paragraph is the wrong shape for something performed one action at a time,
 * in another app, against a dialog that closes if you dawdle. Numbers give the
 * user somewhere to put their finger and a place to come back to when they look
 * up from Settings and forget where they were.
 */
@Composable
private fun Steps(vararg lines: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEachIndexed { index, line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
