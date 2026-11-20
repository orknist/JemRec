package com.jemcik.jemrec.system

import android.content.Context
import androidx.annotation.StringRes
import com.jemcik.jemrec.R

/**
 * Whether setup's `pm grant` gave JemRec WRITE_SECURE_SETTINGS, and when it
 * did not, what the user can do about it.
 *
 * OPPO, OnePlus AND REALME PHONES REFUSE IT OUT OF THE BOX. Their system
 * (ColorOS, OxygenOS, realme UI) takes a list of permissions away from the
 * shell - the user an ADB session runs as - while the property
 * persist.sys.permission.enable is "true", which is how they ship. With it,
 * `pm grant` answers "Neither user 2000 nor current process has
 * android.permission.GRANT_RUNTIME_PERMISSIONS": issue #64 (Nord CE5,
 * OxygenOS 16, 2026-10-29) quoted it, and issue #61 (OnePlus 10R, Android 15,
 * 2026-10-28) got the Xiaomi advice this used to give everyone. No OnePlus
 * here; the rest is CallVault's, measured on a OnePlus 9 (OxygenOS 14) and a
 * OnePlus 12 (OxygenOS 16) on 2026-09-16: the property is "false" once the
 * Developer options switch is on, `pm grant`, `appops set` and `settings put`
 * from the shell all fail while it is "true", and a grant made earlier
 * outlives the switch going off again, and a restart.
 *
 * The switch is the last one in Developer options' Apps section: "Disable
 * system optimization" on ColorOS and OxygenOS 15 and 16, "Disable permission
 * monitoring" before. Some builds show it only in English (Shizuku issues
 * #374 and #2149, reported 2026-06 and 2026-07); CallVault saw it in Hebrew
 * on OxygenOS 16.1. Nothing an app or the shell can do gets past it while it
 * is off: the shell's own writes are refused too.
 *
 * WHETHER IT WAS GRANTED IS READ BACK, not taken from `pm grant` saying
 * nothing: a Xiaomi on HyperOS 3 was reported to CallVault as printing
 * nothing and granting nothing. This process cannot ask for itself - a
 * granted permission reaches it only once it restarts (Setup.isComplete) -
 * so the shell reads the package's record with dumpsys.
 */
internal object ShellGrant {

    /** "true" while an OPPO-family phone refuses the shell its grants,
     *  "false" once the switch is on; not set on other phones. */
    const val MONITORING_PROPERTY = "persist.sys.permission.enable"

    /** Debug builds only: act as an OnePlus with the switch off does, on a
     *  phone or emulator that grants. Nothing in the release build sets it. */
    @Volatile
    internal var actAsRefused = false

    /** What #64's phone printed, for [actAsRefused]. */
    internal const val REFUSAL =
        "Exception occurred while executing 'grant':\n" +
            "java.lang.SecurityException: grantRuntimePermission: Neither user 2000 nor current " +
            "process has android.permission.GRANT_RUNTIME_PERMISSIONS.\npm-exit=255"

    enum class Outcome {
        GRANTED,

        /** Android said no, with its SecurityException. */
        REFUSED,

        /** Nothing refused, and nothing granted either. */
        NOT_GRANTED,
    }

    /** The grant: its output and exit status, both on one stream. */
    fun grantCommand(packageName: String, permission: String): String =
        "pm grant $packageName $permission 2>&1; echo pm-exit=\$?"

    /** The read-back: the package's header line, so a dumpsys that ran can
     *  be told from one that did not, and every line naming the permission.
     *  Fixed strings (-F), as a package name is all regex dots. */
    fun readBackCommand(packageName: String, permission: String): String =
        "dumpsys package $packageName | grep -F -e 'Package [$packageName]' -e '${permission.substringAfterLast('.')}'"

    /**
     * [grant]: what [grantCommand] printed. [readBack]: what [readBackCommand]
     * printed for [permission], null if it could not be run.
     *
     * A read-back that names the package is the answer, either way. One that
     * does not - an Android whose dumpsys says it some other way - is no
     * answer, and then a grant that exited 0 counts, as it did before the
     * read-back: blocking setup on a phone that granted would be worse than
     * missing the one that only said it did.
     */
    fun outcome(packageName: String, permission: String, grant: String, readBack: String?): Outcome {
        val lines = readBack.orEmpty().lines().map { it.trim() }
        if (lines.any { it.contains("$permission: granted=true") }) return Outcome.GRANTED
        if (refused(grant)) return Outcome.REFUSED
        val listed = lines.any { it.startsWith("Package [$packageName]") }
        return when {
            listed -> Outcome.NOT_GRANTED
            exitStatus(grant) == 0 -> Outcome.GRANTED
            else -> Outcome.NOT_GRANTED
        }
    }

    /** The SecurityException every gate known so far throws for uid 2000. */
    fun refused(grant: String): Boolean =
        grant.contains("SecurityException") || grant.contains("GRANT_RUNTIME_PERMISSIONS")

    private fun exitStatus(grant: String): Int? =
        Regex("""pm-exit=(\d+)""").find(grant)?.groupValues?.get(1)?.toIntOrNull()

    /** [MONITORING_PROPERTY] as read: true while the switch is off, false
     *  once it is on, null where the phone does not have it or it could not
     *  be read. */
    fun monitoring(property: String?): Boolean? = when (property?.trim()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    /**
     * Whether this phone may be refusing the shell, so a card should name the
     * switch: setup's pairing card, for it to be turned on while the user is
     * in Developer options anyway rather than after the grant fails, and the
     * self-test's, where the recorder could not allow exact call tracking.
     * Only an OPPO, OnePlus or realme phone has it, and only there is the
     * property read: it is a getprop, and these run on screen refreshes.
     */
    fun switchMayBeOff(): Boolean =
        DeviceSkin.current() == DeviceSkin.OPLUS && switchMayBeOff(SystemProperty.read(MONITORING_PROPERTY))

    /** [switchMayBeOff] on an OPPO-family phone, from [MONITORING_PROPERTY]
     *  as this app reads it, if it may (sepolicy's call; "" if not): only a
     *  "false" there says the switch is on. */
    internal fun switchMayBeOff(property: String?): Boolean = monitoring(property) != false

    @StringRes
    fun switchRes(): Int = R.string.shell_grant_switch

    fun switchText(context: Context): String = context.getString(switchRes())

    /** Why setup did not finish, and what to do. [monitoring]: see [monitoring]. */
    fun advice(context: Context, outcome: Outcome, skin: DeviceSkin, monitoring: Boolean?): String {
        val what = context.getString(
            if (outcome == Outcome.REFUSED) R.string.shell_would_not_change else R.string.shell_no_permission,
        )
        val todo = when {
            monitoring == true || (monitoring == null && skin == DeviceSkin.OPLUS) ->
                context.getString(R.string.shell_turn_on_switch, context.getString(switchRes()))
            monitoring == false ->
                context.getString(R.string.shell_restart_phone) +
                    context.getString(R.string.shell_restart_phone_tail)
            skin == DeviceSkin.XIAOMI -> context.getString(R.string.shell_usb_security)
            else -> context.getString(R.string.shell_try_either_switch)
        }
        return "$what $todo"
    }
}