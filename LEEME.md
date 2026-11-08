# LiquidLock — pantalla de bloqueo con música estilo iOS 26

## Qué hace
- Aparece cada vez que apagas la pantalla (encima del bloqueo de Android).
- Reloj grande con efecto vidrio y fecha.
- Si suena música (Spotify, YouTube Music, Deezer, etc.):
  - La carátula ocupa **toda la pantalla** con un zoom lento mientras suena.
  - Toca la carátula y se convierte en **tarjeta** (con un "spring"); en pausa la tarjeta se encoge, como en Apple Music.
  - El fondo son manchas de color que se mueven, sacadas de los colores de la carátula, más la carátula desenfocada girando.
  - Panel de vidrio con título, artista, barra de progreso (arrástrala para adelantar) y botones que rebotan.
- Desliza hacia arriba para desbloquear (si tienes PIN/huella, Android te lo pide).

## Cómo compilarla
1. Instala **Android Studio** (gratis) en tu computadora.
2. Descomprime este ZIP y en Android Studio elige **File > Open** y abre la carpeta `LiquidLock`.
3. Espera a que termine "Gradle sync" (la primera vez descarga cosas, necesita internet).
   - Si te pide actualizar el plugin de Android (AGP), acepta.
4. Conecta tu teléfono con **Depuración USB** activada (Ajustes > Acerca del teléfono > toca 7 veces "Número de compilación" > Opciones de desarrollador > Depuración USB).
5. Presiona el botón verde ▶ **Run**.
   - Para sacar un APK: **Build > Build App Bundle(s) / APK(s) > Build APK(s)**. Queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Primer uso en el teléfono
Abre LiquidLock y concede, en orden:
1. **Acceso a notificaciones** (para leer la música).
2. **Mostrar sobre otras apps** (para abrirse al apagar la pantalla).
3. **Notificaciones** (Android 13+).
4. Recomendado: **Batería sin restricciones** (en Xiaomi, Samsung, Oppo, etc. también activa "Inicio automático" para LiquidLock).

Luego activa "Activar pantalla de bloqueo" y prueba con "Vista previa".

## Límites de Android (no son errores)
- Android no deja reemplazar el bloqueo del sistema: esta app se muestra **encima** de él. Tu PIN/huella siguen protegiendo el teléfono.
- El sistema puede mostrar su propio bloqueo un instante antes que el nuestro.
- La fuente del reloj es la del sistema (la de Apple no se puede incluir).

## Archivos principales
- `MediaListenerService.kt` — lee la canción, carátula y estado de cualquier app de música.
- `LockService.kt` — detecta cuando se apaga la pantalla y abre la pantalla de bloqueo.
- `LockActivity.kt` — la pantalla que se muestra sobre el bloqueo.
- `ui/LockScreen.kt` — toda la interfaz y animaciones.
- `ui/LiquidBackground.kt` — el fondo de colores animado.
- `MainActivity.kt` — pantalla de permisos y opciones.
