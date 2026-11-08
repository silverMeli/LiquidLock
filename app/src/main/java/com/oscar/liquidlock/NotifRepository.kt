package com.oscar.liquidlock

import android.app.PendingIntent
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Una notificación lista para mostrarse en la pantalla de bloqueo. */
data class LockNotif(
    val key: String,
    val pkg: String,
    val appName: String,
    val icon: Bitmap?,
    val title: String,
    val text: String,
    val postTime: Long,
    val intent: PendingIntent?,
    val clearable: Boolean,
    val autoCancel: Boolean,
    val secret: Boolean,
)

object NotifRepository {
    private val _state = MutableStateFlow<List<LockNotif>>(emptyList())
    val state: StateFlow<List<LockNotif>> = _state.asStateFlow()

    fun set(list: List<LockNotif>) {
        _state.value = list
    }

    fun dismiss(n: LockNotif) {
        if (!n.clearable) return
        _state.value = _state.value.filterNot { it.key == n.key }
        MediaListenerService.instance?.dismiss(n.key)
    }

    fun dismissAll() {
        val clearable = _state.value.filter { it.clearable }
        _state.value = _state.value.filterNot { it.clearable }
        clearable.forEach { MediaListenerService.instance?.dismiss(it.key) }
    }
}

/** Estado compartido entre el servicio y la pantalla para el modo Always On. */
object AodState {
    /** La pantalla AOD está visible ahora mismo. */
    @Volatile var aodVisible = false

    /** El próximo "pantalla apagada" lo causó el fin del AOD: no relanzar. */
    @Volatile var suppressNextOff = false

    /** Encendimos la pantalla nosotros para el AOD (no fue el usuario). */
    @Volatile var expectingAodWake = false
}
