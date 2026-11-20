package com.jemcik.jemrec.recordings

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.jemcik.jemrec.DeepLink
import com.jemcik.jemrec.Prefs
import com.jemcik.jemrec.R
import com.jemcik.jemrec.calls.AfterCall

/**
 * The folder chosen for recordings lost - [whyLost] - and said: on home
 * and in Settings while it lasts (UiState.folderLost, from [lost]), and once
 * in a notification at the first save it sent elsewhere, for whoever is not
 * opening JemRec. Until 1.2 a save fell back
 * without a word, and the folder's recordings left the list: for Honor's
 * file names, the Phone app's play buttons went with them.
 */
internal object FolderAccess {

    private const val TAG = "JemRec"
    private const val KEY_TOLD = "folder_lost_told"

    /** What may have happened to it, said the same in the notification, on
     *  home and in Settings: two of them had lost the drive (reviews,
     *  2026-11-18). */
    fun whyLost(context: Context): String = context.getString(R.string.folder_why_lost)

    /** Among the problems' ids (AfterCall's 31 and 33, MissedCalls' 32,
     *  RecorderHealth's 34, BackgroundKill's 35). */
    private const val NOTIFICATION_ID = 36

    /** Whether the folder chosen is lost now; the folder there again, what
     *  was said of it goes. */
    fun lost(context: Context): Boolean = when (RecordingStore.folderReachable(context)) {
        null -> false
        true -> { reached(context); false }
        false -> true
    }

    /** A save gone to [where] instead of the folder chosen: said once, if
     *  the folder is lost - a passing failure with the folder there is the
     *  log's alone. */
    fun savedElsewhere(context: Context, where: String) {
        if (RecordingStore.folderReachable(context) != false) {
            Log.w(TAG, "store: saved to $where though the folder chosen is there")
            return
        }
        val prefs = Prefs.of(context)
        if (prefs.getBoolean(KEY_TOLD, false)) return
        runCatching {
            // The tap: home, whose card says it too; the button: Android's
            // folder picker, at once.
            val tap = PendingIntent.getActivity(context, 0, DeepLink.home(context), PendingIntent.FLAG_IMMUTABLE)
            val choose = PendingIntent.getActivity(
                context, 3601, DeepLink.intent(context, DeepLink.Kind.CHOOSE_FOLDER),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = context.getString(
                R.string.folder_saved_elsewhere_body,
                RecordingStore.chosenWords(RecordingStore.describe(context)),
                whyLost(context),
                where,
            )
            val seen = AfterCall.notifyProblem(
                context, NOTIFICATION_ID,
                AfterCall.problem(
                    context,
                    context.getString(R.string.recording_saved_elsewhere),
                    text,
                    tap,
                    context.getString(R.string.choose_again) to choose,
                ).build(),
                "store",
            )
            // Told once - if it could be seen; else at the next save.
            if (seen) prefs.edit { putBoolean(KEY_TOLD, true) }
        }.onFailure { Log.w(TAG, "store: could not say the folder is lost", it) }
    }

    /** The folder there again, or another chosen: what was said goes. */
    fun reached(context: Context) {
        val prefs = Prefs.of(context)
        if (!prefs.getBoolean(KEY_TOLD, false)) return
        prefs.edit { remove(KEY_TOLD) }
        AfterCall.cancelProblem(context, NOTIFICATION_ID)
    }
}
