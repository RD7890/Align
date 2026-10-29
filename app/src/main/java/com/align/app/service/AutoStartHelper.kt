package com.align.app.service

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Opens the phone maker's own "Autostart" / "Auto-launch" / "Background activity" screen so the user
 * can allow Align to start again after it was swiped away or killed.
 *
 * There is no Android API for this. Every maker ships its own settings app with its own (unofficial)
 * screen names, so this keeps a list of the known ones per brand and tries them in order; if none
 * opens it falls back to Align's App info page. Android also gives no way to read the setting,
 * so "done" is something the user confirms. Component names collected from the open-source
 * AutoStarter / AppKillerManager projects.
 */
object AutoStartHelper {
    private const val TAG = "AutoStartHelper"

    enum class Family(val title: String, val steps: String) {
        XIAOMI(
            "Xiaomi · Redmi · POCO",
            "Switch Align ON in Autostart. Then Settings > Apps > Manage apps > Align > Battery saver > No restrictions, and lock the app in Recents."
        ),
        OPPO(
            "OPPO · ColorOS",
            "Turn ON \"Allow auto-launch\" for Align. Also Settings > Battery > Align > allow background activity."
        ),
        REALME(
            "realme · realme UI",
            "Turn ON \"Allow auto-launch\" for Align. Also Settings > Battery > Align > allow background activity."
        ),
        ONEPLUS(
            "OnePlus · OxygenOS",
            "Allow Align to auto-launch, and set its battery usage to \"Don't optimize\" / allow background activity."
        ),
        VIVO(
            "vivo · iQOO · Funtouch/Origin OS",
            "Allow Align in Autostart, and under Battery allow high background power consumption."
        ),
        HUAWEI(
            "Huawei · EMUI/HarmonyOS",
            "Turn OFF \"Manage automatically\" for Align and enable Auto-launch, Secondary launch and Run in background."
        ),
        HONOR(
            "Honor · MagicOS",
            "Turn OFF \"Manage automatically\" for Align and enable Auto-launch, Secondary launch and Run in background."
        ),
        SAMSUNG(
            "Samsung · One UI",
            "Add Align to \"Never sleeping apps\" and remove it from \"Sleeping apps\" / \"Deep sleeping apps\" (Battery > Background usage limits)."
        ),
        ASUS("ASUS · ZenUI", "Allow Align in Autostart."),
        NOKIA("Nokia · HMD", "Add Align to the power-saving exception list."),
        TRANSSION(
            "Tecno · Infinix · itel",
            "Allow Align in AutoStart (Phone Master / Settings > Apps)."
        ),
        LETV("LeEco · Letv", "Allow Align in Autoboot management."),
        GENERIC(
            "Your phone",
            "Allow Align to run in the background: App info > Battery > Unrestricted, and enable any \"Autostart\" or \"Background activity\" option you see."
        )
    }

    enum class Result { OEM_SCREEN, APP_SETTINGS, FAILED }

    private class Candidate(val pkg: String, val component: String? = null, val action: String? = null)

    /** Settings-app packages (declared in <queries> so we can see whether they are installed). */
    private val ROM_PACKAGES = mapOf(
        "com.miui.securitycenter" to Family.XIAOMI,
        "com.coloros.safecenter" to Family.OPPO,
        "com.oppo.safe" to Family.OPPO,
        "com.oneplus.security" to Family.ONEPLUS,
        "com.iqoo.secure" to Family.VIVO,
        "com.vivo.permissionmanager" to Family.VIVO,
        "com.huawei.systemmanager" to Family.HUAWEI,
        "com.samsung.android.lool" to Family.SAMSUNG,
        "com.asus.mobilemanager" to Family.ASUS,
        "com.evenwell.powersaving.g3" to Family.NOKIA,
        "com.transsion.phonemaster" to Family.TRANSSION,
        "com.letv.android.letvsafe" to Family.LETV
    )

    private val OPPO_FAMILY = listOf(
        Candidate("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        Candidate("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        Candidate("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        Candidate("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity")
    )

    private val CANDIDATES: Map<Family, List<Candidate>> = mapOf(
        Family.XIAOMI to listOf(
            Candidate("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Candidate("com.miui.securitycenter", action = "miui.intent.action.OP_AUTO_START")
        ),
        Family.OPPO to OPPO_FAMILY,
        Family.REALME to OPPO_FAMILY,
        Family.ONEPLUS to listOf(
            Candidate("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            Candidate("com.android.settings", action = "com.android.settings.action.BACKGROUND_OPTIMIZE")
        ) + OPPO_FAMILY,
        Family.VIVO to listOf(
            Candidate("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            Candidate("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Candidate("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
        ),
        Family.HUAWEI to listOf(
            Candidate("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Candidate("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            Candidate("com.huawei.systemmanager", action = "huawei.intent.action.HSM_PROTECTED_APPS")
        ),
        Family.HONOR to listOf(
            Candidate("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            Candidate("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        ),
        Family.SAMSUNG to listOf(
            Candidate("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"),
            Candidate("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            Candidate("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
        ),
        Family.ASUS to listOf(
            Candidate("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
            Candidate("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
        ),
        Family.NOKIA to listOf(
            Candidate("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity")
        ),
        Family.TRANSSION to listOf(
            Candidate("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")
        ),
        Family.LETV to listOf(
            Candidate("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
        )
    )

    /** Which maker's software this phone runs (by brand, then by installed settings app). */
    fun family(context: Context): Family {
        val id = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        val byBrand = when {
            "xiaomi" in id || "redmi" in id || "poco" in id || "blackshark" in id -> Family.XIAOMI
            "realme" in id -> Family.REALME
            "oneplus" in id -> Family.ONEPLUS
            "oppo" in id -> Family.OPPO
            "vivo" in id || "iqoo" in id -> Family.VIVO
            "honor" in id -> Family.HONOR
            "huawei" in id -> Family.HUAWEI
            "samsung" in id -> Family.SAMSUNG
            "asus" in id -> Family.ASUS
            "nokia" in id || "hmd" in id -> Family.NOKIA
            "tecno" in id || "infinix" in id || "itel" in id || "transsion" in id -> Family.TRANSSION
            "letv" in id || "leeco" in id -> Family.LETV
            else -> null
        }
        if (byBrand != null) return byBrand
        // Unknown brand (custom ROM, rebrand): look for a maker's settings app.
        val pm = context.packageManager
        for ((pkg, family) in ROM_PACKAGES) {
            if (isInstalled(pm, pkg)) return family
        }
        return Family.GENERIC
    }

    /** Opens the best matching screen. Must be called from the UI (needs a visible app to launch from). */
    fun open(context: Context): Result {
        val family = family(context)
        val pm = context.packageManager
        for (c in CANDIDATES[family].orEmpty()) {
            // Skip screens of settings apps this phone doesn't have (system "settings" always counts).
            if (c.pkg != "com.android.settings" && !isInstalled(pm, c.pkg)) continue
            if (tryStart(context, intentFor(c))) return Result.OEM_SCREEN
        }
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        return if (tryStart(context, details)) Result.APP_SETTINGS else Result.FAILED
    }

    private fun intentFor(c: Candidate): Intent = Intent().apply {
        if (c.component != null) {
            component = ComponentName(c.pkg, c.component)
        } else if (c.action != null) {
            action = c.action
            setPackage(c.pkg)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // ActivityNotFoundException / SecurityException (screen not exported on this ROM version)
            Log.d(TAG, "Could not open ${intent.component ?: intent.action}: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }
}
