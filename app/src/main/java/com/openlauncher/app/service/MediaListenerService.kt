package com.openlauncher.app.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.app.Notification
import android.speech.tts.TextToSpeech
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.openlauncher.app.model.IncomingMessage
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.util.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MediaListenerService : NotificationListenerService() {

    private var messageTts: TextToSpeechManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        messageTts = TextToSpeechManager(this)
        messageTts?.setLanguage(voiceLanguageTag)
    }

    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshNowPlaying()
        override fun onMetadataChanged(metadata: MediaMetadata?)   = refreshNowPlaying()
        override fun onSessionDestroyed()                          = refreshNowPlaying()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isConnected.value = true
        refreshNowPlaying()
        runCatching { activeNotifications?.forEach(::captureMessage) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        isConnected.value = false
        clearController()
        _nowPlaying.value = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshNowPlaying()
        sbn?.let(::captureMessage)
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (_incomingMessage.value?.key == sbn?.key) _incomingMessage.value = null
        refreshNowPlaying()
    }

    override fun onDestroy() {
        instance = null
        clearController()
        // Clear the static flow so the UI doesn't keep showing a dead session
        // (and pinning its album-art bitmap) after the service is killed
        _nowPlaying.value = null
        messageTts?.close()
        messageTts = null
        super.onDestroy()
    }

    private fun captureMessage(sbn: StatusBarNotification) {
        if (sbn.isOngoing || sbn.packageName !in MESSAGE_PACKAGES) return
        val extras = sbn.notification.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val content = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (sender.isBlank() || content.isBlank() || content == sender) return
        val message = IncomingMessage(sbn.key, sender, content, sbn.packageName)
        _incomingMessage.value = message
        if (announceIncomingMessages) messageTts?.speak(messageAnnouncement(sender, content))
    }

    private fun readLatestMessage() {
        _incomingMessage.value?.let { messageTts?.speak(messageAnnouncement(it.sender, it.content)) }
    }

    private fun messageAnnouncement(sender: String, content: String): String =
        if (voiceLanguageTag.startsWith("en")) "Message from $sender: $content" else "Message de $sender : $content"

    private fun clearController() {
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
    }

    private fun refreshNowPlaying() {
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val sessions: List<MediaController> = try {
            msm.getActiveSessions(ComponentName(this, MediaListenerService::class.java))
        } catch (_: SecurityException) {
            emptyList()
        }

        val active = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()

        if (active == null) {
            clearController()
            _nowPlaying.value = null
            return
        }

        // getActiveSessions returns NEW MediaController instances on every call —
        // compare session tokens, not references, or we churn callbacks and
        // recompose the UI on every notification system-wide.
        if (active.sessionToken != activeController?.sessionToken) {
            clearController()
            activeController = active
            active.registerCallback(
                controllerCallback,
                android.os.Handler(android.os.Looper.getMainLooper())
            )
        }

        updateFromController(activeController)
    }

    private fun updateFromController(controller: MediaController?) {
        if (controller == null) { _nowPlaying.value = null; return }
        val meta = controller.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Unknown"
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        // Prefer the largest bitmap the source provides; many apps put a
        // downscaled image in ALBUM_ART and the full one in ART (or vice versa)
        val art = try {
            listOfNotNull(
                meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
                meta?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ).maxByOrNull { it.width * it.height }
        } catch (_: Exception) { null }
        val artUri = meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        // Skip redundant emissions: metadata bitmaps parcel into fresh instances on
        // every read, so a plain data-class compare would never match and every
        // notification would force a recomposition + art redraw.
        val prev = _nowPlaying.value
        if (prev != null &&
            prev.controller?.sessionToken == controller.sessionToken &&
            prev.title == title && prev.artist == artist &&
            prev.isPlaying == isPlaying && prev.artUri == artUri &&
            (prev.albumArt != null) == (art != null)
        ) return

        _nowPlaying.value = NowPlayingState(
            title      = title,
            artist     = artist,
            albumArt   = art,
            artUri     = artUri,
            isPlaying  = isPlaying,
            controller = controller
        )
    }

    companion object {
        private val _nowPlaying = MutableStateFlow<NowPlayingState?>(null)
        val nowPlaying: StateFlow<NowPlayingState?> = _nowPlaying
        val isConnected = MutableStateFlow(false)
        private val _incomingMessage = MutableStateFlow<IncomingMessage?>(null)
        val incomingMessage: StateFlow<IncomingMessage?> = _incomingMessage
        @Volatile var announceIncomingMessages: Boolean = false
        @Volatile var voiceLanguageTag: String = "fr-CA"
            set(value) {
                field = value
                instance?.messageTts?.setLanguage(value)
            }
        private val MESSAGE_PACKAGES = setOf(
            "com.google.android.apps.messaging", "com.android.messaging", "com.whatsapp",
            "org.telegram.messenger", "com.facebook.orca", "com.viber.voip", "com.signal"
        )

        @Volatile private var instance: MediaListenerService? = null
        fun requestRefresh() { instance?.refreshNowPlaying() }
        fun readIncomingMessage() { instance?.readLatestMessage() }
        fun dismissIncomingMessage() {
            val key = _incomingMessage.value?.key
            val service = instance
            if (key != null && service != null) runCatching { service.cancelNotification(key) }
            _incomingMessage.value = null
        }
    }
}
