package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionManager(private val activity: ComponentActivity) {
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var overlaySettingsLauncher: ActivityResultLauncher<Intent>
    private lateinit var accessibilitySettingsLauncher: ActivityResultLauncher<Intent>
    
    private val _permissionsState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionsState: StateFlow<Map<String, Boolean>> = _permissionsState.asStateFlow()
    
    private var onPermissionsResult: ((Map<String, Boolean>) -> Unit)? = null

    init {
        setupPermissionLaunchers()
    }

    private fun setupPermissionLaunchers() {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            _permissionsState.value = permissions
            onPermissionsResult?.invoke(permissions)
        }
        
        overlaySettingsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // Check if overlay permission was granted
            val granted = canDrawOverlays()
            onPermissionsResult?.invoke(mapOf(Manifest.permission.SYSTEM_ALERT_WINDOW to granted))
        }
        
        accessibilitySettingsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // Check if accessibility service is enabled
            val granted = isAccessibilityServiceEnabled()
            onPermissionsResult?.invoke(mapOf("ACCESSIBILITY_SERVICE" to granted))
        }
    }

    fun requestRuntimePermissions(callback: (Map<String, Boolean>) -> Unit) {
        onPermissionsResult = callback
        val permissionsToRequest = getRequiredPermissions()
            .filter { !isPermissionGranted(it) }
            .toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            // All permissions already granted
            val status = getRequiredPermissions().associateWith { true }
            _permissionsState.value = status
            callback(status)
        }
    }

    fun requestOverlayPermission(callback: (Boolean) -> Unit) {
        if (canDrawOverlays()) {
            callback(true)
            return
        }
        
        onPermissionsResult = { callback(it[Manifest.permission.SYSTEM_ALERT_WINDOW] ?: false) }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            overlaySettingsLauncher.launch(intent)
        }
    }

    fun requestAccessibilityPermission(callback: (Boolean) -> Unit) {
        if (isAccessibilityServiceEnabled()) {
            callback(true)
            return
        }
        
        onPermissionsResult = { callback(it["ACCESSIBILITY_SERVICE"] ?: false) }
        
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilitySettingsLauncher.launch(intent)
    }

    fun requestMediaProjectionPermission() {
        // MediaProjection is requested through ActivityResultLauncher in MainActivity
        // This is a placeholder for consistency
    }

    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            isPermissionGranted(Manifest.permission.SYSTEM_ALERT_WINDOW)
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        return accessibilityManager?.isEnabled ?: false
    }

    fun areAllPermissionsGranted(): Boolean {
        return getRequiredPermissions().all { isPermissionGranted(it) } &&
                canDrawOverlays() &&
                isAccessibilityServiceEnabled()
    }

    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
        )

        // RECORD_AUDIO for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        // QUERY_ALL_PACKAGES for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissions.add(Manifest.permission.QUERY_ALL_PACKAGES)
        }

        return permissions
    }

    fun getPermissionStatus(): Map<String, Boolean> {
        val status = mutableMapOf<String, Boolean>()
        
        // Regular permissions
        for (permission in getRequiredPermissions()) {
            status[permission] = isPermissionGranted(permission)
        }
        
        // Special permissions
        status["OVERLAY"] = canDrawOverlays()
        status["ACCESSIBILITY_SERVICE"] = isAccessibilityServiceEnabled()
        
        return status
    }

    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        
        for (permission in getRequiredPermissions()) {
            if (!isPermissionGranted(permission)) {
                missing.add(permission)
            }
        }
        
        if (!canDrawOverlays()) {
            missing.add("OVERLAY")
        }
        
        if (!isAccessibilityServiceEnabled()) {
            missing.add("ACCESSIBILITY_SERVICE")
        }
        
        return missing
    }

    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.SYSTEM_ALERT_WINDOW, "OVERLAY" -> 
                "Permiso para mostrar la app encima de otras aplicaciones (Overlay)"
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE, "ACCESSIBILITY_SERVICE" ->
                "Permiso para acceder al servicio de accesibilidad (necesario para gestos automáticos)"
            Manifest.permission.READ_PHONE_STATE ->
                "Permiso para leer el estado del teléfono"
            Manifest.permission.RECORD_AUDIO ->
                "Permiso para grabar audio"
            Manifest.permission.INTERNET ->
                "Permiso para acceder a internet"
            Manifest.permission.QUERY_ALL_PACKAGES ->
                "Permiso para consultar aplicaciones instaladas"
            else -> "Permiso: $permission"
        }
    }
}
