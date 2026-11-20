package com.jemcik.jemrec.ui

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.jemcik.jemrec.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.Locale

/**
 * Phone locale picks Turkish, Ukrainian or English resources; any other
 * language falls back to the default (English) values/.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36])
class LocaleResourcesTest {

    private val app = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun resourcesFor(language: String) = app.createConfigurationContext(
        Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        },
    ).resources

    @Test fun turkishPhoneGetsTurkish() {
        val tr = resourcesFor("tr")
        assertEquals("Henüz kayıt yok", tr.getString(R.string.home_no_recordings))
        assertEquals("Bugün", tr.getString(R.string.day_today))
        assertEquals("Ara", tr.getString(R.string.chip_search))
        assertEquals("KAYIT HAZIR", tr.getString(R.string.deck_rec_ready))
        assertEquals("Ayarlar", tr.getString(R.string.screen_settings))
        assertEquals("Kayıtlar", tr.getString(R.string.recordings))
        assertEquals("Aramalar", tr.getString(R.string.calls))
        assertEquals("Telesekreter", tr.getString(R.string.screen_answering_machine))
        assertFalse(tr.getString(R.string.settings_recorder_running).contains("kayıtçı"))
        assertTrue(tr.getString(R.string.settings_recorder_running).contains("Kaydedici"))
    }

    @Test fun ukrainianPhoneGetsUkrainian() {
        val uk = resourcesFor("uk")
        assertEquals("Поки немає записів", uk.getString(R.string.home_no_recordings))
        assertEquals("Сьогодні", uk.getString(R.string.day_today))
        assertEquals("Пошук", uk.getString(R.string.chip_search))
        assertEquals("ЗАПИС ГОТОВИЙ", uk.getString(R.string.deck_rec_ready))
        assertEquals("Налаштування", uk.getString(R.string.screen_settings))
        assertEquals("Записи", uk.getString(R.string.recordings))
        assertEquals("Дзвінки", uk.getString(R.string.calls))
        assertEquals("Автовідповідач", uk.getString(R.string.screen_answering_machine))
        assertTrue(uk.getString(R.string.settings_recorder_running).contains("Записувач"))
        assertEquals("1 запис", uk.getQuantityString(R.plurals.day_heading_recordings, 1, 1))
        assertEquals("2 записи", uk.getQuantityString(R.plurals.day_heading_recordings, 2, 2))
        assertEquals("5 записів", uk.getQuantityString(R.plurals.day_heading_recordings, 5, 5))
    }

    @Test fun englishPhoneGetsEnglish() {
        assertEquals("No recordings yet", resourcesFor("en").getString(R.string.home_no_recordings))
        assertEquals("Today", resourcesFor("en").getString(R.string.day_today))
    }

    @Test fun germanFallsBackToEnglish() {
        assertEquals("No recordings yet", resourcesFor("de").getString(R.string.home_no_recordings))
        assertEquals("Today", resourcesFor("de").getString(R.string.day_today))
        assertEquals("Favourites", resourcesFor("de").getString(R.string.chip_favourites))
    }

    @Test fun everyTranslatableKeyHasATurkishValue() {
        assertNoBlank(resourcesFor("tr"), "tr")
    }

    @Test fun everyTranslatableKeyHasAUkrainianValue() {
        assertNoBlank(resourcesFor("uk"), "uk")
    }

    private fun assertNoBlank(res: android.content.res.Resources, tag: String) {
        for (field in R.string::class.java.fields) {
            if (field.name == "app_name") continue
            val id = field.getInt(null)
            assertFalse("blank $tag: ${field.name}", res.getString(id).isBlank())
        }
    }
}
