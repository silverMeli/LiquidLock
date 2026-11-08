package com.oscar.liquidlock.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Colores por defecto cuando no hay música (azul/morado tipo iOS). */
val DefaultColors = listOf(
    Color(0xFF3A3DD9),
    Color(0xFF1B1464),
    Color(0xFF8E2DE2),
    Color(0xFF0B0B2B),
    Color(0xFFFF5E9C),
)

/** Saca 5 colores de la carátula, como hace Apple Music para su fondo animado. */
@Composable
fun rememberArtColors(art: Bitmap?): List<Color> {
    var colors by remember { mutableStateOf(DefaultColors) }
    LaunchedEffect(art) {
        colors = if (art == null) DefaultColors else withContext(Dispatchers.Default) {
            try {
                val p = Palette.from(art).maximumColorCount(16).generate()
                val fb = p.getDominantColor(0xFF2A2A3A.toInt())
                listOf(
                    p.getVibrantColor(fb),
                    p.getDarkVibrantColor(fb),
                    p.getMutedColor(fb),
                    p.getDarkMutedColor(fb),
                    p.getLightVibrantColor(fb),
                ).map { Color(it) }
            } catch (_: Exception) {
                DefaultColors
            }
        }
    }
    return colors
}

/**
 * Fondo "líquido": manchas de color que se mueven lentamente y se mezclan.
 * Se mueve más rápido cuando hay música sonando y casi se detiene en pausa.
 */
@Composable
fun LiquidBackground(
    colors: List<Color>,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val speed by animateFloatAsState(if (playing) 1f else 0.12f, tween(1500), label = "speed")
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                time += (now - last) / 1_000_000_000f * speed
                last = now
            }
        }
    }

    val c = List(5) { i ->
        animateColorAsState(colors.getOrElse(i) { DefaultColors[i] }, tween(1800), label = "c$i").value
    }
    val base = animateColorAsState(colors.getOrElse(3) { Color.Black }.darken(0.55f), tween(1800), label = "base").value

    // Parámetros de movimiento distintos para cada mancha
    val freqs = remember { listOf(0.21f to 0.17f, 0.13f to 0.23f, 0.27f to 0.11f, 0.09f to 0.19f, 0.17f to 0.29f) }
    val phases = remember { listOf(0f to 1f, 2f to 0.5f, 4f to 3f, 1.5f to 5f, 3.3f to 2.2f) }

    Canvas(modifier) {
        drawRect(base)
        val r = max(size.width, size.height) * 0.62f
        for (i in 0 until 5) {
            val (fx, fy) = freqs[i]
            val (px, py) = phases[i]
            val center = Offset(
                size.width * (0.5f + 0.42f * sin(time * fx * 6.28f + px)),
                size.height * (0.5f + 0.38f * cos(time * fy * 6.28f + py)),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c[i].copy(alpha = 0.85f), c[i].copy(alpha = 0f)),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
        // Velo oscuro para que el texto blanco siempre se lea
        drawRect(Color.Black.copy(alpha = 0.18f))
    }
}

fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
