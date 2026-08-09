package com.eliteprime.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.XtreamAccount
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.theme.AppColors

private enum class SettingCategory(val label: String, val icon: ImageVector) {
    ACCOUNT("Account", Icons.Default.Person),
    GENERAL("General", Icons.Default.Settings),
    PLAYBACK("Playback", Icons.Default.PlayCircle),
    ABOUT("About", Icons.Default.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf(SettingCategory.ACCOUNT) }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        titleContentColor = Color.White
                    ),
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                )
            }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // --- SIDEBAR ---
                Surface(
                    modifier = Modifier.weight(0.32f).fillMaxHeight(),
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SettingCategory.entries.forEach { category ->
                            SidebarItem(
                                label = category.label,
                                icon = category.icon,
                                isSelected = selectedCategory == category,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }

                // --- CONTENT ---
                Surface(
                    modifier = Modifier.weight(0.68f).fillMaxHeight(),
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(selectedCategory.icon, contentDescription = null, tint = AppColors.ArenaOrange, modifier = Modifier.size(18.dp))
                            Text(
                                text = selectedCategory.label.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

                        when (selectedCategory) {
                            SettingCategory.ACCOUNT -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    item {
                                        account?.let {
                                            AccountCard(it, onSync = { viewModel.syncAll() })
                                        }
                                    }
                                    item {
                                        LogoutButton(onLogout = {
                                            viewModel.logout()
                                            onLogout()
                                        })
                                    }
                                }
                            }
                            else -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Coming Soon", color = Color.White.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(if (isSelected) AppColors.ArenaOrange else Color.Transparent, RoundedCornerShape(50))
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) AppColors.ArenaOrange else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountCard(account: XtreamAccount, onSync: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(account.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(account.username, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Surface(color = AppColors.ArenaOrange, shape = RoundedCornerShape(8.dp)) {
                    Text("ACTIVE", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
            }
            
            Button(
                onClick = onSync,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.ArenaOrange)
            ) {
                Text("Refresh Data", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LogoutButton(onLogout: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onLogout() },
        color = Color(0xFFEF5350).copy(alpha = 0.1f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFEF5350))
            Column {
                Text("Log Out", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Sign out and clear all local data", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
    }
}
