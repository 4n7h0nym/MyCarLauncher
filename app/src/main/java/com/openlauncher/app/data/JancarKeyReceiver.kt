package com.openlauncher.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Receives the Jancar steering-wheel key events while the launcher is visible. */
class JancarKeyReceiver(
    private val onKeyEvent: (keyId: Int, eventType: Int) -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEY_EVENT) return

        val extras = intent.extras
        if (extras == null || extras.isEmpty) {
            Log.d(TAG, "Aucun extra trouvé dans l'intent !")
        } else {
            extras.keySet().forEach { key ->
                Log.d(TAG, "Clé extra: $key | Valeur: ${extras.get(key)}")
            }
        }

        val keyId = intent.getIntExtra(EXTRA_KEY_EVENT_ID, INVALID_KEY_ID)
        val eventType = intent.getIntExtra(EXTRA_KEY_EVENT_TYPE, INVALID_EVENT_TYPE)
        if (keyId == INVALID_KEY_ID || eventType == INVALID_EVENT_TYPE) {
            Log.w(TAG, "Événement volant incomplet: ID=$keyId, type=$eventType")
            return
        }

        Log.d(TAG, "Touche reçue: id=$keyId, type=$eventType")
        onKeyEvent(keyId, eventType)
    }

    companion object {
        const val ACTION_KEY_EVENT = "com.jancar.services.action.key.event"
        const val EXTRA_KEY_EVENT_ID = "key_event_id"
        const val EXTRA_KEY_EVENT_TYPE = "key_event_type"

        private const val TAG = "JANCAR_KEYS"
        private const val INVALID_KEY_ID = Int.MIN_VALUE
        private const val INVALID_EVENT_TYPE = Int.MIN_VALUE
    }
}
