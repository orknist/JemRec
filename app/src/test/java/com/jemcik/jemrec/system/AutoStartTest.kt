package com.jemcik.jemrec.system

import androidx.test.core.app.ApplicationProvider
import com.jemcik.jemrec.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the background-start card and notification tell each maker's users -
 * the screen's name and what to do on it - so a phone never gets another
 * maker's words. Honor's is measured; the rest follow dontkillmyapp.com (see
 * AutoStart.instructionFor).
 */
class AutoStartTest {

    private val app = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun anHonorIsToldManageManuallyOnAppLaunch() {
        assertEquals(app.getString(R.string.autostart_app_launch), AutoStart.labelFor(app, "com.hihonor.systemmanager"))
        assertEquals(
            app.getString(R.string.autostart_instruction_manage_manually),
            AutoStart.instructionFor(app, "com.hihonor.systemmanager",
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )
    }

    @Test fun huaweisOlderProtectedAppsScreenGetsItsOwnLine() {
        val line = AutoStart.instructionFor(app, "com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.process.ProtectActivity")
        assertEquals(app.getString(R.string.autostart_instruction_protected), line)
    }

    @Test fun switchPerAppScreensAreToldToTurnTheSwitchOn() {
        for (pkg in listOf("com.miui.securitycenter", "com.coloros.safecenter", "com.oppo.safe", "com.vivo.permissionmanager")) {
            assertEquals(pkg, app.getString(R.string.autostart_instruction_switch_on), AutoStart.instructionFor(app, pkg, null))
        }
        assertEquals(app.getString(R.string.autostart_autostart), AutoStart.labelFor(app, "com.miui.securitycenter"))
    }

    @Test fun aSamsungIsSentToNeverSleepingApps() {
        assertEquals(app.getString(R.string.autostart_battery), AutoStart.labelFor(app, "com.samsung.android.lool"))
        assertEquals(
            app.getString(R.string.autostart_instruction_samsung),
            AutoStart.instructionFor(app, "com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
    }

    @Test fun nobodyButHonorAndHuaweiHearsManageManually() {
        for (pkg in listOf("com.miui.securitycenter", "com.coloros.safecenter", "com.vivo.permissionmanager",
            "com.samsung.android.lool", "com.asus.mobilemanager", null)) {
            assertTrue(pkg.toString(), "Manage manually" !in AutoStart.instructionFor(app, pkg, null))
        }
    }

    @Test fun anUnknownScreenGetsTheGenericLine() {
        assertEquals(app.getString(R.string.autostart_instruction_generic), AutoStart.instructionFor(app, null, null))
        assertNull(AutoStart.labelFor(app, "com.asus.mobilemanager"))
    }

    @Test fun anUnknownScreensButtonReadsAsAButton() {
        // Not "the app's start-up settings", which is for the middle of
        // Settings' sentence: the card's button and step name it.
        val asus = AutoStart.screenFor(app, "com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")
        assertEquals(app.getString(R.string.autostart_startup_settings), asus.label)
        assertEquals(
            AutoStart.Screen(
                app.getString(R.string.autostart_app_launch),
                AutoStart.instructionFor(app, "com.hihonor.systemmanager", null),
            ),
            AutoStart.screenFor(app, "com.hihonor.systemmanager", null),
        )
    }
}