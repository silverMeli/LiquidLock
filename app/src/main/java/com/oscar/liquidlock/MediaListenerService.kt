package com.oscar.liquidlock

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService

/**
 * Con el permiso de "Acceso a notificaciones", Android nos deja ver las
 * sesiones de música activas (Spotify, YouTube Music, Deezer, etc.)
 * y controlarlas.
 */
class MediaListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private val tracked = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var selected: MediaSession.Token? = null

    // Caché de carátula para no decodificarla en cada actualización de posición
    private var artKey: String? = null
    private var artCache: Bitmap? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessionsChanged(list ?: emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val msm = getSystemService(MediaSessionManager::class.java) ?: return
        sessionManager = msm
        val me = ComponentName(this, MediaListenerService::class.java)
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, me, handler)
            onSessionsChanged(msm.getActiveSessions(me))
        } catch (_: SecurityException) {
            // El permiso aún no está concedido
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        tracked.values.forEach { (c, cb) -> c.unregisterCallback(cb) }
        tracked.clear()
        selected = null
        MediaRepository.publish(null, null)
    }

    private fun onSessionsChanged(controllers: List<MediaController>) {
        val newTokens = controllers.map { it.sessionToken }.toSet()

        // Quitar sesiones que ya no existen
        tracked.keys.filter { it !in newTokens }.forEach { token ->
            tracked.remove(token)?.let { (c, cb) -> c.unregisterCallback(cb) }
        }
        // Registrar las nuevas
        controllers.forEach { c ->
            if (c.sessionToken !in tracked) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                    override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                    override fun onSessionDestroyed() = refresh()
                }
                c.registerCallback(cb, handler)
                tracked[c.sessionToken] = c to cb
            }
        }
        refresh()
    }

    private fun MediaController.playing(): Boolean =
        playbackState?.state == PlaybackState.STATE_PLAYING ||
            playbackState?.state == PlaybackState.STATE_BUFFERING

    /** Elige la sesión que se mostrará: la que suena; si ninguna, la última elegida. */
    private fun refresh() {
        val all = tracked.values.map { it.first }
        val current = all.firstOrNull { it.sessionToken == selected }
        val chosen = when {
            current != null && current.playing() -> current
            else -> all.firstOrNull { it.playing() }
                ?: current
                ?: all.firstOrNull { it.metadata != null }
        }
        selected = chosen?.sessionToken
        MediaRepository.publish(chosen?.let { toNowPlaying(it) }, chosen)
    }

    private fun toNowPlaying(c: MediaController): NowPlaying? {
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val album = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val ps = c.playbackState

        val key = "${c.packageName}|$title|$artist|$album"
        if (key != artKey || artCache == null) {
            artKey = key
            artCache = loadArt(md)
        }

        return NowPlaying(
            title = title,
            artist = artist,
            album = album,
            art = artCache,
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = ps?.position ?: 0L,
            positionUpdatedAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            isPlaying = c.playing(),
            packageName = c.packageName,
        )
    }

    private fun loadArt(md: MediaMetadata): Bitmap? {
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: loadFromUri(
                md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            )
        return bmp?.let { downscale(it, 1080) }
    }

    private fun loadFromUri(uri: String?): Bitmap? {
        if (uri.isNullOrBlank()) return null
        return try {
            val u = Uri.parse(uri)
            if (u.scheme == "content" || u.scheme == "android.resource" || u.scheme == "file") {
                contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun downscale(src: Bitmap, max: Int): Bitmap {
        val big = maxOf(src.width, src.height)
        if (big <= max) return src
        val f = max.toFloat() / big
        return Bitmap.createScaledBitmap(src, (src.width * f).toInt(), (src.height * f).toInt(), true)
    }
}
