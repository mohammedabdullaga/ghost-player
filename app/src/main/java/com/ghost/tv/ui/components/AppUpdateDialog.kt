package com.ghost.tv.ui.components

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ghost.tv.BuildConfig
import com.ghost.tv.R
import com.ghost.tv.data.update.AppUpdateManager
import com.ghost.tv.data.update.UpdateState
import com.ghost.tv.domain.model.AppUpdateConfig
import com.ghost.tv.ui.theme.AppColors
import com.ghost.tv.util.isTelevisionDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppUpdateDialog(
    updateConfig: AppUpdateConfig,
    appUpdateManager: AppUpdateManager,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isTv = context.isTelevisionDevice()
    val scope = rememberCoroutineScope()
    val updateState by appUpdateManager.updateState.collectAsState()

    val isForced = updateConfig.force_update || BuildConfig.VERSION_CODE < updateConfig.min_supported_version_code
    val primaryFocusRequester = remember { FocusRequester() }

    if (isForced) {
        BackHandler {
            Log.d("AppUpdateDialog", "Forced update active — system back press intercepted")
        }
    }

    LaunchedEffect(Unit) {
        if (isTv) {
            delay(200)
            try { primaryFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isForced && updateState !is UpdateState.Downloading) {
                appUpdateManager.resetState()
                onDismissRequest()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isForced && updateState !is UpdateState.Downloading,
            dismissOnClickOutside = !isForced && updateState !is UpdateState.Downloading
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1A1A1A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            tonalElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with App Update Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(AppColors.ArenaOrange.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = AppColors.ArenaOrange,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isForced) "Required App Update" else "Update Available",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "v${updateConfig.resolvedVersionName} (Current: v${BuildConfig.VERSION_NAME})",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Body State Machine
                when (val state = updateState) {
                    is UpdateState.Idle -> {
                        // Release Notes Section
                        if (updateConfig.release_notes.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "What's New:",
                                    color = AppColors.ArenaOrange,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = updateConfig.release_notes,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!isForced) {
                                OutlinedButton(
                                    onClick = {
                                        appUpdateManager.resetState()
                                        onDismissRequest()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Text("LATER", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        appUpdateManager.downloadAndInstall(updateConfig)
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.ArenaOrange,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .focusRequester(primaryFocusRequester)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("UPDATE NOW", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Downloading update... ${(state.progress * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = AppColors.ArenaOrange,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )

                            if (state.totalBytes > 0) {
                                Text(
                                    text = "${state.downloadedBytes / 1024 / 1024} MB / ${state.totalBytes / 1024 / 1024} MB",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    is UpdateState.VerifyingChecksum -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = AppColors.ArenaOrange, modifier = Modifier.size(32.dp))
                            Text(
                                text = "Verifying package integrity (SHA-256)...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // TASK 1: Permission Required Dialog State
                    is UpdateState.PermissionRequired -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = AppColors.ArenaOrange, modifier = Modifier.size(24.dp))
                                Text(
                                    text = "Permission Required",
                                    color = AppColors.ArenaOrange,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "This app needs permission to install updates — you'll be taken to a system settings screen, please enable 'Allow from this source' and return.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )

                            Button(
                                onClick = { appUpdateManager.requestInstallPermission() },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.ArenaOrange,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .focusRequester(primaryFocusRequester)
                            ) {
                                Text("OPEN SYSTEM SETTINGS", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }

                    is UpdateState.ReadyToInstall -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Download complete. Ready to install.",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = { appUpdateManager.proceedToInstall(state.apkFile) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.ArenaOrange,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Text("INSTALL NOW", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }

                    // TASK 2: Error / Checksum Mismatch State
                    is UpdateState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                                Text(
                                    text = "Update Error",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = state.message,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )

                            Button(
                                onClick = {
                                    scope.launch {
                                        appUpdateManager.downloadAndInstall(updateConfig)
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.ArenaOrange,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Text("RETRY DOWNLOAD", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
