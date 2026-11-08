package com.oscar.liquidlock.ui

import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oscar.liquidlock.ClockStyle
import com.oscar.liquidlock.LockNotif
import com.oscar.liquidlock.MediaRepository
import com.oscar.liquidlock.NotifRepository
import com.oscar.liquidlock.NowPlaying
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/* ------------------------------------------------------------------ */
/*  Pantalla principal                                                 */
/* ------------------------------------------------------------------ */

@Composable
fun LockScreen(
    clock: ClockStyle,
    showNotifs: Boolean,
    showNotifContent: Boolean,
    onUnlock: () -> Unit,
    onOpenNotification: (LockNotif) -> Unit,
) {
    val music by MediaRepository.state.collectAsStateWithLifecycle()
    val allNotifs by NotifRepository.state.collectAsStateWithLifecycle()
    val notifs = if (showNotifs) allNotifs else emptyList()
    val current = music

    var lastMusic by remember { mutableStateOf<NowPlaying?>(null) }
    LaunchedEffect(current) { if (current != null) lastMusic = current }
    val display = current ?: lastMusic

    val hasMusic = current != null
    val playing = current?.isPlaying == true
    var fullBleed by rememberSaveable { mutableStateOf(true) }
    var sheetOpen by remember { mutableStateOf(false) }
    LaunchedEffect(notifs.isEmpty()) { if (notifs.isEmpty()) sheetOpen = false }
    val colors = rememberArtColors(current?.art)

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val drag = remember { Animatable(0f) }

    // Medidas para colocar la carátula en el hueco libre
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var clockBottom by remember { mutableFloatStateOf(0f) }
    var bottomTop by remember { mutableFloatStateOf(0f) }

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
                .onGloballyPositioned { rootCoords = it }
        ) {
            // 1) Fondo líquido con los colores de la carátula
            LiquidBackground(colors = colors, playing = playing, modifier = Modifier.fillMaxSize())
            display?.art?.let { BlurredArt(it, Modifier.fillMaxSize()) }

            // 2) Carátula (tarjeta ↔ ampliada)
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
                        areaTop = with(density) { clockBottom.toDp() },
                        areaBottom = with(density) { bottomTop.toDp() },
                        onToggle = { fullBleed = !fullBleed },
                    )
                }
            }

            // 3) Reloj arriba; notificaciones, reproductor y "desliza" abajo
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                ClockView(
                    style = clock,
                    accent = accentFrom(colors),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .onGloballyPositioned { c ->
                            rootCoords?.let { r -> clockBottom = r.localBoundingBoxOf(c).bottom }
                        },
                )
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.onGloballyPositioned { c ->
                        rootCoords?.let { r -> bottomTop = r.localBoundingBoxOf(c).top }
                    }
                ) {
                    AnimatedVisibility(
                        visible = notifs.isNotEmpty(),
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut(),
                    ) {
                        NotificationStack(
                            notifs = notifs,
                            showContent = showNotifContent,
                            onExpand = { sheetOpen = true },
                            onOpen = onOpenNotification,
                            onDismiss = { NotifRepository.dismiss(it) },
                            modifier = Modifier.padding(bottom = 22.dp),
                        )
                    }
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
                            .padding(top = 12.dp, bottom = 8.dp)
                    )
                }
            }

            // 4) Lista completa de notificaciones
            AnimatedVisibility(visible = sheetOpen, enter = fadeIn(), exit = fadeOut()) {
                NotificationSheet(
                    notifs = notifs,
                    showContent = showNotifContent,
                    onClose = { sheetOpen = false },
                    onOpen = onOpenNotification,
                    onDismiss = { NotifRepository.dismiss(it) },
                    onClearAll = {
                        NotifRepository.dismissAll()
                        sheetOpen = false
                    },
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Carátula animada                                                   */
/* ------------------------------------------------------------------ */

/**
 * Tarjeta: carátula redondeada en el hueco entre reloj y controles; se encoge en pausa.
 * Ampliada (estilo iOS 26): ocupa todo el ancho arriba, sin recortarse, y se funde
 * suavemente con el fondo de colores. Así no se ve pixelada ni deformada.
 */
@Composable
private fun MorphingArtwork(
    np: NowPlaying,
    fullBleed: Boolean,
    playing: Boolean,
    areaTop: Dp,
    areaBottom: Dp,
    onToggle: () -> Unit,
) {
    val t by animateFloatAsState(
        if (fullBleed) 1f else 0f,
        spring(dampingRatio = 0.85f, stiffness = 200f),
        label = "morph",
    )
    val pauseScale by animateFloatAsState(
        if (playing || fullBleed) 1f else 0.86f,
        spring(dampingRatio = 0.55f, stiffness = 280f),
        label = "pause",
    )
    val inf = rememberInfiniteTransition(label = "breathe")
    val zoom by inf.animateFloat(
        1f, 1.04f,
        infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "zoom",
    )
    val zoomAmount by animateFloatAsState(if (playing) 1f else 0f, tween(1200), label = "zoomAmt")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        // Hueco libre (si aún no se midió, valores por defecto)
        val top = if (areaTop > 0.dp) areaTop else h * 0.25f
        val bottom = if (areaBottom > top + 100.dp) areaBottom else h * 0.62f
        val card = max(120.dp, min(w - 64.dp, bottom - top - 28.dp))
        val cardTop = top + (bottom - top - card) / 2

        val bigW = w
        val bigH = min(w * 1.1f, h * 0.62f)

        val tc = t.coerceIn(0f, 1f) // el rebote del spring no debe dar valores negativos
        val width = lerp(card, bigW, t)
        val height = lerp(card, bigH, t)
        val x = lerp((w - card) / 2, 0.dp, t)
        val y = lerp(cardTop, 0.dp, t)
        val artShape = RoundedCornerShape(lerp(26.dp, 0.dp, tc))

        Box(
            Modifier
                .offset(x, y)
                .width(width)
                .height(height)
                .graphicsLayer {
                    scaleX = pauseScale
                    scaleY = pauseScale
                    shadowElevation = (1f - tc) * 36.dp.toPx()
                    shape = artShape
                    clip = true
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onToggle()
                }
        ) {
            Crossfade(targetState = np.art, animationSpec = tween(700), label = "art") { art ->
                Box(
                    Modifier
                        .fillMaxSize()
                        // Desvanecido inferior para fundirse con el fondo (solo ampliada)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (tc > 0.01f) {
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.Black,
                                        0.55f to Color.Black,
                                        1f to Color.Black.copy(alpha = 1f - tc),
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                ) {
                    if (art != null) {
                        val img = remember(art) { art.asImageBitmap() }
                        Image(
                            bitmap = img,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val z = 1f + (zoom - 1f) * zoomAmount
                                    scaleX = z
                                    scaleY = z
                                },
                        )
                    } else {
                        ArtPlaceholder()
                    }
                    // Oscurecer un poco arriba para que el reloj se lea
                    if (tc > 0.01f) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.4f * tc),
                                        0.35f to Color.Transparent,
                                    )
                                )
                        )
                    }
                }
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
/*  Reproductor                                                        */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerPanel(np: NowPlaying) {
    Column(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .glass()
            .padding(horizontal = 22.dp, vertical = 16.dp)
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
        Spacer(Modifier.height(10.dp))
        ProgressBar(np)
        Controls(np.isPlaying)
        VolumeSlider()
    }
}

/** Barra fina que se engrosa al arrastrarla (como en iOS). */
@Composable
private fun GlassBar(
    fraction: Float,
    onSeek: (Float, Boolean) -> Unit, // (fracción, terminó)
    modifier: Modifier = Modifier,
) {
    val seek by rememberUpdatedState(onSeek)
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val shown = (dragFrac ?: fraction).coerceIn(0f, 1f)
    val barH by animateDpAsState(if (dragFrac != null) 12.dp else 7.dp, spring(), label = "barH")

    Box(
        modifier
            .height(26.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragFrac = (o.x / size.width).coerceIn(0f, 1f)
                        seek(dragFrac!!, false)
                    },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        dragFrac = ((dragFrac ?: 0f) + dx / size.width).coerceIn(0f, 1f)
                        seek(dragFrac!!, false)
                    },
                    onDragEnd = {
                        dragFrac?.let { seek(it, true) }
                        dragFrac = null
                    },
                    onDragCancel = { dragFrac = null },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { o -> seek((o.x / size.width).coerceIn(0f, 1f), true) }
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
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.92f))
            )
        }
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
    var preview by remember { mutableStateOf<Float?>(null) }
    val livePos = np.currentPosition(now)
    val frac = if (dur > 0) livePos.toFloat() / dur else 0f
    val shownPos = preview?.let { (it * dur).toLong() } ?: livePos

    Column {
        GlassBar(
            fraction = preview ?: frac,
            onSeek = { f, done ->
                if (done) {
                    if (dur > 0) MediaRepository.seekTo((f * dur).toLong())
                    preview = null
                } else {
                    preview = f
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(shownPos), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (dur > 0) {
                Text("-" + formatTime(dur - shownPos), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Volumen del teléfono (música), con íconos a cada lado como en iOS. */
@Composable
private fun VolumeSlider() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) } ?: return
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var dragging by remember { mutableStateOf(false) }

    // Si cambian el volumen con los botones físicos, la barra se actualiza
    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            delay(350)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Rounded.VolumeDown, "Bajar volumen",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        GlassBar(
            fraction = vol.toFloat() / max,
            onSeek = { f, done ->
                dragging = !done
                val v = (f * max).roundToInt().coerceIn(0, max)
                if (v != vol) {
                    vol = v
                    try {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                    } catch (_: Exception) {
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp, "Subir volumen",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
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
            Icon(Icons.Rounded.FastRewind, "Anterior", tint = Color.White, modifier = Modifier.size(40.dp))
        }
        BounceButton(onClick = MediaRepository::playPause, size = 72.dp) {
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
                    modifier = Modifier.size(54.dp),
                )
            }
        }
        BounceButton(onClick = MediaRepository::next) {
            Icon(Icons.Rounded.FastForward, "Siguiente", tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

/** Botón que se "hunde" y rebota al tocarlo, como en iOS. */
@Composable
private fun BounceButton(
    onClick: () -> Unit,
    size: Dp = 60.dp,
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
            .background(Color.White.copy(alpha = bg.coerceIn(0f, 1f)))
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
                .size(26.dp)
                .graphicsLayer { translationY = dy.dp.toPx() },
        )
        Text("Desliza hacia arriba para desbloquear", color = Color.White.copy(alpha = a), fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
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
