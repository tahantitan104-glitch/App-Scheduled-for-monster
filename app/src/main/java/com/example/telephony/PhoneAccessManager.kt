package com.example.telephony

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager

class PhoneAccessManager(private val context: Context) {

    fun resolveEssentialDialerPackages(): List<String> {
        val packages = linkedSetOf<String>()
        val telecomManager = runCatching {
            context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        }.getOrNull()
        val pm = context.packageManager

        runCatching { telecomManager?.defaultDialerPackage }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { packages.add(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { telecomManager?.systemDialerPackage }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { packages.add(it) }
        }

        runCatching {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            pm.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { packages.add(it) }

        // Dynamically resolve installed InCallService UI packages across OEMs
        runCatching {
            val inCallIntent = Intent("android.telecom.InCallService")
            pm.queryIntentServices(inCallIntent, PackageManager.MATCH_ALL)
                .mapNotNull { it.serviceInfo?.packageName }
                .filter { it.isNotBlank() }
        }.getOrDefault(emptyList()).forEach { packages.add(it) }

        // Include platform Telecom / Phone packages only if actually present on this device
        listOf("com.android.server.telecom", "com.android.phone", "com.android.incallui")
            .forEach { pkg ->
                if (isPackageInstalled(pm, pkg)) {
                    packages.add(pkg)
                }
            }

        return packages.toList()
    }

    fun resolveEmergencyPackages(): List<String> {
        val pm = context.packageManager
        return listOf("com.android.emergency", "com.android.phone", "com.android.server.telecom")
            .filter { isPackageInstalled(pm, it) }
    }

    fun launchSystemDialer(): Boolean {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching {
            pm.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }
}
