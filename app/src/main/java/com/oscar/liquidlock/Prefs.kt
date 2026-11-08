package com.oscar.liquidlock

import android.content.Context

/** Estilo del reloj que el usuario puede editar. */
data class ClockStyle(
    val font: String = "sans",      // sans, serif, mono, cursive
    val weight: Int = 700,          // 200..900
    val size: Int = 92,             // sp
    val color: Long = 0xFFFFFFFF,   // ARGB; ART_COLOR = color de la carátula
    val stretch: Boolean = false,   // reloj alto estilo iOS 26
    val seconds: Boolean = false,
) {
    companion object {
        const val ART_COLOR = 0L
    }
}

object Prefs {
    private const val FILE = "liquidlock"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Pantalla de bloqueo
    fun isEnabled(ctx: Context) = sp(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("enabled", v).apply()

    fun isMusicOnly(ctx: Context) = sp(ctx).getBoolean("music_only", false)
    fun setMusicOnly(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("music_only", v).apply()

    // Always On Display propio
    fun isAodEnabled(ctx: Context) = sp(ctx).getBoolean("aod", false)
    fun setAodEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("aod", v).apply()

    /** Minutos que dura el AOD antes de apagarse de verdad. 0 = sin límite. */
    fun aodTimeoutMin(ctx: Context) = sp(ctx).getInt("aod_timeout", 10)
    fun setAodTimeoutMin(ctx: Context, v: Int) = sp(ctx).edit().putInt("aod_timeout", v).apply()

    fun isAodMusicOnly(ctx: Context) = sp(ctx).getBoolean("aod_music_only", false)
    fun setAodMusicOnly(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("aod_music_only", v).apply()

    // Notificaciones
    fun showNotifications(ctx: Context) = sp(ctx).getBoolean("notifs", true)
    fun setShowNotifications(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("notifs", v).apply()

    fun showNotifContent(ctx: Context) = sp(ctx).getBoolean("notif_content", true)
    fun setShowNotifContent(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("notif_content", v).apply()

    // Reloj
    fun clockStyle(ctx: Context): ClockStyle {
        val p = sp(ctx)
        return ClockStyle(
            font = p.getString("clk_font", "sans") ?: "sans",
            weight = p.getInt("clk_weight", 700),
            size = p.getInt("clk_size", 92),
            color = p.getLong("clk_color", 0xFFFFFFFF),
            stretch = p.getBoolean("clk_stretch", false),
            seconds = p.getBoolean("clk_seconds", false),
        )
    }

    fun setClockStyle(ctx: Context, s: ClockStyle) {
        sp(ctx).edit()
            .putString("clk_font", s.font)
            .putInt("clk_weight", s.weight)
            .putInt("clk_size", s.size)
            .putLong("clk_color", s.color)
            .putBoolean("clk_stretch", s.stretch)
            .putBoolean("clk_seconds", s.seconds)
            .apply()
    }
}
