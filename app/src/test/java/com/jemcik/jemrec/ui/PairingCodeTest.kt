package com.jemcik.jemrec.ui

import androidx.test.core.app.ApplicationProvider
import com.jemcik.jemrec.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the reply field's text becomes before it is sent to adbd as a pairing code. */
class PairingCodeTest {

    private val app = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun sixDigitsPassThroughWhateverSeparatesThem() {
        assertEquals("123456", pairingCodeFrom("123456"))
        assertEquals("123456", pairingCodeFrom("123 456"))
        assertEquals("123456", pairingCodeFrom("12-34-56"))
        assertEquals("123456", pairingCodeFrom(" 123456\n"))
        assertEquals("123456", pairingCodeFrom("code: 123456"))
    }

    /** The service stopped at Android's cap: its own "timed out" words
     *  stand, not "Something went wrong: Job was cancelled" over them. */
    @Test fun aCancelledPairingSaysNothingOverTheTimeout() {
        assertNull(failureText(app, kotlinx.coroutines.CancellationException("Job was cancelled")))
        assertNull(failureText(app, kotlin.coroutines.cancellation.CancellationException("cut")))
        assertEquals(
            app.getString(R.string.pairing_something_wrong, "refused"),
            failureText(app, java.io.IOException("refused")),
        )
    }

    /** Out of time while pairing, connecting, or in setup's last step
     *  after the connect: each its own words. */
    @Test fun runningOutOfTimeSaysWhereItWas() {
        assertEquals(app.getString(R.string.pairing_timeout), timedOut(app, PairingService.Stage.PAIRING))
        assertEquals(app.getString(R.string.pairing_connect_timeout_full), timedOut(app, PairingService.Stage.CONNECTING))
        assertEquals(app.getString(R.string.pairing_setup_timeout_full), timedOut(app, PairingService.Stage.FINISHING))
    }

    @Test fun anythingButSixDigitsIsNotACode() {
        assertNull(pairingCodeFrom("12345"))
        assertNull(pairingCodeFrom("1234567"))
        assertNull(pairingCodeFrom(""))
        assertNull(pairingCodeFrom("abcdef"))
        assertNull(pairingCodeFrom(null))
    }
}