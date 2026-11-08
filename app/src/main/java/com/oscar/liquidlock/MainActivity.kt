package com.oscar.liquidlock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.oscar.liquidlock.ui.ClockView
import com.oscar.liquidlock.ui.fontFor

/** Pantalla de configuración: permisos y opciones. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (App.crashFile(this).exists()) {
            startActivity(Intent(this, CrashActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        if (Prefs.isEnabled(this)) LockService.start(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Blue)) {
                SetupScreen()
            }
        }
    }
}

private val Blue = Color(0xFF0A84FF)
private val CardBg = Color(0xFF1C1C1E)

private fun hasNotificationAccess(ctx: Context) =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

private fun hasOverlay(ctx: Context) = Settings.canDrawOverlays(ctx)

private fun hasPostNotifications(ctx: Context) =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun ignoresBattery(ctx: Context) =
    ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

@Composable
private fun SetupScreen() {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val notifAccess = remember(refresh) { hasNotificationAccess(ctx) }
    val overlay = remember(refresh) { hasOverlay(ctx) }
    val postNotif = remember(refresh) { hasPostNotifications(ctx) }
    val battery = remember(refresh) { ignoresBattery(ctx) }

    var enabled by remember { mutableStateOf(Prefs.isEnabled(ctx)) }
    var musicOnly by remember { mutableStateOf(Prefs.isMusicOnly(ctx)) }
    var aod by remember { mutableStateOf(Prefs.isAodEnabled(ctx)) }
    var aodTimeout by remember { mutableIntStateOf(Prefs.aodTimeoutMin(ctx)) }
    var aodMusicOnly by remember { mutableStateOf(Prefs.isAodMusicOnly(ctx)) }
    var notifs by remember { mutableStateOf(Prefs.showNotifications(ctx)) }
    var notifContent by remember { mutableStateOf(Prefs.showNotifContent(ctx)) }
    var clock by remember { mutableStateOf(Prefs.clockStyle(ctx)) }
    fun saveClock(c: ClockStyle) {
        clock = c
        Prefs.setClockStyle(ctx, c)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val allRequired = notifAccess && overlay && postNotif

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("LiquidLock", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(
                "v${BuildConfig.VERSION_NAME}",
                color = Blue,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            "Pantalla de bloqueo con reproductor, notificaciones y Always On.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp,
        )

        /* ---------------- Permisos ---------------- */
        SectionTitle("1. Permisos")
        PermissionRow("Acceso a notificaciones", "Para la música y las notificaciones", notifAccess) {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        PermissionRow("Mostrar sobre otras apps", "Para abrirse al apagar la pantalla", overlay) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionRow("Notificaciones", "Android lo exige para el servicio en segundo plano", postNotif) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        PermissionRow(
            "Batería sin restricciones (recomendado)",
            "Busca LiquidLock y elige \"Sin restricciones\"",
            battery,
        ) { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }

        /* ---------------- Pantalla de bloqueo ---------------- */
        SectionTitle("2. Pantalla de bloqueo")
        SwitchRow(
            "Activar pantalla de bloqueo",
            if (allRequired) "Aparece cada vez que apagas la pantalla" else "Primero concede los permisos de arriba",
            enabled,
            allRequired || enabled,
        ) {
            enabled = it
            Prefs.setEnabled(ctx, it)
            if (it) LockService.start(ctx) else LockService.stop(ctx)
        }
        SwitchRow("Solo cuando suene música", "Si está apagado, también aparece sin música", musicOnly, true) {
            musicOnly = it
            Prefs.setMusicOnly(ctx, it)
        }
        SwitchRow("Mostrar notificaciones", "En la pantalla de bloqueo y en el Always On", notifs, true) {
            notifs = it
            Prefs.setShowNotifications(ctx, it)
        }
        SwitchRow("Mostrar contenido de notificaciones", "Si está apagado solo se ve el nombre de la app", notifContent, notifs) {
            notifContent = it
            Prefs.setShowNotifContent(ctx, it)
        }

        /* ---------------- Reloj ---------------- */
        SectionTitle("3. Reloj")
        ClockEditor(clock, ::saveClock)

        /* ---------------- Always On ---------------- */
        SectionTitle("4. Always On Display")
        SwitchRow(
            "Activar Always On",
            if (enabled) "Al apagar la pantalla se muestra reloj, música y notificaciones con brillo mínimo"
            else "Primero activa la pantalla de bloqueo",
            aod,
            enabled,
        ) {
            aod = it
            Prefs.setAodEnabled(ctx, it)
        }
        SwitchRow("Always On solo con música", "Si no suena nada, la pantalla se apaga normal", aodMusicOnly, aod) {
            aodMusicOnly = it
            Prefs.setAodMusicOnly(ctx, it)
        }
        SettingRow {
            Column(Modifier.weight(1f)) {
                Text("Tiempo encendido", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                ChipRow(
                    options = listOf(1 to "1 min", 5 to "5 min", 10 to "10 min", 30 to "30 min", 0 to "Siempre"),
                    selected = aodTimeout,
                ) {
                    aodTimeout = it
                    Prefs.setAodTimeoutMin(ctx, it)
                }
            }
        }
        Text(
            "Consejos: desactiva el Always On Display de Samsung (Ajustes > Pantalla de bloqueo y AOD) " +
                "para que no se vean los dos. Toca dos veces el Always On para abrir la pantalla completa. " +
                "Con batería baja (15%) no se activa, salvo cargando.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { ctx.startActivity(Intent(ctx, LockActivity::class.java)) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue),
        ) { Text("Vista previa", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        Button(
            onClick = {
                ctx.startActivity(
                    Intent(ctx, LockActivity::class.java).putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_AOD)
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CardBg),
        ) { Text("Vista previa del Always On", fontSize = 17.sp, color = Color.White) }
        Text(
            "En la vista previa: toca la carátula para ampliarla o hacerla tarjeta, toca la pila de " +
                "notificaciones para verlas todas y desliza hacia arriba para salir.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/* ------------------------------ Editor del reloj ------------------------------ */

private val clockColors = listOf(
    0xFFFFFFFF, 0xFF0A84FF, 0xFF5E5CE6, 0xFFFF375F, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF,
)

@Composable
private fun ClockEditor(style: ClockStyle, onChange: (ClockStyle) -> Unit) {
    // Vista previa en vivo
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A3DD9), Color(0xFF8E2DE2), Color(0xFFFF5E9C)))),
        contentAlignment = Alignment.Center,
    ) {
        ClockView(style = style, accent = Color(0xFFFFD1E6), scale = 0.8f)
    }

    SettingRow {
        Column(Modifier.weight(1f)) {
            Label("Fuente")
            ChipRow(
                options = listOf("sans" to "Normal", "serif" to "Clásica", "mono" to "Digital", "cursive" to "Manuscrita"),
                selected = style.font,
                fontKey = true,
            ) { onChange(style.copy(font = it)) }

            Spacer(Modifier.height(12.dp))
            Label("Grosor")
            ChipRow(
                options = listOf(200 to "Fino", 400 to "Normal", 700 to "Negrita", 900 to "Grueso"),
                selected = style.weight,
            ) { onChange(style.copy(weight = it)) }

            Spacer(Modifier.height(12.dp))
            Label("Tamaño: ${style.size}")
            Slider(
                value = style.size.toFloat(),
                onValueChange = { onChange(style.copy(size = it.toInt())) },
                valueRange = 56f..130f,
            )

            Label("Color")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Color de la canción
                ColorDot(
                    brush = Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                    selected = style.color == ClockStyle.ART_COLOR,
                ) { onChange(style.copy(color = ClockStyle.ART_COLOR)) }
                clockColors.forEach { c ->
                    ColorDot(
                        brush = Brush.linearGradient(listOf(Color(c.toInt()), Color(c.toInt()))),
                        selected = style.color == c,
                    ) { onChange(style.copy(color = c)) }
                }
            }
            Text(
                "El círculo de colores usa el color de la canción que suena.",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    SwitchRow("Reloj alto (estilo iOS 26)", "Estira los números hacia abajo", style.stretch, true) {
        onChange(style.copy(stretch = it))
    }
    SwitchRow("Mostrar segundos", "", style.seconds, true) {
        onChange(style.copy(seconds = it))
    }
    TextButton(onClick = { onChange(ClockStyle()) }) { Text("Restablecer reloj") }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    fontKey: Boolean = false,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Text(
                label,
                color = if (sel) Color.White else Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (fontKey) fontFor(value as String) else null,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (sel) Blue else Color.White.copy(alpha = 0.08f))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ColorDot(brush: Brush, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.2f), CircleShape)
            .padding(if (selected) 5.dp else 0.dp)
            .clip(CircleShape)
            .background(brush)
            .clickable(onClick = onClick)
    )
}

/* ------------------------------ Filas genéricas ------------------------------ */

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun SettingRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    SettingRow {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Text("✓ Listo", color = Color(0xFF30D158), fontWeight = FontWeight.SemiBold)
        } else {
            TextButton(onClick = onClick) { Text("Conceder") }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.White.copy(alpha = if (enabled) 0.55f else 0.3f), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
