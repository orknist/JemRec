package com.jemcik.jemrec.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** User-visible copy held as a resource id (and optional format args) until shown. */
internal data class UiText(
    @StringRes val res: Int,
    val args: Array<Any> = emptyArray(),
) {
    fun resolve(context: Context): String =
        if (args.isEmpty()) context.getString(res) else context.getString(res, *args)

    @Composable
    fun resolve(): String =
        if (args.isEmpty()) stringResource(res) else stringResource(res, *args)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UiText) return false
        if (res != other.res) return false
        if (!args.contentEquals(other.args)) return false
        return true
    }

    override fun hashCode(): Int = 31 * res + args.contentHashCode()
}

internal fun uiText(@StringRes res: Int, vararg args: Any) = UiText(res, arrayOf(*args))
