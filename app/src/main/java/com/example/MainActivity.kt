package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ImmersiveBorder
import kotlinx.coroutines.delay
import java.util.Locale

import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.example.smartAnalyzeRequest

import android.content.Intent
import android.provider.Settings
import android.content.ComponentName
import android.text.TextUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FishBotDashboard()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FishBotDashboard() {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    
    // Function to check accessibility
    fun isAccessibilityServiceEnabled(): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
        val stringColonSplitter = TextUtils.SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                stringColonSplitter.setString(settingValue)
                while (stringColonSplitter.hasNext()) {
                    val accessibilityService = stringColonSplitter.next()
                    if (accessibilityService.equals(
                            "${context.packageName}/${BotAccessibilityService::class.java.name}",
                            ignoreCase = true
                        )
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    var hasAccessibilityPermission by remember { mutableStateOf(isAccessibilityServiceEnabled()) }

    // Update permissions on focus
    LaunchedEffect(Unit) {
        while(true) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasAccessibilityPermission = isAccessibilityServiceEnabled()
            delay(1000)
        }
    }

    val scrollState = rememberScrollState()
    var isRunning by remember { mutableStateOf(false) }
    var fishCaughtCount by remember { mutableStateOf(0) }
    var logs by remember { mutableStateOf(listOf("Sistema inicializado. Esperando configuración.")) }

    // Mock the backend process
    LaunchedEffect(isRunning) {
        if (isRunning) {
            logs = listOf("🚀 Iniciando bot de pesca...") + logs
            delay(1000)
            logs = listOf("🔎 Buscando pantalla de pesca...") + logs
            while(isRunning) {
                delay(3000)
                fishCaughtCount++
                val msg = listOf(
                    "¡Picada detectada!",
                    "Minijuego completado con éxito.",
                    "Pez capturado. Total: $fishCaughtCount"
                ).random()
                logs = (listOf(msg) + logs).take(5)
            }
        } else {
            if (fishCaughtCount > 0) {
                logs = listOf("🛑 Bot detenido.") + logs
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fish Bot Avanzado", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Panel de Control Principal
            ControlPanel(
                isRunning = isRunning,
                fishCount = fishCaughtCount,
                logs = logs,
                onToggleBot = { isRunning = !isRunning }
            )
            
            HorizontalDivider()

            PermissionsCard(
                hasOverlay = hasOverlayPermission,
                hasAccessibility = hasAccessibilityPermission,
                onRequestOverlay = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                },
                onRequestAccessibility = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )

            HorizontalDivider()

            SmartAssistantCard()

            HorizontalDivider()

            Text(
                "Calibración Visual",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Catch Screen Config
            ImageCalibrationCard(
                title = "1. Pantalla de Pesca (Catch Screen)",
                description = "Sube una imagen del texto 'Fishing' o botón de acción.",
                sliderLabel = "Sensibilidad de Captura",
                initialSliderValue = 0.80f
            )

            // Bite Prompt Config
            ImageCalibrationCard(
                title = "2. Anillo de Picada (Bite Prompt)",
                description = "Sube la imagen del indicador azul brillante de picada.",
                sliderLabel = "Sensibilidad de Picada",
                initialSliderValue = 0.85f
            )

            // Minigame Config
            ImageCalibrationCard(
                title = "3. Minijuego (Green Zone)",
                description = "Sube un recorte de la zona verde de la barra.",
                sliderLabel = "Tolerancia de Seguimiento",
                initialSliderValue = 0.15f
            )

            HorizontalDivider()

            Text(
                "Rutinas y Automatización",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            RoutineSettings()

            HorizontalDivider()

            Text(
                "Configuración de Memoria (Frida)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            MemorySettings()

            Spacer(modifier = Modifier.padding(24.dp))
        }
    }
}

@Composable
fun ControlPanel(
    isRunning: Boolean,
    fishCount: Int,
    logs: List<String>,
    onToggleBot: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "Estado del Bot",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        if (isRunning) "Ejecutándose..." else "Detenido",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onToggleBot,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("submit_button").height(56.dp)
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Bot"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRunning) "DETENER" else "INICIAR")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Peces Capturados: $fishCount",
                        color = Color.Green,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Último evento:",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (logs.isNotEmpty()) {
                        Text(
                            logs.first(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImageCalibrationCard(
    title: String,
    description: String,
    sliderLabel: String,
    initialSliderValue: Float
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> imageUri = uri }
    )
    var sliderValue by remember { mutableFloatStateOf(initialSliderValue) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Subir Imagen")
                    Spacer(Modifier.width(8.dp))
                    Text(if (imageUri != null) "Cambiar Referencia" else "Subir Imagen")
                }
            }

            AnimatedVisibility(visible = imageUri != null) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Imagen de Referencia",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("$sliderLabel: ${String.format(Locale.US, "%.2f", sliderValue)}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun RoutineSettings() {
    var restockInterval by remember { mutableStateOf("95") }
    var sellInterval by remember { mutableStateOf("50") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = restockInterval,
                onValueChange = { restockInterval = it.filter { char -> char.isDigit() } },
                label = { Text("Comprar carnada (cada X peces)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = sellInterval,
                onValueChange = { sellInterval = it.filter { char -> char.isDigit() } },
                label = { Text("Vender inventario (cada X peces)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun SmartAssistantCard() {
    var prompt by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> imageUri = uri }
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Asistente Inteligente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Describe la configuración de forma natural. Sube una captura si necesitas que la IA analice la pantalla.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Instrucciones") },
                placeholder = { Text("Ej: Haz que la sensibilidad de captura sea más alta y compra carnada cada 10 peces.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(if (imageUri != null) Icons.Default.Check else Icons.Default.Image, contentDescription = "Subir Imagen")
                    Spacer(Modifier.width(8.dp))
                    Text(if (imageUri != null) "Imagen Lista" else "Subir Imagen")
                }
                
                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            val bitmap = imageUri?.let { uri ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                        decoder.isMutableRequired = true
                                    }
                                } else {
                                    @Suppress("DEPRECATION")
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                }
                            }
                            resultText = smartAnalyzeRequest(prompt, bitmap)
                            isProcessing = false
                        }
                    },
                    enabled = prompt.isNotEmpty() && !isProcessing,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Analizar")
                        Spacer(Modifier.width(8.dp))
                        Text("Analizar")
                    }
                }
            }

            AnimatedVisibility(visible = resultText.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text(
                        text = resultText,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MemorySettings() {
    var packageName by remember { mutableStateOf("com.neverness.everness") }
    var moduleName by remember { mutableStateOf("libil2cpp.so") }
    
    var offsetFishState by remember { mutableStateOf("0x100") }
    var offsetBite by remember { mutableStateOf("0x104") }
    var offsetMinigame by remember { mutableStateOf("0x10C") }
    var offsetPlayerPos by remember { mutableStateOf("0x200") }
    var offsetFishPos by remember { mutableStateOf("0x204") }
    var offsetZoneStart by remember { mutableStateOf("0x208") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Ajustes de Frida", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Parámetros para la lectura de memoria del proceso de juego.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text("App Package Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = moduleName,
                onValueChange = { moduleName = it },
                label = { Text("Module Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Offsets Principales (Hex)", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = offsetFishState,
                    onValueChange = { offsetFishState = it },
                    label = { Text("State") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = offsetBite,
                    onValueChange = { offsetBite = it },
                    label = { Text("Bite") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = offsetMinigame,
                    onValueChange = { offsetMinigame = it },
                    label = { Text("Minigame") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = offsetPlayerPos,
                    onValueChange = { offsetPlayerPos = it },
                    label = { Text("Player Pos") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = offsetFishPos,
                    onValueChange = { offsetFishPos = it },
                    label = { Text("Fish Pos") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = offsetZoneStart,
                    onValueChange = { offsetZoneStart = it },
                    label = { Text("Zone Start") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun PermissionsCard(
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, ImmersiveBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Permisos del Sistema (Bot Nativo)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Para poder simular toques tal como GramAddict desde la propia aplicación, requerimos estos permisos clave.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Overlay Permission
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasOverlay) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasOverlay) Color.Green else Color.Yellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Ventana Flotante", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Botón In-game", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!hasOverlay) {
                    Button(onClick = onRequestOverlay) {
                        Text("Activar")
                    }
                } else {
                    Text("Activo", color = Color.Green, style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Accessibility Permission
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasAccessibility) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasAccessibility) Color.Green else Color.Yellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Accesibilidad", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Simular Toques", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!hasAccessibility) {
                    Button(onClick = onRequestAccessibility) {
                        Text("Activar")
                    }
                } else {
                    Text("Activo", color = Color.Green, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
