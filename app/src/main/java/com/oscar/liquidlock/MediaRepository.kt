package com.oscar.liquidlock

import android.graphics.Bitmap
import android.media.session.MediaController
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lo que está sonando ahora mismo en cualquier app de música. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val durationMs: Long,
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() de cuando se tomó positionMs */
    val positionUpdatedAt: Long,
    val speed: Float,
    val isPlaying: Boolean,
    val packageName: String,
) {
    /** Clave para detectar cambio de canción (y animar la carátula). */
    val trackKey: String get() = "$packageName|$title|$artist|$album"

    fun currentPosition(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        val p = if (isPlaying) positionMs + ((nowElapsed - positionUpdatedAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** Puente entre el NotificationListener y la interfaz. */
object MediaRepository {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    @Volatile
    private var controller: MediaController? = null

    fun publish(np: NowPlaying?, controller: MediaController?) {
        this.controller = controller
        _state.value = np
    }

    fun playPause() {
        val c = controller ?: return
        if (_state.value?.isPlaying == true) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms)
    }
}
