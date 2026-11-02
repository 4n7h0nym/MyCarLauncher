package com.openlauncher.app.util

import android.content.Context
import android.media.AudioManager

object MediaVolumeHelper {
    /** User-triggered Android stream volume, without PCM gain or persistent forcing. */
    fun maximum(context:Context):Result<Pair<Int,Int>> = runCatching {
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        check(!audio.isVolumeFixed) { "Volume géré par le firmware de l’autoradio" }
        val maximum=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,maximum,AudioManager.FLAG_SHOW_UI)
        audio.getStreamVolume(AudioManager.STREAM_MUSIC) to maximum
    }
}
