package com.oscar.liquidlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Servicio en primer plano. Al apagarse la pantalla decide qué mostrar:
 *  - Si el AOD está visible y se presiona el botón → despierta a la pantalla de bloqueo.
 *  - Si el AOD está activado → vuelve a encender la pantalla en modo AOD.
 *  - Si no → deja lista la pantalla de bloqueo para cuando el usuario la encienda.
 */
class LockService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (e: Exception) {
            Log.e("LiquidLock", "startForeground", e)
            stopSelf()
            return
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        addAnchorOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        addAnchorOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        anchor?.let {
            try {
                getSystemService(WindowManager::class.java)?.removeView(it)
            } catch (_: Exception) {
            }
        }
        anchor = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /* ----------------------------------------------------------------------- */

    private fun onScreenOff() {
        if (AodState.suppressNextOff) {
            AodState.suppressNextOff = false
            return
        }
        if (!Prefs.isEnabled(this)) return
        if (!Settings.canDrawOverlays(this)) return
        addAnchorOverlay()

        val playing = MediaRepository.state.value?.isPlaying == true

        // 1) Estaba el AOD y presionaron el botón: mostrar la pantalla de bloqueo
        if (AodState.aodVisible) {
            AodState.aodVisible = false
            AodState.expectingAodWake = false
            handler.postDelayed({ launch(LockActivity.MODE_WAKE) }, 150)
            return
        }

        // 2) Modo Always On
        if (Prefs.isAodEnabled(this) && batteryOk() && (!Prefs.isAodMusicOnly(this) || playing)) {
            AodState.expectingAodWake = true
            handler.postDelayed({ AodState.expectingAodWake = false }, 4000)
            handler.postDelayed({ launch(LockActivity.MODE_AOD) }, 350)
            return
        }

        // 3) Pantalla de bloqueo normal (aparece al encender)
        if (Prefs.isMusicOnly(this) && !playing) return
        launch(LockActivity.MODE_NORMAL)
    }

    private fun launch(mode: Int) {
        val wakes = mode != LockActivity.MODE_NORMAL
        if (wakes) wakeScreen()
        val i = Intent(this, LockActivity::class.java)
            .putExtra(LockActivity.EXTRA_MODE, mode)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        try {
            startActivity(i)
        } catch (e: Exception) {
            Log.e("LiquidLock", "launch", e)
        }
    }

    /** Respaldo para encender la pantalla en teléfonos que ignoran setTurnScreenOn. */
    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            val pm = getSystemService(PowerManager::class.java) ?: return
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "liquidlock:wake"
            )
            wl.acquire(1500)
        } catch (e: Exception) {
            Log.e("LiquidLock", "wake", e)
        }
    }

    /** No usar AOD con batería baja (salvo que esté cargando). */
    private fun batteryOk(): Boolean {
        val bm = getSystemService(BatteryManager::class.java) ?: return true
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return bm.isCharging || level < 0 || level > 15
    }

    /**
     * Ventanita invisible de 1x1 px. Desde Android 15, el permiso "mostrar sobre
     * otras apps" solo deja abrir pantallas desde segundo plano si la app tiene
     * una ventana superpuesta visible.
     */
    private var anchor: View? = null

    private fun addAnchorOverlay() {
        if (anchor != null || !Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.START or Gravity.TOP }
        try {
            wm.addView(v, lp)
            anchor = v
        } catch (_: Exception) {
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "Pantalla de bloqueo activa", NotificationManager.IMPORTANCE_MIN)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_lock_notif)
            .setContentTitle("LiquidLock activo")
            .setContentText("Toca para configurar")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL = "lock_service"
        private const val NOTIF_ID = 7

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, LockService::class.java))
            } catch (e: Exception) {
                Log.e("LiquidLock", "start", e)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LockService::class.java))
        }
    }
}
