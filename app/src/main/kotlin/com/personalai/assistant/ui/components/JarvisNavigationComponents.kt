package com.personalai.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalai.assistant.ui.theme.JarvisCyanElectric
import com.personalai.assistant.ui.theme.JarvisGlassBorder
import com.personalai.assistant.ui.theme.JarvisNavyCard
import com.personalai.assistant.ui.theme.JarvisNavyDeep
import com.personalai.assistant.ui.theme.JarvisSpaceBlack
import com.personalai.assistant.ui.theme.JarvisStatusOnline
import com.personalai.assistant.ui.theme.JarvisTextPrimary
import com.personalai.assistant.ui.theme.JarvisTextSecondary
import com.personalai.assistant.ui.theme.JarvisVioletNeon

enum class JarvisNavDestination(
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    HOME("Core", Icons.Default.Home, "Primary Dashboard"),
    CHAT("Chat", Icons.Default.AutoAwesome, "Local RAG Assistant"),
    MEETINGS("Minutes", Icons.Default.Hearing, "Meeting Intelligence"),
    TASKS("Protocols", Icons.Default.CheckCircle, "Action Items"),
    MEMORY("Memory", Icons.Default.Psychology, "Sub-ms Search & Notes"),
    SETTINGS("Config", Icons.Default.Settings, "System Config")
}

@Composable
fun JarvisTopAppBar(
    title: String = "J.A.R.V.I.S.",
    subtitle: String = "QUANTUM AI // OFFLINE READY",
    onNavigateToSettings: (() -> Unit)? = null,
    onToggleSidebar: (() -> Unit)? = null,
    isSidebarOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        JarvisSpaceBlack,
                        JarvisNavyDeep.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(JarvisGlassBorder, Color.Transparent)
                ),
                shape = androidx.compose.ui.graphics.RectangleShape
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToggleSidebar != null) {
                    IconButton(
                        onClick = onToggleSidebar,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isSidebarOpen) Icons.Default.MenuOpen else Icons.Default.Menu,
                            contentDescription = if (isSidebarOpen) "Collapse Navigation" else "Expand Navigation",
                            tint = JarvisCyanElectric
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // AI Arc Core mini logo
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(JarvisNavyCard)
                        .border(1.5.dp, JarvisCyanElectric, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(JarvisVioletNeon)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = JarvisTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(JarvisStatusOnline)
                        )
                    }
                    Text(
                        text = subtitle,
                        color = JarvisCyanElectric.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                JarvisStatusBadge(
                    label = "ONLINE",
                    statusColor = JarvisStatusOnline,
                    isPulsing = true
                )

                if (onNavigateToSettings != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "System Settings",
                            tint = JarvisTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JarvisBottomNavigationBar(
    currentDestination: JarvisNavDestination,
    onNavigate: (JarvisNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, JarvisGlassBorder)
            .navigationBarsPadding(),
        containerColor = JarvisSpaceBlack,
        tonalElevation = 0.dp
    ) {
        JarvisNavDestination.entries.forEach { destination ->
            val isSelected = currentDestination == destination
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.description,
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = {
                    Text(
                        text = destination.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = 0.4.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = JarvisCyanElectric,
                    selectedTextColor = JarvisCyanElectric,
                    unselectedIconColor = JarvisTextSecondary,
                    unselectedTextColor = JarvisTextSecondary,
                    indicatorColor = JarvisNavyCard
                )
            )
        }
    }
}

@Composable
fun JarvisNavigationRailBar(
    currentDestination: JarvisNavDestination,
    onNavigate: (JarvisNavDestination) -> Unit,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .width(if (isExpanded) 200.dp else 72.dp)
            .border(1.dp, JarvisGlassBorder)
            .statusBarsPadding(),
        containerColor = JarvisSpaceBlack,
        header = {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (onToggleExpand != null) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                            contentDescription = "Toggle Sidebar",
                            tint = JarvisCyanElectric
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(JarvisNavyCard)
                        .border(1.2.dp, JarvisCyanElectric, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(JarvisVioletNeon))
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                JarvisNavDestination.entries.forEach { destination ->
                    val isSelected = currentDestination == destination
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { onNavigate(destination) },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.description,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = destination.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = JarvisCyanElectric,
                            selectedTextColor = JarvisCyanElectric,
                            unselectedIconColor = JarvisTextSecondary,
                            unselectedTextColor = JarvisTextSecondary,
                            indicatorColor = JarvisNavyCard
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                JarvisStatusBadge(label = "JARVIS", statusColor = JarvisStatusOnline)
            }
        }
    }
}
