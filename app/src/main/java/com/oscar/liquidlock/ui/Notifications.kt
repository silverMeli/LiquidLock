package com.oscar.liquidlock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oscar.liquidlock.LockNotif
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

val GlassShape = RoundedCornerShape(28.dp)

/** Fondo de "vidrio" reutilizable. */
fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape = GlassShape, alpha: Float = 0.14f): Modifier =
    this
        .clip(shape)
        .background(Color.White.copy(alpha = alpha))
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.05f))),
            shape,
        )

fun relativeTime(t: Long): String {
    val diff = (System.currentTimeMillis() - t) / 60_000
    return when {
        diff < 1 -> "ahora"
        diff < 60 -> "hace $diff min"
        else -> SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(t))
    }
}

@Composable
fun AppIcon(n: LockNotif, size: Dp) {
    val bmp = n.icon
    if (bmp != null) {
        val img = remember(bmp) { bmp.asImageBitmap() }
        Image(img, null, Modifier.size(size).clip(RoundedCornerShape(size / 4.5f)))
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4.5f))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Notifications, null, tint = Color.White, modifier = Modifier.size(size * 0.6f)) }
    }
}

/** Tarjeta de una notificación. Se puede deslizar a los lados para borrarla. */
@Composable
fun NotifCard(
    n: LockNotif,
    showContent: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 3,
) {
    val offset = remember(n.key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hidden = !showContent || n.secret

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 1f)
            }
            .pointerInput(n.key) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dx ->
                        if (!n.clearable) return@detectHorizontalDragGestures
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + dx) }
                    },
                    onDragEnd = {
                        scope.launch {
                            val w = size.width.toFloat()
                            if (abs(offset.value) > w * 0.35f) {
                                offset.animateTo(if (offset.value > 0) w else -w, tween(180))
                                onDismiss()
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = 0.7f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                )
            }
            .glass(RoundedCornerShape(24.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(n, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hidden) n.appName else n.title.ifBlank { n.appName },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(relativeTime(n.postTime), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
            Text(
                if (hidden) "Notificación" else n.text,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Pila de notificaciones (como en iOS): se ve la más reciente con "capas" detrás. */
@Composable
fun NotificationStack(
    notifs: List<LockNotif>,
    showContent: Boolean,
    onExpand: () -> Unit,
    onOpen: (LockNotif) -> Unit,
    onDismiss: (LockNotif) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notifs.isEmpty()) return
    val top = notifs.first()
    Box(modifier.padding(horizontal = 14.dp)) {
        // Capas de atrás
        if (notifs.size > 2) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer { translationY = 16.dp.toPx() }
                    .glass(RoundedCornerShape(24.dp), 0.06f)
            )
        }
        if (notifs.size > 1) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = 12.dp)
                    .graphicsLayer { translationY = 8.dp.toPx() }
                    .glass(RoundedCornerShape(24.dp), 0.09f)
            )
        }
        NotifCard(
            n = top,
            showContent = showContent,
            onOpen = { if (notifs.size > 1) onExpand() else onOpen(top) },
            onDismiss = { onDismiss(top) },
            maxLines = 2,
        )
        if (notifs.size > 1) {
            Text(
                "+${notifs.size - 1}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
                    .graphicsLayer { translationY = -8.dp.toPx() }
                    .clip(CircleShape)
                    .background(Color(0xFFFF453A))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}


/** Lista completa (al tocar la pila). */
@Composable
fun NotificationSheet(
    notifs: List<LockNotif>,
    showContent: Boolean,
    onClose: () -> Unit,
    onOpen: (LockNotif) -> Unit,
    onDismiss: (LockNotif) -> Unit,
    onClearAll: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, null) { onClose() }
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Notificaciones",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Borrar todo",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .glass(CircleShape, 0.18f)
                        .clickable { onClearAll() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notifs, key = { it.key }) { n ->
                    NotifCard(
                        n = n,
                        showContent = showContent,
                        onOpen = { onOpen(n) },
                        onDismiss = { onDismiss(n) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            Text(
                "Mostrar menos",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
                    .glass(CircleShape, 0.16f)
                    .clickable { onClose() }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }
    }
}
