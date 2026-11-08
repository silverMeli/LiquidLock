package com.oscar.liquidlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Vuelve a arrancar el servicio al reiniciar el teléfono o actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.isEnabled(context)) {
            try {
                LockService.start(context)
            } catch (_: Exception) {
            }
        }
    }
}
