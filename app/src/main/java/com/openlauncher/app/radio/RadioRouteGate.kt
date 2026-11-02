package com.openlauncher.app.radio

import kotlinx.coroutines.delay

/** Bounded wait for asynchronous OEM switching; never issues a new open/seek. */
internal suspend fun awaitRadioRoute(
    readChannel: suspend () -> Int,
    onChannel: (Int) -> Unit = {},
    pause: suspend () -> Unit = { delay(100) },
    maxReads: Int = 20
): Int {
    require(maxReads > 0)
    var lastChannel: Int? = null
    repeat(maxReads) { attempt ->
        val channel = readChannel()
        lastChannel = channel
        onChannel(channel)
        if (channel == 11) return channel
        if (attempt < maxReads - 1) pause()
    }
    throw IllegalStateException("Canal RADIO non confirmé : attendu=11, observé=$lastChannel. Aucune commande de fréquence envoyée.")
}
