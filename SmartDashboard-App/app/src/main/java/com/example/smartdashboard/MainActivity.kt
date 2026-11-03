package com.example.smartdashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.smartdashboard.ui.theme.AccentCyan
import com.example.smartdashboard.ui.theme.AccentPurple
import com.example.smartdashboard.ui.theme.DarkBackground
import com.example.smartdashboard.ui.theme.DarkBorder
import com.example.smartdashboard.ui.theme.DarkSurface
import com.example.smartdashboard.ui.theme.DarkSurfaceVariant
import com.example.smartdashboard.ui.theme.SmartDashboardTheme
import com.example.smartdashboard.ui.theme.StatusAmber
import com.example.smartdashboard.ui.theme.StatusGreen
import com.example.smartdashboard.ui.theme.StatusRed
import com.example.smartdashboard.ui.theme.TextMuted
import com.example.smartdashboard.ui.theme.TextPrimary
import com.example.smartdashboard.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var repository: DashboardRepository
    private lateinit var settings: DashboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = DashboardRepository.getInstance(applicationContext)
        settings = DashboardSettings.getInstance(applicationContext)

        setContent {
            SmartDashboardTheme {
                MainScreen(
                    repository = repository,
                    settings = settings,
                    onOpenNotificationSettings = { openNotificationListenerSettings() },
                    onRequestCallPermission = { /* handled by rememberLauncherForActivityResult */ },
                    onStartCallMonitor = { (application as? AthrvvApplication)?.callStateMonitor?.start() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        repository.refreshPermissionStatuses(applicationContext)
        (application as? AthrvvApplication)?.callStateMonitor?.start()
    }

    private fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(appDetails)
        }
    }
}

@Composable
fun MainScreen(
    repository: DashboardRepository,
    settings: DashboardSettings,
    onOpenNotificationSettings: () -> Unit,
    onRequestCallPermission: () -> Unit,
    onStartCallMonitor: () -> Unit
) {
    val context = LocalContext.current
    val connectionStatus by repository.connectionStatus.collectAsState()
    val dashboardState by repository.currentDashboardState.collectAsState()
    val recentEvents by repository.recentEvents.collectAsState()
    val isNotifAccessGranted by repository.isNotificationAccessGranted.collectAsState()
    val isPhonePermGranted by repository.isPhoneStatePermissionGranted.collectAsState()
    val isMediaActive by repository.isMediaListenerActive.collectAsState()
    val activeMusic by repository.activeMusic.collectAsState()

    var showPermissionInfoDialog by remember { mutableStateOf(false) }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var redactSensitive by remember { mutableStateOf(settings.redactSensitive) }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        repository.refreshPermissionStatuses(context)
        if (isGranted) {
            onStartCallMonitor()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopHeader(
                connectionStatus = connectionStatus,
                isChecking = isCheckingConnection,
                onRefresh = {
                    isCheckingConnection = true
                    repository.checkConnection { isCheckingConnection = false }
                },
                onHelp = { showPermissionInfoDialog = true }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 1. Server Configuration & Connection Card
            item {
                ServerConfigCard(
                    settings = settings,
                    connectionStatus = connectionStatus,
                    isChecking = isCheckingConnection,
                    onTestConnection = {
                        isCheckingConnection = true
                        repository.checkConnection { isCheckingConnection = false }
                    }
                )
            }

            // 2. ESP32 TFT Screen Live Mirror
            item {
                DashboardMirrorCard(dashboardState = dashboardState)
            }

            // 3. Permissions and Service Status
            item {
                PermissionsCard(
                    isNotificationGranted = isNotifAccessGranted,
                    isPhoneGranted = isPhonePermGranted,
                    isMediaActive = isMediaActive,
                    activeMusic = activeMusic,
                    onGrantNotification = onOpenNotificationSettings,
                    onRequestPhonePermission = {
                        phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    },
                    onExplainPermissions = { showPermissionInfoDialog = true }
                )
            }

            // 4. Manual Diagnostics and Testing Suite
            item {
                ManualControlsCard(
                    onSendTestNotification = {
                        repository.postNotificationEvent(
                            app = "WhatsApp",
                            sender = "Ramza",
                            text = "I Love You Athrvv",
                            isManualTest = true
                        )
                    },
                    onToggleMusic = { playing ->
                        repository.postMusicEvent(
                            playing = playing,
                            title = "Midnight City",
                            artist = "M83",
                            source = "Spotify",
                            isManualTest = true
                        )
                    },
                    onSendCallState = { status ->
                        repository.postCallEvent(
                            status = status,
                            caller = "Phone call",
                            isManualTest = true
                        )
                    }
                )
            }

            // 5. Activity Log & Feed
            item {
                ActivityLogHeader(
                    redactSensitive = redactSensitive,
                    onToggleRedact = {
                        redactSensitive = it
                        settings.redactSensitive = it
                    },
                    onClear = { repository.clearEvents() }
                )
            }

            if (recentEvents.isEmpty()) {
                item {
                    EmptyActivityPlaceholder()
                }
            } else {
                items(recentEvents, key = { it.id }) { event ->
                    RecentEventItem(event = event, redactSensitive = redactSensitive)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showPermissionInfoDialog) {
        PermissionsExplanationDialog(onDismiss = { showPermissionInfoDialog = false })
    }
}

@Composable
fun TopHeader(
    connectionStatus: ConnectionStatus,
    isChecking: Boolean,
    onRefresh: () -> Unit,
    onHelp: () -> Unit
) {
    Surface(
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Athrvv",
                        color = AccentCyan,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ESP32 BRIDGE",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "Companion for 1.8\" TFT Dashboard",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Online/Offline badge
                StatusBadge(isConnected = connectionStatus.isConnected)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onRefresh,
                    enabled = !isChecking,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AccentCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(text = "↻", color = TextPrimary, fontSize = 20.sp)
                    }
                }
                IconButton(
                    onClick = onHelp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(text = "ⓘ", color = TextSecondary, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun StatusBadge(isConnected: Boolean) {
    val bgColor = if (isConnected) StatusGreen.copy(alpha = 0.15f) else StatusRed.copy(alpha = 0.15f)
    val dotColor = if (isConnected) StatusGreen else StatusRed
    val text = if (isConnected) "ONLINE" else "OFFLINE"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            color = dotColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ServerConfigCard(
    settings: DashboardSettings,
    connectionStatus: ConnectionStatus,
    isChecking: Boolean,
    onTestConnection: () -> Unit
) {
    var urlText by remember { mutableStateOf(settings.serverUrl) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Flask Dashboard Server",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (connectionStatus.responseTimeMs > 0 && connectionStatus.isConnected) {
                    Text(
                        text = "${connectionStatus.responseTimeMs} ms",
                        color = StatusGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    hasUnsavedChanges = it != settings.serverUrl
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server Base URL", color = TextSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    settings.serverUrl = urlText
                    urlText = settings.serverUrl
                    hasUnsavedChanges = false
                    focusManager.clearFocus()
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasUnsavedChanges) {
                    Button(
                        onClick = {
                            settings.serverUrl = urlText
                            urlText = settings.serverUrl
                            hasUnsavedChanges = false
                            focusManager.clearFocus()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save URL", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onTestConnection,
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasUnsavedChanges) DarkSurfaceVariant else AccentCyan,
                        contentColor = if (hasUnsavedChanges) TextPrimary else DarkBackground
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Test Connection", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (!connectionStatus.isConnected && connectionStatus.lastError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(StatusRed.copy(alpha = 0.12f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Error: ${connectionStatus.lastError}",
                        color = StatusRed,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardMirrorCard(dashboardState: DashboardState?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ESP32 TFT Live State",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val screenName = dashboardState?.screen?.uppercase() ?: "UNKNOWN"
                val screenColor = when (screenName) {
                    "CALL" -> StatusRed
                    "NOTIFICATION" -> StatusAmber
                    "MUSIC" -> StatusGreen
                    else -> AccentCyan
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(screenColor.copy(alpha = 0.15f))
                        .border(1.dp, screenColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SCREEN: $screenName",
                        color = screenColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulated TFT Display Box (160x128 aspect ratio mockup)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                if (dashboardState == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Waiting for server sync...",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        when (dashboardState.screen) {
                            "call" -> {
                                Text(
                                    text = "⚡ INCOMING / ACTIVE CALL",
                                    color = StatusRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dashboardState.call.caller.ifBlank { "Phone Call" },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Status: ${dashboardState.call.status.uppercase()}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            "notification" -> {
                                Text(
                                    text = "✉ ${dashboardState.notification.app.uppercase()}",
                                    color = StatusAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dashboardState.notification.sender,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = dashboardState.notification.text,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            "music" -> {
                                Text(
                                    text = "♫ ${dashboardState.music.source.uppercase()}",
                                    color = StatusGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dashboardState.music.title.ifBlank { "No Title" },
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = dashboardState.music.artist.ifBlank { "Unknown Artist" },
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            else -> {
                                Text(
                                    text = "⏰ CLOCK & IDLE",
                                    color = AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(Date(dashboardState.time * 1000L))
                                Text(
                                    text = timeFormatted,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionsCard(
    isNotificationGranted: Boolean,
    isPhoneGranted: Boolean,
    isMediaActive: Boolean,
    activeMusic: MusicState?,
    onGrantNotification: () -> Unit,
    onRequestPhonePermission: () -> Unit,
    onExplainPermissions: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Integration",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onExplainPermissions) {
                    Text("Why needed?", color = AccentCyan, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Notification Listener
            IntegrationRow(
                title = "Notification Listener",
                subtitle = if (isNotificationGranted) "Granted & Active" else "Required to capture WhatsApp, messages, etc.",
                isEnabled = isNotificationGranted,
                actionText = if (isNotificationGranted) "Active" else "Grant Access",
                onAction = onGrantNotification
            )

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))

            // Phone State
            IntegrationRow(
                title = "Phone Call State",
                subtitle = if (isPhoneGranted) "Granted (Ringing & Call detection)" else "Required for call detection",
                isEnabled = isPhoneGranted,
                actionText = if (isPhoneGranted) "Granted" else "Allow",
                onAction = onRequestPhonePermission
            )

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))

            // Media Session (Spotify / YouTube)
            IntegrationRow(
                title = "Media Sessions (Spotify/YT)",
                subtitle = when {
                    !isNotificationGranted -> "Requires Notification Listener access"
                    isMediaActive -> if (activeMusic != null && activeMusic.title.isNotBlank()) {
                        "${activeMusic.source}: ${activeMusic.title}"
                    } else {
                        "Listening for active playback"
                    }
                    else -> "Standby"
                },
                isEnabled = isMediaActive,
                actionText = if (isMediaActive) "Listening" else "Waiting",
                onAction = onGrantNotification
            )
        }
    }
}

@Composable
fun IntegrationRow(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isEnabled) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(StatusGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionText,
                    color = StatusGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = DarkBackground
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(text = actionText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualControlsCard(
    onSendTestNotification: () -> Unit,
    onToggleMusic: (Boolean) -> Unit,
    onSendCallState: (String) -> Unit
) {
    var isMusicPlaying by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Manual Diagnostic Testing",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Simulate events directly to test ESP32 TFT priority and display",
                color = TextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Test Notification Button
                OutlinedButton(
                    onClick = onSendTestNotification,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAmber)
                ) {
                    Text("✉ Test Notification", fontSize = 12.sp)
                }

                // Music Play / Pause Toggle
                OutlinedButton(
                    onClick = {
                        isMusicPlaying = !isMusicPlaying
                        onToggleMusic(isMusicPlaying)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isMusicPlaying) StatusGreen else TextSecondary
                    )
                ) {
                    Text(
                        text = if (isMusicPlaying) "⏸ Pause Music" else "♫ Play Spotify",
                        fontSize = 12.sp
                    )
                }

                // Call Ringing Button
                OutlinedButton(
                    onClick = { onSendCallState("ringing") },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed)
                ) {
                    Text("⚡ Ringing", fontSize = 12.sp)
                }

                // Call Active Button
                OutlinedButton(
                    onClick = { onSendCallState("active") },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple)
                ) {
                    Text("📞 Active Call", fontSize = 12.sp)
                }

                // Call Ended Button
                OutlinedButton(
                    onClick = { onSendCallState("ended") },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("⏹ End Call", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ActivityLogHeader(
    redactSensitive: Boolean,
    onToggleRedact: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Recent Activity Feed",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Events forwarded to ESP32 server",
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (redactSensitive) "Masked" else "Visible",
                color = TextMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = redactSensitive,
                onCheckedChange = onToggleRedact,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentCyan,
                    checkedTrackColor = DarkSurfaceVariant,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            TextButton(onClick = onClear) {
                Text("Clear", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun RecentEventItem(event: RecentEvent, redactSensitive: Boolean) {
    val typeColor = when (event.type) {
        "call" -> StatusRed
        "notification" -> StatusAmber
        "music" -> StatusGreen
        "connection" -> AccentCyan
        else -> TextSecondary
    }

    val typeIcon = when (event.type) {
        "call" -> "⚡"
        "notification" -> "✉"
        "music" -> "♫"
        "connection" -> "⇌"
        else -> "•"
    }

    val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))

    val displayDetails = if (redactSensitive && event.type == "notification") {
        maskSensitiveContent(event.details)
    } else {
        event.details
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = typeIcon, color = typeColor, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = event.summary,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = timeString,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayDetails,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (event.success) StatusGreen else StatusRed)
            )
        }
    }
}

@Composable
fun EmptyActivityPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "No events yet", color = TextMuted, fontSize = 13.sp)
            Text(
                text = "Tap manual test buttons or play music on your phone",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun PermissionsExplanationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "Athrvv Permissions Overview",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Athrvv acts as an autonomous Wi-Fi companion for your ESP32 smart dashboard display.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = "• Notification Access: Allows Athrvv to capture incoming message sender/preview and forward to your ESP32 for 5 seconds.",
                    color = TextPrimary,
                    fontSize = 12.sp
                )
                Text(
                    text = "• Media Session: Granted alongside notification access to read Spotify and YouTube playing title/artist.",
                    color = TextPrimary,
                    fontSize = 12.sp
                )
                Text(
                    text = "• Phone State: Detects incoming and active calls so the ESP32 can prioritize caller screens over music and notifications.",
                    color = TextPrimary,
                    fontSize = 12.sp
                )
                Text(
                    text = "Privacy: Athrvv communicates strictly with your local Flask server over local Wi-Fi. No data leaves your personal network.",
                    color = AccentCyan,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
            ) {
                Text("Got It")
            }
        }
    )
}

fun maskSensitiveContent(text: String): String {
    val colonIndex = text.indexOf(':')
    return if (colonIndex != -1 && colonIndex < text.length - 1) {
        val sender = text.substring(0, colonIndex + 1)
        val body = text.substring(colonIndex + 1).trim()
        val masked = if (body.length > 3) "${body.take(2)}•••••••• (${body.length} chars)" else "••••"
        "$sender $masked"
    } else {
        if (text.length > 4) "${text.take(2)}••••••••" else "••••"
    }
}
