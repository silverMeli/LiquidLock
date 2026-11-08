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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

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
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF0A84FF))) {
                SetupScreen()
            }
        }
    }
}

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

    // Leer `refresh` hace que todo se recalcule al volver de Ajustes
    val notifAccess = remember(refresh) { hasNotificationAccess(ctx) }
    val overlay = remember(refresh) { hasOverlay(ctx) }
    val postNotif = remember(refresh) { hasPostNotifications(ctx) }
    val battery = remember(refresh) { ignoresBattery(ctx) }

    var enabled by remember { mutableStateOf(Prefs.isEnabled(ctx)) }
    var musicOnly by remember { mutableStateOf(Prefs.isMusicOnly(ctx)) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val allRequired = notifAccess && overlay && postNotif

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("LiquidLock", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Pantalla de bloqueo con reproductor a pantalla completa y animaciones.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp,
        )

        SectionTitle("1. Permisos")
        PermissionRow(
            "Acceso a notificaciones",
            "Para saber qué canción suena y controlarla",
            notifAccess,
        ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        PermissionRow(
            "Mostrar sobre otras apps",
            "Para abrir la pantalla de bloqueo al apagar el display",
            overlay,
        ) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }

        if (Build.VERSION.SDK_INT >= 33) {
            PermissionRow(
                "Notificaciones",
                "Android lo exige para el servicio en segundo plano",
                postNotif,
            ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }

        PermissionRow(
            "Batería sin restricciones (recomendado)",
            "Evita que el sistema cierre la app. Busca LiquidLock y elige \"Sin restricciones\"",
            battery,
        ) { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }

        SectionTitle("2. Opciones")
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
        SwitchRow(
            "Solo cuando suene música",
            "Si está apagado, también aparece sin música (solo reloj)",
            musicOnly,
            true,
        ) {
            musicOnly = it
            Prefs.setMusicOnly(ctx, it)
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { ctx.startActivity(Intent(ctx, LockActivity::class.java)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
        ) { Text("Vista previa", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }

        Text(
            "Consejo: pon algo en Spotify o YouTube Music y toca \"Vista previa\". " +
                "Toca la carátula para cambiar entre pantalla completa y tarjeta. " +
                "Desliza hacia arriba para salir.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
        )
    }
}

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
            .background(Color(0xFF1C1C1E), RoundedCornerShape(18.dp))
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
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
