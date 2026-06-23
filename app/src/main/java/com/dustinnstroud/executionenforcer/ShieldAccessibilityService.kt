package com.dustinnstroud.executionenforcer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class ShieldAccessibilityService : AccessibilityService() {
    private val ignoredPackages = setOf(
        "com.dustinnstroud.executionenforcer",
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.settings",
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName in ignoredPackages) return

        val settings = SettingsStore(this).load()
        if (packageName !in settings.blockedPackages) return

        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_INTERVENTION_PACKAGE, packageName)
        startActivity(intent)
    }

    override fun onInterrupt() = Unit
}
