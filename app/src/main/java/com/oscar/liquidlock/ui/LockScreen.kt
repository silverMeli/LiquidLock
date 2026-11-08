package com.oscar.liquidlock.ui

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oscar.liquidlock.MediaRepository
import com.oscar.liquidlock.NowPlaying
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ */
/*  Pantalla principal                                                 */
/* ------------------------------------------------------------------ */

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val music by MediaRepository.state.collectAsStateWithLifecycle()
    val current = music

    // Guardamos la última canción para que las animaciones de salida no se corten
    var lastMusic by remember { mutableStateOf<NowPlaying?>(null) }
    LaunchedEffect(current) { if (current != null) lastMusic = current }
    val display = current ?: lastMusic

    val hasMusic = current != null
    val playing = current?.isPlaying == true
    var fullBleed by rememberSaveable { mutableStateOf(true) } // carátula a pantalla completa
    val colors = rememberArtColors(current?.art)

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val drag = remember { Animatable(0f) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val progress = (-drag.value / hPx).coerceIn(0f, 1f)

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = drag.value
                    alpha = 1f - progress * 0.9f
                    val s = 1f - progress * 0.08f
                    scaleX = s
                    scaleY = s
                }
                .pointerInput(hPx) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            scope.launch { drag.snapTo((drag.value + dy).coerceAtMost(0f)) }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (drag.value < -hPx * 0.22f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    drag.animateTo(-hPx, tween(260))
                                    onUnlock()
                                } else {
                                    drag.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                    )
                }
        ) {
            // 1) Fondo líquido con los colores de la carátula
            LiquidBackground(colors = colors, playing = playing, modifier = Modifier.fillMaxSize())
            display?.art?.let { BlurredArt(it, Modifier.fillMaxSize()) }

            // 2) Carátula que se transforma entre tarjeta y pantalla completa
            AnimatedVisibility(
                visible = hasMusic,
                enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.9f),
                exit = fadeOut(tween(400)) + scaleOut(targetScale = 0.9f),
            ) {
                display?.let {
                    MorphingArtwork(
                        np = it,
                        fullBleed = fullBleed,
                        playing = playing,
                        onToggle = { fullBleed = !fullBleed },
                    )
                }
            }

            // 3) Reloj, reproductor y "desliza para desbloquear"
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Clock(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                )
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = hasMusic,
                    enter = fadeIn(tween(500)) + slideInVertically(spring(dampingRatio = 0.8f)) { it / 2 },
                    exit = fadeOut(tween(300)) + slideOutVertically { it / 2 },
                ) {
                    display?.let { PlayerPanel(it) }
                }
                UnlockHint(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 10.dp)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Carátula animada                                                   */
/* ------------------------------------------------------------------ */

/**
 * La carátula pasa con un "spring" de tarjeta redondeada (estilo Apple Music)
 * a ocupar toda la pantalla (estilo pantalla de bloqueo de iOS 26).
 * En pausa la tarjeta se encoge; en pantalla completa hace un zoom lento.
 */
@Composable
private fun MorphingArtwork(
    np: NowPlaying,
    fullBleed: Boolean,
    playing: Boolean,
    onToggle: () -> Unit,
) {
    val t by animateFloatAsState(
        if (fullBleed) 1f else 0f,
        spring(dampingRatio = 0.85f, stiffness = 200f),
        label = "morph",
    )
    val pauseScale by animateFloatAsState(
        if (playing || fullBleed) 1f else 0.84f,
        spring(dampingRatio = 0.55f, stiffness = 280f),
        label = "pause",
    )
    val inf = rememberInfiniteTransition(label = "kenburns")
    val zoom by inf.animateFloat(
        1f, 1.09f,
        infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "zoom",
    )
    val zoomAmount by animateFloatAsState(if (playing) 1f else 0f, tween(1200), label = "zoomAmt")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val card = min(w - 72.dp, h * 0.36f)
        val cardTop = h * 0.27f

        val width = lerp(card, w, t)
        val height = lerp(card, h, t)
        val x = lerp((w - card) / 2, 0.dp, t)
        val y = lerp(cardTop, 0.dp, t)
        val corner = lerp(26.dp, 0.dp, t)
        val artShape = RoundedCornerShape(corner)

        Box(
            Modifier
                .offset(x, y)
                .width(width)
                .height(height)
                .graphicsLayer {
                    scaleX = pauseScale
                    scaleY = pauseScale
                    shadowElevation = (1f - t) * 36.dp.toPx()
                    shape = artShape
                    clip = true
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onToggle()
                }
        ) {
            Crossfade(targetState = np.art, animationSpec = tween(700), label = "art") { art ->
                if (art != null) {
                    val img = remember(art) { art.asImageBitmap() }
                    Image(
                        bitmap = img,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val z = 1f + (zoom - 1f) * t * zoomAmount
                                scaleX = z
                                scaleY = z
                            },
                    )
                } else {
                    ArtPlaceholder()
                }
            }
            // Degradados arriba y abajo para que se lean reloj y controles
            if (t > 0.01f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f * t),
                                0.28f to Color.Transparent,
                                0.5f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.8f * t),
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun ArtPlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF4A4A5A), Color(0xFF2A2A35)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(96.dp))
    }
}

/** Carátula muy desenfocada y girando lento detrás de todo. */
@Composable
private fun BlurredArt(art: Bitmap, modifier: Modifier) {
    val small = remember(art) {
        try {
            val ratio = art.height.toFloat() / art.width.coerceAtLeast(1)
            Bitmap.createScaledBitmap(art, 32, (32 * ratio).toInt().coerceAtLeast(1), true).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    } ?: return
    val inf = rememberInfiniteTransition(label = "rot")
    val rot by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(90_000, easing = LinearEasing)),
        label = "rotation",
    )
    Crossfade(targetState = small, animationSpec = tween(1000), label = "blur") { img ->
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .graphicsLayer {
                    scaleX = 1.8f
                    scaleY = 1.8f
                    rotationZ = rot
                    alpha = 0.5f
                }
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(60.dp) else Modifier),
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Reloj                                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun Clock(modifier: Modifier) {
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
    val time = SimpleDateFormat(if (is24) "H:mm" else "h:mm", locale).format(now)
    val datePattern = if (locale.language == "es") "EEEE, d 'de' MMMM" else "EEEE, MMMM d"
    val date = SimpleDateFormat(datePattern, locale).format(now).replaceFirstChar { it.titlecase(locale) }

    val shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 4f), 20f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            date,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = shadow,
            ),
        )
        // Efecto "vidrio": blanco que se desvanece hacia abajo
        Text(
            time,
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.72f))),
                fontSize = 92.sp,
                lineHeight = 96.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp,
                shadow = shadow,
            ),
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Reproductor                                                        */
/* ------------------------------------------------------------------ */

private val GlassShape = RoundedCornerShape(32.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerPanel(np: NowPlaying) {
    Column(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clip(GlassShape)
            .background(Color.White.copy(alpha = 0.13f))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.05f))),
                GlassShape,
            )
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        AnimatedContent(
            targetState = np.title to np.artist,
            transitionSpec = {
                (fadeIn(tween(450)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(200))
            },
            label = "title",
        ) { (title, artist) ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                if (artist.isNotBlank()) {
                    Text(
                        artist,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ProgressBar(np)
        Spacer(Modifier.height(6.dp))
        Controls(np.isPlaying)
    }
}

@Composable
private fun ProgressBar(np: NowPlaying) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(np.isPlaying, np.positionUpdatedAt, np.positionMs) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            if (!np.isPlaying) break
            delay(250)
        }
    }
    val dur = np.durationMs
    var dragFrac by remember { mutableStateOf<Float?>(null) }

    val livePos = np.currentPosition(now)
    val frac = dragFrac ?: if (dur > 0) livePos.toFloat() / dur else 0f
    val shownPos = if (dragFrac != null) (frac * dur).toLong() else livePos
    val barH by animateDpAsState(if (dragFrac != null) 12.dp else 7.dp, spring(), label = "barH")

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            dragFrac = ((dragFrac ?: 0f) + dx / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFrac?.let { if (dur > 0) MediaRepository.seekTo((it * dur).toLong()) }
                            dragFrac = null
                        },
                        onDragCancel = { dragFrac = null },
                    )
                }
                .pointerInput(dur) {
                    detectTapGestures { o ->
                        if (dur > 0) MediaRepository.seekTo((o.x / size.width * dur).toLong())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(barH)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.92f))
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(shownPos), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (dur > 0) {
                Text("-" + formatTime(dur - shownPos), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Controls(playing: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BounceButton(onClick = MediaRepository::previous) {
            Icon(Icons.Rounded.FastRewind, "Anterior", tint = Color.White, modifier = Modifier.size(42.dp))
        }
        BounceButton(onClick = MediaRepository::playPause, size = 76.dp) {
            AnimatedContent(
                targetState = playing,
                transitionSpec = {
                    (scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 0.5f) + fadeOut(tween(120)))
                },
                label = "playpause",
            ) { p ->
                Icon(
                    if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (p) "Pausa" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(58.dp),
                )
            }
        }
        BounceButton(onClick = MediaRepository::next) {
            Icon(Icons.Rounded.FastForward, "Siguiente", tint = Color.White, modifier = Modifier.size(42.dp))
        }
    }
}

/** Botón que se "hunde" y rebota al tocarlo, como en iOS. */
@Composable
private fun BounceButton(
    onClick: () -> Unit,
    size: Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.8f else 1f,
        spring(dampingRatio = 0.4f, stiffness = 650f),
        label = "press",
    )
    val bg by animateFloatAsState(if (pressed) 0.2f else 0f, tween(150), label = "pressBg")
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = bg))
            .clickable(interactionSource = src, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/* ------------------------------------------------------------------ */
/*  Desliza para desbloquear                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun UnlockHint(modifier: Modifier) {
    val inf = rememberInfiniteTransition(label = "hint")
    val dy by inf.animateFloat(
        0f, -8f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dy",
    )
    val a by inf.animateFloat(
        0.45f, 0.95f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Rounded.KeyboardArrowUp,
            null,
            tint = Color.White.copy(alpha = a),
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { translationY = dy.dp.toPx() },
        )
        Text("Desliza hacia arriba para desbloquear", color = Color.White.copy(alpha = a), fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .width(134.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f))
        )
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
