package com.oscar.liquidlock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oscar.liquidlock.ClockStyle
import com.oscar.liquidlock.MediaRepository
import com.oscar.liquidlock.NotifRepository
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Always On Display propio: fondo negro (en pantallas AMOLED no gasta),
 * brillo mínimo y todo se mueve un poquito cada minuto para no marcar la pantalla.
 * Doble toque = pantalla de bloqueo completa.
 */
@Composable
fun AodScreen(
    clock: ClockStyle,
    showNotifs: Boolean,
    onDoubleTap: () -> Unit,
) {
    val music by MediaRepository.state.collectAsStateWithLifecycle()
    val notifs by NotifRepository.state.collectAsStateWithLifecycle()
    val colors = rememberArtColors(music?.art)

    var shift by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            shift = IntOffset(Random.nextInt(-14, 15), Random.nextInt(-24, 25))
        }
    }
    val animShift by animateIntOffsetAsState(shift, tween(3000), label = "shift")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
                .offset(animShift.x.dp, animShift.y.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClockView(style = clock, accent = accentFrom(colors), scale = 0.85f, alpha = 0.85f)

            // Íconos de apps con notificaciones
            val apps = if (showNotifs) notifs.distinctBy { it.pkg }.take(6) else emptyList()
            if (apps.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    apps.forEach { n ->
                        Box(Modifier.graphicsLayer { alpha = 0.75f }) { AppIcon(n, 26.dp) }
                    }
                }
            }
        }

        // Música
        val m = music
        AnimatedVisibility(
            visible = m != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
                .offset(animShift.x.dp, -animShift.y.dp),
        ) {
            if (m != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    m.art?.let { art ->
                        val img = remember(art) { art.asImageBitmap() }
                        Image(
                            img, null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .graphicsLayer { alpha = 0.85f },
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.widthIn(max = 220.dp)) {
                        Text(
                            m.title, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            m.artist, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    EqualizerBars(playing = m.isPlaying, color = accentFrom(colors))
                }
            }
        }
    }
}

/** Barritas que "bailan" mientras suena la música. */
@Composable
fun EqualizerBars(playing: Boolean, color: Color) {
    val inf = rememberInfiniteTransition(label = "eq")
    val durations = listOf(520, 680, 430, 600)
    Row(
        Modifier.height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        durations.forEachIndexed { i, d ->
            val h by inf.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(d, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight(if (playing) h else 0.2f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.85f))
            )
        }
    }
}
