package com.oscar.liquidlock

import android.content.Context

object Prefs {
    private const val FILE = "liquidlock"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MUSIC_ONLY = "music_only"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = sp(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_ENABLED, v).apply()

    /** Si está activo, la pantalla solo aparece cuando hay música sonando. */
    fun isMusicOnly(ctx: Context) = sp(ctx).getBoolean(KEY_MUSIC_ONLY, false)
    fun setMusicOnly(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_MUSIC_ONLY, v).apply()
}
