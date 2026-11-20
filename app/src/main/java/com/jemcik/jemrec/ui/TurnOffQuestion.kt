package com.jemcik.jemrec.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jemcik.jemrec.R

/**
 * The one question turning JemRec off can ask: with When JemRec is off set to
 * Turn all debugging off, and no Wi-Fi to come back on over (OffMode.asksFirst).
 * Every other off is instant both ways and asks nothing - Material 3 keeps
 * dialogs for the decisions that need one (m3.material.io, Dialogs).
 *
 * The same composable for the header's button and for the Quick Settings tile
 * (TurnOffQuestionActivity), so the two cannot word it differently.
 */
@Composable
internal fun TurnOffAwayFromWifiDialog(onTurnOff: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.turn_off_away_from_wi_fi)) },
        text = { Text(stringResource(R.string.turn_off_away_wifi_body)) },
        confirmButton = { TextButton(onClick = onTurnOff) { Text(stringResource(R.string.turn_off)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
