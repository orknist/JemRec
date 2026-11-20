package com.jemcik.jemrec.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jemcik.jemrec.R
import com.jemcik.jemrec.ui.theme.LightDark
import com.jemcik.jemrec.ui.theme.LocalLookChoice
import com.jemcik.jemrec.ui.theme.Look

@StringRes
internal fun lookNameRes(look: Look): Int = when (look) {
    Look.MEMO -> R.string.theme_memo
    Look.DECK -> R.string.theme_deck
    Look.MULTITRACK -> R.string.theme_multitrack
}

/** Android's own words: its dark theme guide offers "Light", "Dark" and
 *  "System default (the recommended default option)" (developer.android.com,
 *  Dark theme). "Same as phone" did not fit its button (the user,
 *  2026-10-23). */
@StringRes
internal fun lightDarkWordRes(lightDark: LightDark): Int = when (lightDark) {
    LightDark.PHONE -> R.string.theme_system
    LightDark.LIGHT -> R.string.theme_light
    LightDark.DARK -> R.string.theme_dark
}

/** The same in Settings' summary line, where "Deck · system" said too little. */
@StringRes
internal fun lightDarkSummaryRes(lightDark: LightDark): Int = when (lightDark) {
    LightDark.PHONE -> R.string.theme_system_default_summary
    LightDark.LIGHT -> R.string.theme_light_summary
    LightDark.DARK -> R.string.theme_dark_summary
}

/** In the guide's order, the phone's own last. */
private val LIGHT_DARK = listOf(LightDark.LIGHT, LightDark.DARK, LightDark.PHONE)

@StringRes
private fun blurbRes(look: Look): Int = when (look) {
    Look.MEMO -> R.string.theme_memo_blurb
    Look.DECK -> R.string.theme_deck_blurb
    Look.MULTITRACK -> R.string.theme_multitrack_blurb
}

/**
 * Settings > Theme: the home screen in the look picked, the three looks as
 * connected choices under it, then light or dark the same way - design C of
 * the canvas's "The Theme screen, three ways", the user's choice, 2026-10-23.
 * A choice repaints the app at once (Looks), this screen and its preview
 * included, so the preview is always the look and the mode picked.
 *
 * WHAT THE CARDS DID, AND WHERE EACH NOW IS. A card per look, each a picture
 * of its header, a radio, the name and a line:
 * - which look is picked, and picking one: the look's connected choices,
 *   radios to a screen reader as the cards were;
 * - what each looks like: the preview, of the picked one - drawn by the home
 *   screen's own parts, where the pictures were hand-drawn and had fallen
 *   behind; the others are a tap away, and a tap shows the whole look;
 * - the line about each: under the choices, for the picked one.
 */
@Composable
internal fun ThemeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val chosen = LocalLookChoice.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.theme_all_same),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LookPreview(vm, Modifier.padding(vertical = 4.dp).align(Alignment.CenterHorizontally))
        ConnectedChoices(
            choices = Look.entries,
            selected = chosen.look,
            label = { context.getString(lookNameRes(it)) },
            onChoose = vm::setLook,
        )
        Text(
            stringResource(blurbRes(chosen.look)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsHeading(stringResource(R.string.theme_light_or_dark), top = 12.dp)
        ConnectedChoices(
            choices = LIGHT_DARK,
            selected = chosen.lightDark,
            label = { context.getString(lightDarkWordRes(it)) },
            onChoose = vm::setLightDark,
        )
        Text(
            stringResource(R.string.theme_notif_follow_phone),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}