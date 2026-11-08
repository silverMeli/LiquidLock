package com.oscar.liquidlock

import android.app.Notification
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.BundleCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Con el permiso de "Acceso a notificaciones", Android nos deja:
 *  - ver y controlar las sesiones de música (Spotify, YouTube Music, etc.)
 *  - leer las notificaciones para mostrarlas en la pantalla de bloqueo
 */
class MediaListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var sessionManager: MediaSessionManager? = null
    private val tracked = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var selected: MediaSession.Token? = null

    // Caché de carátula
    private var artKey: String? = null
    private var artCache: Bitmap? = null
    private var hiResRequested: String? = null

    // Caché de íconos de apps
    private val iconCache = mutableMapOf<String, Bitmap?>()
    private val nameCache = mutableMapOf<String, String>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessionsChanged(list ?: emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        val msm = getSystemService(MediaSessionManager::class.java) ?: return
        sessionManager = msm
        val me = ComponentName(this, MediaListenerService::class.java)
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, me, handler)
            onSessionsChanged(msm.getActiveSessions(me))
        } catch (_: SecurityException) {
        }
        reloadNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) instance = null
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        tracked.values.forEach { (c, cb) -> c.unregisterCallback(cb) }
        tracked.clear()
        selected = null
        MediaRepository.publish(null, null)
        NotifRepository.set(emptyList())
    }

    /* ------------------------------ Notificaciones ------------------------------ */

    override fun onNotificationPosted(sbn: StatusBarNotification?) = reloadNotifications()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = reloadNotifications()

    fun dismiss(key: String) {
        try {
            cancelNotification(key)
        } catch (_: Exception) {
        }
    }

    private fun reloadNotifications() {
        try {
            val list = (activeNotifications ?: emptyArray())
                .mapNotNull { toLockNotif(it) }
                .sortedByDescending { it.postTime }
                .take(40)
            NotifRepository.set(list)
        } catch (e: Exception) {
            Log.e("LiquidLock", "notifs", e)
        }
    }

    private fun toLockNotif(sbn: StatusBarNotification): LockNotif? {
        if (sbn.packageName == packageName) return null
        val n = sbn.notification ?: return null
        val skipFlags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or
            Notification.FLAG_GROUP_SUMMARY
        if (n.flags and skipFlags != 0) return null
        val ex = n.extras ?: return null
        if (ex.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true) return null

        val title = (ex.getCharSequence(Notification.EXTRA_TITLE)
            ?: ex.getCharSequence(Notification.EXTRA_TITLE_BIG))?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null

        val pkg = sbn.packageName
        val ai: ApplicationInfo? = BundleCompat.getParcelable(ex, "android.appInfo", ApplicationInfo::class.java)
            ?: try {
                packageManager.getApplicationInfo(pkg, 0)
            } catch (_: Exception) {
                null
            }
        val name = nameCache.getOrPut(pkg) {
            try {
                ai?.let { packageManager.getApplicationLabel(it).toString() } ?: pkg
            } catch (_: Exception) {
                pkg
            }
        }
        val icon = iconCache.getOrPut(pkg) {
            try {
                ai?.let { packageManager.getApplicationIcon(it).toBitmap(96, 96) }
            } catch (_: Exception) {
                null
            }
        }

        return LockNotif(
            key = sbn.key,
            pkg = pkg,
            appName = name,
            icon = icon,
            title = title,
            text = text,
            postTime = sbn.postTime,
            intent = n.contentIntent,
            clearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            secret = n.visibility == Notification.VISIBILITY_SECRET,
        )
    }

    /* --------------------------------- Música ---------------------------------- */

    private fun onSessionsChanged(controllers: List<MediaController>) {
        try {
            val newTokens = controllers.map { it.sessionToken }.toSet()
            tracked.keys.filter { it !in newTokens }.forEach { token ->
                tracked.remove(token)?.let { (c, cb) -> c.unregisterCallback(cb) }
            }
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
        } catch (e: Exception) {
            Log.e("LiquidLock", "sessions", e)
        }
    }

    private fun MediaController.playing(): Boolean =
        playbackState?.state == PlaybackState.STATE_PLAYING ||
            playbackState?.state == PlaybackState.STATE_BUFFERING

    private fun refresh() {
        try {
            val all = tracked.values.map { it.first }
            val current = all.firstOrNull { it.sessionToken == selected }
            val chosen = when {
                current != null && current.playing() -> current
                else -> all.firstOrNull { it.playing() } ?: current ?: all.firstOrNull { it.metadata != null }
            }
            selected = chosen?.sessionToken
            MediaRepository.publish(chosen?.let { toNowPlaying(it) }, chosen)
        } catch (e: Exception) {
            Log.e("LiquidLock", "refresh", e)
        }
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
        if (key != artKey) {
            artKey = key
            artCache = loadEmbeddedArt(md)
            hiResRequested = null
        }
        // Si la carátula viene pequeña, intentamos bajar una versión en alta calidad
        val small = artCache == null || maxOf(artCache!!.width, artCache!!.height) < 900
        if (small && hiResRequested != key) {
            hiResRequested = key
            val uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            fetchHiRes(key, uri)
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

    private fun loadEmbeddedArt(md: MediaMetadata): Bitmap? = try {
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        bmp?.let { downscale(toSoftware(it), 2048) }
    } catch (_: Exception) {
        null
    }

    /** Descarga la carátula en alta resolución (en segundo plano). */
    private fun fetchHiRes(key: String, raw: String?) {
        if (raw.isNullOrBlank()) return
        io.execute {
            val bmp = try {
                val u = Uri.parse(raw)
                if (u.scheme == "content" || u.scheme == "file" || u.scheme == "android.resource") {
                    contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
                } else {
                    hiResUrl(raw)?.let { download(it) }
                }
            } catch (_: Exception) {
                null
            }?.let { downscale(toSoftware(it), 2048) }

            if (bmp != null) handler.post {
                val cur = artCache
                if (artKey == key && (cur == null || bmp.width > cur.width)) {
                    artCache = bmp
                    refresh()
                }
            }
        }
    }

    /** Pide la imagen más grande que ofrece cada servicio. */
    private fun hiResUrl(raw: String): String? {
        var u = raw
        if (u.startsWith("spotify:image:")) u = "https://i.scdn.co/image/" + u.removePrefix("spotify:image:")
        if (!u.startsWith("http")) return null
        if ("i.scdn.co/image/" in u) {
            u = u.replace("ab67616d00001e02", "ab67616d0000b273").replace("ab67616d00004851", "ab67616d0000b273")
        }
        // Google / YouTube Music: ...=w226-h226 → =w1400-h1400
        u = Regex("=w\\d+-h\\d+").replace(u, "=w1400-h1400")
        u = Regex("=s\\d+(-|$)").replace(u) { "=s1400" + it.groupValues[1] }
        return u
    }

    private fun download(url: String): Bitmap? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            if (conn.responseCode in 200..299) conn.inputStream.use { BitmapFactory.decodeStream(it) } else null
        } finally {
            conn.disconnect()
        }
    }

    /** Las imágenes "HARDWARE" no se pueden leer ni redimensionar: se copian. */
    private fun toSoftware(b: Bitmap): Bitmap =
        if (Build.VERSION.SDK_INT >= 26 && b.config == Bitmap.Config.HARDWARE) {
            b.copy(Bitmap.Config.ARGB_8888, false)
        } else b

    private fun downscale(src: Bitmap, max: Int): Bitmap {
        val big = maxOf(src.width, src.height)
        if (big <= max) return src
        val f = max.toFloat() / big
        return Bitmap.createScaledBitmap(src, (src.width * f).toInt(), (src.height * f).toInt(), true)
    }

    companion object {
        @Volatile
        var instance: MediaListenerService? = null
    }
}
