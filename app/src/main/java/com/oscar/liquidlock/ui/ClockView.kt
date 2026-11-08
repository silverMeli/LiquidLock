package com.oscar.liquidlock.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.oscar.liquidlock.ClockStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun fontFor(key: String): FontFamily = when (key) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

/** Color claro sacado de la carátula, para el reloj en modo "color de la canción". */
fun accentFrom(colors: List<Color>): Color {
    val c = colors.getOrElse(4) { Color.White }
    return if (c.luminance() < 0.45f) lerp(c, Color.White, 0.45f) else c
}

/** Estira verticalmente sin que se encime con lo de abajo (reloj alto tipo iOS 26). */
fun Modifier.stretchY(f: Float): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val h = (p.height * f).roundToInt()
    layout(p.width, h) {
        p.placeWithLayer(0, 0) {
            scaleY = f
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }
}

@Composable
fun ClockView(
    style: ClockStyle,
    accent: Color,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    alpha: Float = 1f,
    showDate: Boolean = true,
) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    val locale = Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(ctx)
    val pattern = (if (is24) "H:mm" else "h:mm") + if (style.seconds) ":ss" else ""
    val time = SimpleDateFormat(pattern, locale).format(now)
    val datePattern = if (locale.language == "es") "EEEE, d 'de' MMMM" else "EEEE, MMMM d"
    val date = SimpleDateFormat(datePattern, locale).format(now).replaceFirstChar { it.titlecase(locale) }

    val target = if (style.color == ClockStyle.ART_COLOR) accent else Color(style.color.toInt())
    val color by animateColorAsState(target, tween(1200), label = "clockColor")
    val shadow = Shadow(Color.Black.copy(alpha = 0.3f * alpha), Offset(0f, 4f), 20f)
    val family = fontFor(style.font)
    val size = (style.size * scale * if (style.seconds) 0.8f else 1f)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (showDate) {
            Text(
                date,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.9f * alpha),
                    fontSize = (19 * scale.coerceAtLeast(0.8f)).sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = shadow,
                ),
            )
        }
        Text(
            time,
            modifier = if (style.stretch) Modifier.stretchY(1.45f) else Modifier,
            style = TextStyle(
                // Efecto "vidrio": el color se desvanece hacia abajo
                brush = Brush.verticalGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0.72f * alpha))),
                fontSize = size.sp,
                lineHeight = (size * 1.05f).sp,
                fontFamily = family,
                fontWeight = FontWeight(style.weight.coerceIn(100, 900)),
                letterSpacing = (-size / 46f).sp,
                shadow = shadow,
            ),
        )
    }
}
