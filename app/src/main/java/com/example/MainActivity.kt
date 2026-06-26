package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bot.BotConfig
import com.example.bot.BotLogEntry
import com.example.bot.BotLogLevel
import com.example.bot.BotRuntimeSnapshot
import com.example.capture.MediaProjectionPermissionController
import com.example.capture.NoOpFrameSource
import com.example.capture.ScreenCaptureFrameSource
import com.example.ui.theme.ImmersiveBorder
import com.example.ui.theme.MyApplicationTheme
import com.example.permissions.PermissionManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    private lateinit var mediaProjectionPermissionController: MediaProjectionPermissionController
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize permission manager and request all required permissions
        permissionManager = PermissionManager(this)
        
        // Request regular runtime permissions
        permissionManager.requestRuntimePermissions { permissions ->
            // After regular permissions, request special permissions
            permissionManager.requestOverlayPermission { overlayGranted ->
                if (!overlayGranted) {
                    // Overlay permission not granted, but continue anyway
                }
                // Request accessibility permission
                permissionManager.requestAccessibilityPermission { accessibilityGranted ->
                    if (!accessibilityGranted) {
                        // Accessibility permission not granted, but continue anyway
                    }
                }
            }
        }

        mediaProjectionPermissionController = MediaProjectionPermissionController(this)
        mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK && it.data != null) {
                val viewModel: FishBotViewModel = (application as MyApplication).fishBotViewModel
                viewModel.setFrameSource(ScreenCaptureFrameSource(this, it.resultCode, it.data!!))
            } else {
                // Handle permission denied or null data, maybe show a message
            }
        }

        val viewModel = (application as MyApplication).fishBotViewModel
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FishBotDashboard(mediaProjectionLauncher, mediaProjectionPermissionController, viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FishBotDashboard(
    mediaProjectionLauncher: ActivityResultLauncher<Intent>,
    mediaProjectionPermissionController: MediaProjectionPermissionController,
    viewModel: FishBotViewModel
) {
    val context = LocalContext.current
    val runtime by viewModel.runtime.collectAsState()
    val config by viewModel.config.collectAsState()
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(isAccessibilityServiceEnabled(context, context.packageName)) }
    var hasMediaProjectionPermission by remember { mutableStateOf(false) } // We don't have a direct check for this, it's granted via the launcher

    // Reset frame source to NoOp when the bot is stopped or permissions are lost
    LaunchedEffect(runtime.isRunning, hasMediaProjectionPermission) {
        if (!runtime.isRunning || !hasMediaProjectionPermission) {
            viewModel.setFrameSource(NoOpFrameSource())
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasAccessibilityPermission = isAccessibilityServiceEnabled(context, context.packageName)
            hasMediaProjectionPermission = viewModel.isScreenCaptureActive.value
            delay(1_000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto Fish NTE", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ControlPanel(
                runtime = runtime,
                accessibilityReady = hasAccessibilityPermission,
                onToggleBot = viewModel::toggleBot
            )

            PermissionsCard(
                hasOverlay = hasOverlayPermission,
                hasAccessibility = hasAccessibilityPermission,
                hasMediaProjection = hasMediaProjectionPermission,
                onRequestOverlay = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                },
                onRequestAccessibility = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onRequestMediaProjection = {
                    mediaProjectionPermissionController.createScreenCaptureIntent()?.let {
                        mediaProjectionLauncher.launch(it)
                    }
                }
            )

            HorizontalDivider()

            BotTuningCard(
                config = config,
                onConfigChange = viewModel::updateConfig
            )

            RoutineSettingsCard(
                config = config,
                onConfigChange = viewModel::updateConfig
            )

            PythonVisionCard(
                config = config,
                onConfigChange = viewModel::updateConfig
            )

            RuntimeLogCard(runtime.logs)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ControlPanel(
    runtime: BotRuntimeSnapshot,
    accessibilityReady: Boolean,
    onToggleBot: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(
            containerColor = if (runtime.isRunning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Estado del motor", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (runtime.isRunning) runtime.state.name else "DETENIDO",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (runtime.isRunning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Confianza: ${String.format(Locale.US, "%.2f", runtime.lastConfidence)} · Acción: ${runtime.lastAction}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onToggleBot,
                    enabled = accessibilityReady || runtime.isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (runtime.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("submit_button").height(56.dp)
                ) {
                    Icon(if (runtime.isRunning) Icons.Default.Close else Icons.Default.PlayArrow, contentDescription = "Toggle Bot")
                    Spacer(Modifier.width(8.dp))
                    Text(if (runtime.isRunning) "DETENER" else "INICIAR")
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Peces capturados: ${runtime.fishCaught}", color = Color(0xFF61D394), fontWeight = FontWeight.Bold)
                    Text("Score visión: ${String.format(Locale.US, "%.4f", runtime.lastVisionScore)}", color = Color.LightGray)
                    Text(runtime.logs.firstOrNull()?.message ?: "Sin eventos", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun BotTuningCard(config: BotConfig, onConfigChange: ((BotConfig) -> BotConfig) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Calibración de pesca", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Ajusta la detección de picada por color y las coordenadas de toque normalizadas. Estos valores se guardan en el dispositivo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SliderSetting(
                label = "Tolerancia de color",
                value = config.colorTolerance.toFloat(),
                range = 5f..95f,
                display = config.colorTolerance.toString(),
                onValueChange = { value -> onConfigChange { it.copy(colorTolerance = value.toInt()) } }
            )
            SliderSetting(
                label = "Umbral de picada",
                value = config.biteThreshold.toFloat(),
                range = 0.001f..0.08f,
                display = String.format(Locale.US, "%.3f", config.biteThreshold),
                onValueChange = { value -> onConfigChange { it.copy(biteThreshold = value.toDouble()) } }
            )
            SliderSetting(
                label = "X toque hook",
                value = config.hookXNorm,
                range = 0f..1f,
                display = String.format(Locale.US, "%.2f", config.hookXNorm),
                onValueChange = { value -> onConfigChange { it.copy(hookXNorm = value) } }
            )
            SliderSetting(
                label = "Y toque hook",
                value = config.hookYNorm,
                range = 0f..1f,
                display = String.format(Locale.US, "%.2f", config.hookYNorm),
                onValueChange = { value -> onConfigChange { it.copy(hookYNorm = value) } }
            )
        }
    }
}

@Composable
fun RoutineSettingsCard(config: BotConfig, onConfigChange: ((BotConfig) -> BotConfig) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Rutinas y seguridad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            NumericSetting(
                label = "Comprar carnada cada X peces",
                value = config.restockEveryFish,
                onChange = { onConfigChange { cfg -> cfg.copy(restockEveryFish = it.coerceAtLeast(1)) } }
            )
            NumericSetting(
                label = "Vender inventario cada X peces",
                value = config.sellEveryFish,
                onChange = { onConfigChange { cfg -> cfg.copy(sellEveryFish = it.coerceAtLeast(1)) } }
            )
        }
    }
}

@Composable
fun PythonVisionCard(config: BotConfig, onConfigChange: ((BotConfig) -> BotConfig) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Motor Python local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("La app ya tiene un puente Chaquopy preparado para analizar frames RGBA con NumPy/Pillow sin depender de la nube.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Usar visión Python")
                Switch(checked = config.enablePythonVision, onCheckedChange = { enabled -> onConfigChange { it.copy(enablePythonVision = enabled) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Logs de depuración")
                Switch(checked = config.enableDebugLogs, onCheckedChange = { enabled -> onConfigChange { it.copy(enableDebugLogs = enabled) } })
            }
        }
    }
}

@Composable
fun RuntimeLogCard(logs: List<BotLogEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Eventos del motor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            logs.take(10).forEach { log ->
                Text(
                    text = "${formatTime(log.timestampMillis)} · ${log.level.name}: ${log.message}",
                    color = log.level.toColor(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onValueChange: (Float) -> Unit) {
    Column {
        Text("$label: $display", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun NumericSetting(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter(Char::isDigit).take(4)
            onChange(text.toIntOrNull() ?: value)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun PermissionsCard(
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    hasMediaProjection: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestMediaProjection: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Permisos Android", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            PermissionRow("Ventana flotante", "Overlay para controles rápidos", hasOverlay, onRequestOverlay)
            PermissionRow("Accesibilidad", "Ejecución segura de taps/swipes", hasAccessibility, onRequestAccessibility)
            PermissionRow("Captura de pantalla", "Necesario para la visión del bot", hasMediaProjection, onRequestMediaProjection)
        }
    }
}

@Composable
fun PermissionRow(title: String, subtitle: String, enabled: Boolean, onRequest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (enabled) Color(0xFF61D394) else Color(0xFFFFD166),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (enabled) Text("Activo", color = Color(0xFF61D394), style = MaterialTheme.typography.labelMedium) else Button(onClick = onRequest) { Text("Activar") }
    }
}

@Composable
private fun BotLogLevel.toColor(): Color = when (this) {
    BotLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    BotLogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    BotLogLevel.WARNING -> Color(0xFFFFD166)
    BotLogLevel.ERROR -> MaterialTheme.colorScheme.error
    BotLogLevel.SUCCESS -> Color(0xFF61D394)
}

private fun formatTime(timestampMillis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestampMillis))

private fun isAccessibilityServiceEnabled(context: Context, packageName: String): Boolean {
    val expectedServiceName = "$packageName/${BotAccessibilityService::class.java.name}"
    val enabled = runCatching {
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    }.getOrDefault(0)
    if (enabled != 1) return false
    val settingValue = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(settingValue)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expectedServiceName, ignoreCase = true)) return true
    }
    return BotAccessibilityService.isReady
}

