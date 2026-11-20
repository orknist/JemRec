package com.jemcik.jemrec.ui

import androidx.test.core.app.ApplicationProvider
import com.jemcik.jemrec.answering.AnswerDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.Locale
import java.util.TimeZone

/** The answering machine's row in Settings, and why its switch may be off. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36])
class AnswerSummaryTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private val passed = AnswerDiagnostics.StageOne(permissions = true, inCallMusic = true, screening = false, describe = "")
    private val failed = passed.copy(inCallMusic = false)

    @Test fun theRowSaysWhatItDoes() {
        assertEquals("Off", answerSummary(ctx, AnswerUi()))
        assertEquals(
            "After 20 seconds - not tested on this phone yet",
            answerSummary(ctx, AnswerUi(enabled = true, stageOne = passed)),
        )
        assertEquals(
            "After 10 seconds",
            answerSummary(ctx, AnswerUi(enabled = true, delay = 10, results = mapOf("2G" to AnswerDiagnostics.Verdict.LIKELY))),
        )
        assertEquals("Not available on this phone", answerSummary(ctx, AnswerUi(stageOne = failed)))
        assertEquals("Paused after an emergency call", answerSummary(ctx, AnswerUi(enabled = true, pausedUntil = 1L)))
        // Off, it is just off.
        assertEquals("Off", answerSummary(ctx, AnswerUi(pausedUntil = 1L)))
    }

    /** Cleared by an update, it says so - not "not tested" - until a test is
     *  filed again. */
    @Test fun testsAnUpdateClearedAreSaidSo() {
        assertEquals(
            "After 20 seconds - test again after the update",
            answerSummary(ctx, AnswerUi(enabled = true, cleared = AnswerDiagnostics.Cleared.ROUTES)),
        )
        assertEquals(
            "After 20 seconds - test again after the update",
            answerSummary(ctx, 
                AnswerUi(enabled = true, results = mapOf("2G" to AnswerDiagnostics.Verdict.WORKS), cleared = AnswerDiagnostics.Cleared.VOLTE),
            ),
        )
        assertEquals("Not tested on this phone yet", testedSummary(AnswerUi(), res))
        assertEquals("Works on 2G", testedSummary(AnswerUi(results = mapOf("2G" to AnswerDiagnostics.Verdict.WORKS)), res))
        assertEquals(
            "Works on 2G. Your VoLTE test was cleared: before 0.31 a test over Wi-Fi Calling could be filed as VoLTE. Test again",
            testedSummary(AnswerUi(results = mapOf("2G" to AnswerDiagnostics.Verdict.WORKS), cleared = AnswerDiagnostics.Cleared.VOLTE), res),
        )
        assertEquals(
            "Your VoLTE test was cleared: before 0.31 a test over Wi-Fi Calling could be filed as VoLTE. Test again",
            testedSummary(AnswerUi(cleared = AnswerDiagnostics.Cleared.VOLTE), res),
        )
        assertEquals(
            "Your tests were cleared: since 0.31 the greeting's way to the caller follows the call. Test again",
            testedSummary(AnswerUi(cleared = AnswerDiagnostics.Cleared.ROUTES), res),
        )
    }

    /** What 1.0's step clears, from the stored preferences. */
    @Test fun theVolteStepClearsTheResultItsMarkAndItsQuestion() {
        assertEquals(
            listOf("answer_result_VoLTE", "answer_mixer_VoLTE", "answer_test"),
            AnswerDiagnostics.volteKeys(
                mapOf("answer_result_VoLTE" to "WORKS", "answer_mixer_VoLTE" to true, "answer_test" to "asking VoLTE", "answer_result_2G" to "WORKS"),
            ),
        )
        assertEquals(listOf<String>(), AnswerDiagnostics.volteKeys(mapOf("answer_result_2G" to "WORKS", "answer_test" to "asking 2G")))
        assertEquals(listOf<String>(), AnswerDiagnostics.volteKeys(mapOf("answer_test" to "armed 1 1793000000000")))
        assertEquals(listOf("answer_result_VoLTE"), AnswerDiagnostics.volteKeys(mapOf("answer_result_VoLTE" to "FAILS")))
    }

    @Test fun theSwitchWaitsForTheChecksTrackingAndAGreeting() {
        assertEquals("Run the checks below first", AnswerUi().blocked(res))
        assertEquals(failed.problemText(res), AnswerUi(stageOne = failed).blocked(res))
        assertEquals("Needs exact call tracking, in Settings", AnswerUi(stageOne = passed, callTracking = false).blocked(res))
        assertEquals("Record a greeting first", AnswerUi(stageOne = passed).blocked(res))
        assertNull(AnswerUi(stageOne = passed, greetingSeconds = 6.0).blocked(res))
    }

    @Test fun theCarriersTimerIsReadOnlyWhenOn() {
        assertEquals(25, AnswerUi(carrier = "on 25 *8250").carrierSeconds)
        assertNull(AnswerUi(carrier = "off 0").carrierSeconds)
        assertNull(AnswerUi(carrier = "error 3").carrierSeconds)
        assertNull(AnswerUi().carrierSeconds)
    }

    /**
     * The screen's times - a test call's "until", the end of a pause after an
     * emergency call - in Latin digits and English words, as the rest of the
     * app writes them: in the phone's language they came out in digits the
     * looks' fonts do not have (Arabic, Persian), or a month in another
     * language inside English text (Ukrainian).
     */
    @Test fun timesAreWrittenAsTheRestOfTheAppsAreInAnyLanguage() {
        val locale = Locale.getDefault()
        val zone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            for (language in listOf("ar", "fa", "uk")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                // 3 November 2026, 14:30 UTC.
                assertEquals(language, "14:30", timeOfDay(1_793_716_200_000L))
                assertEquals(language, "3 Nov at 14:30", pauseEnd(1_793_716_200_000L))
                // The greeting's take, counting.
                assertEquals(language, "12 s", takeSeconds(12_400L))
            }
        } finally {
            Locale.setDefault(locale)
            TimeZone.setDefault(zone)
        }
    }
}
