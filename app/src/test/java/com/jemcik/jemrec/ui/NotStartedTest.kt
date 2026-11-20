package com.jemcik.jemrec.ui

import androidx.test.core.app.ApplicationProvider
import com.jemcik.jemrec.recorder.RecorderHealth.Problem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** What Start recorder says when its revive could not start the recorder
 *  (notStarted): a reason for each cause the revive can leave, and one for
 *  none kept. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36])
class NotStartedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun eachCauseHasItsWords() {
        assertEquals(
            mapOf(
                Problem.NEEDS_WIFI to "it needs Wi-Fi for a few seconds, or Start without Wi-Fi's port",
                Problem.NETWORK_NOT_ALLOWED to "Android has not allowed Wireless debugging on this Wi-Fi",
                Problem.CANNOT_CONNECT to "JemRec cannot connect to Wireless debugging - the pairing may be gone",
                Problem.CANNOT_START to "it would not start on this network",
                null to "JemRec is off, or setup has not finished",
            ),
            (Problem.entries + null).associateWith { notStarted(context, it) },
        )
    }
}
