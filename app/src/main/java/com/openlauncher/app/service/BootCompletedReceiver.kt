package com.openlauncher.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.openlauncher.app.MainActivity

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON"
            )) return

        val prefs = context.getSharedPreferences("launcher_runtime", Context.MODE_PRIVATE)
        val shouldLaunch = prefs.getBoolean("launch_at_boot", true)
        val lastDestination = prefs.getString("last_active_destination", "HOME") ?: "HOME"

        if (!shouldLaunch) return

        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("restore_destination", lastDestination)
                }
            )
            Log.i("BootCompletedReceiver", "Auto-started OpenLauncher successfully. Restoring destination: $lastDestination")
        }.onFailure { Log.w("BootCompletedReceiver", "Unable to open launcher after boot", it) }
    }
}
