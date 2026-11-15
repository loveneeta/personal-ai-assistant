package com.personalai.assistant.permissions.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalai.assistant.permissions.JarvisPermissionManager
import com.personalai.assistant.permissions.PermissionCategory
import com.personalai.assistant.permissions.PermissionItem
import com.personalai.assistant.ui.components.JarvisGlassCard
import com.personalai.assistant.ui.components.JarvisStatusBadge
import com.personalai.assistant.ui.theme.JarvisCyanElectric
import com.personalai.assistant.ui.theme.JarvisGlassBorder
import com.personalai.assistant.ui.theme.JarvisNavyCard
import com.personalai.assistant.ui.theme.JarvisNavyDeep
import com.personalai.assistant.ui.theme.JarvisSpaceBlack
import com.personalai.assistant.ui.theme.JarvisSpaceVoid
import com.personalai.assistant.ui.theme.JarvisStatusError
import com.personalai.assistant.ui.theme.JarvisStatusOnline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDashboardScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionManager = remember { JarvisPermissionManager.getInstance(context) }
    val permissions by permissionManager.permissionsState.collectAsStateWithLifecycle()

    // Refresh status when user returns from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionManager.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Dynamic runtime permission request launcher
    var pendingRequestPermissions by remember { mutableStateOf<Array<String>>(emptyArray()) }
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionManager.refreshPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SECURITY & PERMISSION MATRIX",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.0.sp,
                            color = JarvisCyanElectric
                        )
                        Text(
                            text = "Executive Access Controls & Revocation",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = JarvisCyanElectric
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JarvisSpaceBlack
                )
            )
        },
        containerColor = JarvisSpaceVoid,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Privacy & Sandboxing Guarantee Banner
            item {
                Spacer(modifier = Modifier.height(6.dp))
                JarvisGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowAccent = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(JarvisCyanElectric.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = JarvisCyanElectric,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ZERO-LEAK PRIVACY GUARANTEE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisCyanElectric,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "All granted permissions are strictly executed on-device. No messages, contacts, or calendar entries are ever shared or uploaded.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Group permissions by category
            PermissionCategory.values().forEach { category ->
                val categoryPermissions = permissions.filter { it.category == category }
                if (categoryPermissions.isNotEmpty()) {
                    item {
                        Text(
                            text = category.displayName.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyanElectric,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.0.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(categoryPermissions, key = { it.id }) { item ->
                        PermissionCard(
                            item = item,
                            onRequestPermission = {
                                if (item.isSpecialPermission && item.settingsAction != null) {
                                    context.startActivity(permissionManager.createNotificationListenerSettingsIntent())
                                } else if (item.manifestPermissions.isNotEmpty()) {
                                    pendingRequestPermissions = item.manifestPermissions.toTypedArray()
                                    multiplePermissionLauncher.launch(pendingRequestPermissions)
                                }
                            },
                            onOpenSettings = {
                                context.startActivity(permissionManager.createAppSettingsIntent())
                            }
                        )
                    }
                }
            }

            // Universal Revocation Instructions Footer
            item {
                JarvisGlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HOW TO REVOKE PERMISSIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You retain absolute control over JARVIS. To revoke any granted permission at any time, tap 'Manage in App Settings' below, or open Android Settings > Apps > Personal AI Assistant > Permissions.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(permissionManager.createAppSettingsIntent())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(JarvisGlassBorder))
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = JarvisCyanElectric
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Open System App Settings",
                                fontSize = 12.sp,
                                color = JarvisCyanElectric
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun PermissionCard(
    item: PermissionItem,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    JarvisGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        borderColor = if (item.isGranted) JarvisGlassBorder else JarvisStatusError.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon
                val icon = when (item.category) {
                    PermissionCategory.AUDIO -> Icons.Default.Mic
                    PermissionCategory.NOTIFICATIONS -> Icons.Default.Notifications
                    PermissionCategory.COMMUNICATIONS -> Icons.Default.Phone
                    PermissionCategory.CONTACTS -> Icons.Default.Contacts
                    PermissionCategory.CALENDAR -> Icons.Default.CalendarMonth
                    PermissionCategory.STORAGE -> Icons.Default.Folder
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (item.isGranted) JarvisCyanElectric.copy(alpha = 0.12f) else JarvisStatusError.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (item.isGranted) JarvisCyanElectric else JarvisStatusError,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.purpose,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                JarvisStatusBadge(
                    label = if (item.isGranted) "GRANTED" else "ACTION REQUIRED",
                    statusColor = if (item.isGranted) JarvisStatusOnline else JarvisStatusError,
                    isPulsing = !item.isGranted
                )
            }

            // Expanded audit & revocation details
            AnimatedVisibility(visible = expanded || !item.isGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(JarvisNavyDeep)
                            .border(0.6.dp, JarvisGlassBorder, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "ACCESSIBLE DATA SCOPE:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisCyanElectric,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.dataAccessible,
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )

                            if (item.lastAccessedTimestamp != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(item.lastAccessedTimestamp))
                                Text(
                                    text = "Last Exercised by Agent: $dateStr",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!item.isGranted) {
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisCyanElectric,
                                contentColor = JarvisSpaceBlack
                            )
                        ) {
                            Text(
                                text = if (item.isSpecialPermission) "Configure in System Settings" else "Grant Access Permission",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(JarvisGlassBorder))
                        ) {
                            Text(
                                text = "Revoke in App Settings",
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
        }
    }
}
