package com.oscar.liquidlock

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.oscar.liquidlock.ui.AodScreen
import com.oscar.liquidlock.ui.LockScreen

class LockActivity : ComponentActivity() {

    private val mode = mutableIntStateOf(MODE_NORMAL)
    private val handler = Handler(Looper.getMainLooper())
    private var proximityLock: PowerManager.WakeLock? = null
    private val aodTimeout = Runnable { endAod() }

    /** Si el usuario enciende la pantalla (después de que el AOD se apagó) → modo normal. */
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) return
            if (AodState.expectingAodWake) {
                AodState.expectingAodWake = false
                return
            }
            if (mode.intValue == MODE_AOD) applyMode(MODE_NORMAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        ContextCompat.registerReceiver(
            this, screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        applyMode(intent.getIntExtra(EXTRA_MODE, MODE_NORMAL))

        val clock = Prefs.clockStyle(this)
        val showNotifs = Prefs.showNotifications(this)
        val showContent = Prefs.showNotifContent(this)
        setContent {
            if (mode.intValue == MODE_AOD) {
                AodScreen(
                    clock = clock,
                    showNotifs = showNotifs,
                    onDoubleTap = { applyMode(MODE_NORMAL) },
                )
            } else {
                LockScreen(
                    clock = clock,
                    showNotifs = showNotifs,
                    showNotifContent = showContent,
                    onUnlock = { unlock(null) },
                    onOpenNotification = { unlock(it) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyMode(intent.getIntExtra(EXTRA_MODE, MODE_NORMAL))
    }

    override fun onStart() {
        super.onStart()
        AodState.aodVisible = mode.intValue == MODE_AOD
    }

    override fun onStop() {
        super.onStop()
        AodState.aodVisible = false
    }

    override fun onDestroy() {
        handler.removeCallbacks(aodTimeout)
        releaseProximity()
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (_: Exception) {
        }
        AodState.aodVisible = false
        super.onDestroy()
    }

    /* ------------------------------ Modos ------------------------------ */

    private fun applyMode(newMode: Int) {
        val aod = newMode == MODE_AOD
        val wakes = newMode != MODE_NORMAL
        if (Build.VERSION.SDK_INT >= 27) setTurnScreenOn(wakes) else legacyTurnScreenOn(wakes)
        mode.intValue = if (aod) MODE_AOD else MODE_NORMAL
        AodState.aodVisible = aod

        handler.removeCallbacks(aodTimeout)
        if (aod) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setBrightness(0.02f)
            val min = Prefs.aodTimeoutMin(this)
            if (min > 0) handler.postDelayed(aodTimeout, min * 60_000L)
            acquireProximity()
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            releaseProximity()
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyTurnScreenOn(on: Boolean) {
        val f = WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        if (on) window.addFlags(f) else window.clearFlags(f)
    }

    /** Se acabó el tiempo del AOD: dejamos que la pantalla se apague de verdad. */
    private fun endAod() {
        AodState.suppressNextOff = true
        AodState.aodVisible = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setBrightness(0f)
        releaseProximity()
    }

    private fun setBrightness(v: Float) {
        window.attributes = window.attributes.apply { screenBrightness = v }
    }

    /** En el bolsillo (sensor tapado) la pantalla se apaga sola. */
    private fun acquireProximity() {
        if (proximityLock != null) return
        try {
            val pm = getSystemService(PowerManager::class.java) ?: return
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            proximityLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "liquidlock:prox")
                .apply { acquire(6 * 60 * 60 * 1000L) }
        } catch (e: Exception) {
            Log.e("LiquidLock", "prox", e)
        }
    }

    private fun releaseProximity() {
        try {
            proximityLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        proximityLock = null
    }

    /* ---------------------------- Desbloqueo ---------------------------- */

    /** Desbloquea; si viene una notificación, la abre después. */
    private fun unlock(notif: LockNotif?) {
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = done(notif)
                override fun onDismissError() = finishQuietly()
                override fun onDismissCancelled() = recreate()
            })
        } else {
            done(notif)
        }
    }

    private fun done(notif: LockNotif?) {
        notif?.let { openNotification(it) }
        finishQuietly()
    }

    private fun openNotification(n: LockNotif) {
        val pi = n.intent ?: return
        try {
            val opts = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                opts.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            pi.send(this, 0, null, null, null, null, opts.toBundle())
            if (n.autoCancel) NotifRepository.dismiss(n)
        } catch (e: Exception) {
            Log.e("LiquidLock", "open notif", e)
        }
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_NORMAL = 0
        const val MODE_AOD = 1
        const val MODE_WAKE = 2 // pantalla normal, pero encendiendo la pantalla
    }
}
