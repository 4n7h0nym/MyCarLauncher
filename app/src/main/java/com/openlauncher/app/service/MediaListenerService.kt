package com.openlauncher.app.service

import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.app.Notification
import android.speech.tts.TextToSpeech
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        refreshNowPlaying()
    }
    private var broadcastsRegistered = false
    private var broadcastTitle: String? = null
    private var broadcastArtist: String? = null
    private var broadcastPlaying: Boolean? = null
    private val mediaBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val title = intent.getStringExtra("track")
                ?: intent.getStringExtra("title")
                ?: intent.getStringExtra("TITLE")
            val artist = intent.getStringExtra("artist")
                ?: intent.getStringExtra("ARTIST")
            val playing = when (val value = intent.extras?.get("playing") ?: intent.extras?.get("state")) {
                is Boolean -> value
                is Int -> value == PlaybackState.STATE_PLAYING || value == 1
                is String -> value.equals("playing", ignoreCase = true) || value == "1" || value.equals("play", ignoreCase = true)
                else -> null
            }
            if (!title.isNullOrBlank()) broadcastTitle = title.trim()
            if (!artist.isNullOrBlank()) broadcastArtist = artist.trim()
            if (playing != null) broadcastPlaying = playing
            refreshNowPlaying()
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshNowPlaying()
        override fun onMetadataChanged(metadata: MediaMetadata?)   = refreshNowPlaying()
        override fun onSessionDestroyed()                          = refreshNowPlaying()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isConnected.value = true
        registerMediaBroadcasts()
        registerSessionsListener()
        refreshNowPlaying()
        runCatching { activeNotifications?.forEach(::captureMessage) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        isConnected.value = false
        unregisterSessionsListener()
        unregisterMediaBroadcasts()
        clearController()
        broadcastTitle = null
        broadcastArtist = null
        broadcastPlaying = null
        _nowPlaying.value = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshNowPlaying()
        sbn?.let {
            captureMessage(it)
            captureMediaArtwork(it)
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (_incomingMessage.value?.key == sbn?.key) _incomingMessage.value = null
        refreshNowPlaying()
    }

    override fun onDestroy() {
        instance = null
        unregisterSessionsListener()
        unregisterMediaBroadcasts()
        clearController()
        broadcastTitle = null
        broadcastArtist = null
        broadcastPlaying = null
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

    private fun captureMediaArtwork(sbn: StatusBarNotification) {
        val current = _nowPlaying.value ?: return
        if (current.albumArt != null || current.controller?.packageName != sbn.packageName) return
        val extras = sbn.notification.extras
        @Suppress("DEPRECATION")
        val artwork = runCatching {
            extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)
                ?: extras.getParcelable(Notification.EXTRA_PICTURE)
                ?: extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG)
        }.getOrNull() ?: return
        _nowPlaying.value = current.copy(albumArt = artwork)
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

    private fun registerSessionsListener() {
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        sessionManager = manager
        runCatching {
            manager.addOnActiveSessionsChangedListener(
                sessionsListener,
                ComponentName(this, MediaListenerService::class.java),
                mainHandler
            )
        }
    }

    private fun unregisterSessionsListener() {
        sessionManager?.let { manager -> runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) } }
        sessionManager = null
    }

    private fun registerMediaBroadcasts() {
        if (broadcastsRegistered) return
        val filter = IntentFilter().apply {
            addAction("com.android.music.metachanged")
            addAction("com.android.music.playstatechanged")
            addAction("com.android.music.playbackcomplete")
            addAction("com.android.music.queuechanged")
            addAction("com.android.bluetooth.music.metachanged")
            addAction("com.android.bluetooth.music.playstatechanged")
        }
        runCatching {
            ContextCompat.registerReceiver(this, mediaBroadcastReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            broadcastsRegistered = true
        }
    }

    private fun unregisterMediaBroadcasts() {
        if (!broadcastsRegistered) return
        runCatching { unregisterReceiver(mediaBroadcastReceiver) }
        broadcastsRegistered = false
    }

    private fun refreshNowPlaying() {
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val sessions: List<MediaController> = try {
            msm.getActiveSessions(ComponentName(this, MediaListenerService::class.java))
        } catch (_: SecurityException) {
            emptyList()
        }

        val active = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.metadata != null }
            ?: sessions.firstOrNull()

        if (active == null) {
            clearController()
            val title = broadcastTitle
            _nowPlaying.value = if (!title.isNullOrBlank()) {
                NowPlayingState(
                    title = title,
                    artist = broadcastArtist.orEmpty(),
                    albumArt = null,
                    artUri = null,
                    isPlaying = broadcastPlaying == true,
                    controller = null
                )
            } else null
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
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?.takeIf(String::isNotBlank)
            ?: broadcastTitle
            ?: "Unknown"
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?.takeIf(String::isNotBlank)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?.takeIf(String::isNotBlank)
            ?: broadcastArtist
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
            || (controller.playbackState == null && broadcastPlaying == true)

        // Skip redundant emissions: metadata bitmaps parcel into fresh instances on
        // every read, so a plain data-class compare would never match and every
        // notification would force a recomposition + art redraw.
        val prev = _nowPlaying.value
        val resolvedArt = art ?: prev?.albumArt?.takeIf {
            prev.controller?.sessionToken == controller.sessionToken
        }
        if (prev != null &&
            prev.controller?.sessionToken == controller.sessionToken &&
            prev.title == title && prev.artist == artist &&
            prev.isPlaying == isPlaying && prev.artUri == artUri &&
            (prev.albumArt != null) == (resolvedArt != null)
        ) return

        _nowPlaying.value = NowPlayingState(
            title      = title,
            artist     = artist,
            albumArt   = resolvedArt,
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
