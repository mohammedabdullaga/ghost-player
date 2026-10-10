package com.ghost.tv.ui.tv.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import com.ghost.tv.R
import com.ghost.tv.ui.components.ArenaLiveLogo
import com.ghost.tv.ui.screens.login.LoginState
import com.ghost.tv.ui.screens.login.LoginViewModel
import com.ghost.tv.ui.screens.login.SyncProgress
import com.ghost.tv.ui.tv.core.TvColors
import com.ghost.tv.ui.tv.core.TvSurfaceCard
import kotlinx.coroutines.delay

private val ArenaOrange = TvColors.Primary

@Composable
fun TvLoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val state by viewModel.loginState.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val pairingState by viewModel.pairingState.collectAsState()
    val isSecureEnabled by viewModel.isSecureConnectionEnabled.collectAsState()

    val loginButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state) {
        if (state is LoginState.Success) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startQrPairing()
        // Delay to ensure layout is ready before requesting focus
        delay(500)
        loginButtonFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 1100.dp).padding(32.dp),
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier.padding(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(64.dp)
            ) {
                // ── LEFT: Manual login ──────────────────────────────────
                Box(modifier = Modifier.weight(1.1f)) {
                    if (state is LoginState.Loading) {
                        TvSyncProgressView(syncProgress)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            ArenaLiveLogo(textSize = 28.sp)

                            Text(
                                text = stringResource(R.string.login_title),
                                color = ArenaOrange,
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                letterSpacing = 1.sp
                            )

                            TvLoginTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = stringResource(R.string.login_username),
                                icon = Icons.Default.Person
                            )

                            TvLoginTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = stringResource(R.string.login_password),
                                icon = Icons.Default.Lock,
                                isPassword = true,
                                passwordVisible = passwordVisible,
                                onTogglePasswordVisible = { passwordVisible = !passwordVisible }
                            )

                            // Login + VPN row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var isLoginFocused by remember { mutableStateOf(false) }
                                TvSurfaceCard(
                                    onClick = { viewModel.login(username, password) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                        .focusRequester(loginButtonFocusRequester)
                                        .onFocusChanged { isLoginFocused = it.isFocused },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = ArenaOrange,
                                        focusedContainerColor = Color.White
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            stringResource(R.string.login_btn),
                                            color = if (isLoginFocused) Color.Black else Color.Black,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp
                                        )
                                    }
                                }

                                TvVpnToggle(
                                    isEnabled = isSecureEnabled,
                                    onToggle = { viewModel.setSecureConnectionEnabled(it) },
                                    modifier = Modifier.weight(0.7f).height(60.dp)
                                )
                            }

                            (state as? LoginState.Error)?.let {
                                TvErrorBanner(it.message)
                            }
                        }
                    }
                }

                // ── Separator ───────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(300.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent)
                            )
                        )
                )

                // ── RIGHT: QR pairing ───────────────────────────────────
                Column(
                    modifier = Modifier.weight(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    pairingState.qrBitmap?.let { bitmap ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            modifier = Modifier.size(220.dp)
                        ) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = stringResource(R.string.login_qr_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.login_qr_msg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                        Text(
                            text = stringResource(R.string.login_qr_footer),
                            style = MaterialTheme.typography.labelMedium,
                            color = ArenaOrange.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                    } ?: run {
                        CircularProgressIndicator(
                            color = ArenaOrange,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Text(
                            stringResource(R.string.login_qr_generating),
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Sub-components
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun TvLoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisible: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val textFieldFocusRequester = remember { FocusRequester() }
    val toggleFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isFocused) {
        if (!isFocused) {
            isEditing = false
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            // Delay slightly to ensure recomposition (readOnly = false) has applied
            delay(100)
            keyboardController?.show()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(textFieldFocusRequester)
                .onFocusChanged { 
                    isFocused = it.isFocused
                    if (!it.isFocused) {
                        isEditing = false
                    }
                }
                .onKeyEvent { keyEvent ->
                    if (isFocused && !isEditing) {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            when (keyEvent.key) {
                                Key.DirectionCenter, Key.Enter -> {
                                    isEditing = true
                                    return@onKeyEvent true
                                }
                            }
                        } else if (keyEvent.type == KeyEventType.KeyDown) {
                            if (keyEvent.key == Key.DirectionRight && isPassword && onTogglePasswordVisible != null) {
                                toggleFocusRequester.requestFocus()
                                return@onKeyEvent true
                            }
                        }
                    }
                    false
                },
            readOnly = !isEditing,
            label = {
                Text(
                    label,
                    color = if (isFocused) ArenaOrange else Color.White.copy(alpha = 0.4f)
                )
            },
            leadingIcon = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isFocused) ArenaOrange else Color.White.copy(alpha = 0.35f)
                )
            },
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation()
                else VisualTransformation.None,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = ArenaOrange,
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                cursorColor = ArenaOrange
            ),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge
        )

        if (isPassword && onTogglePasswordVisible != null) {
            var isToggleFocused by remember { mutableStateOf(false) }
            TvSurfaceCard(
                onClick = onTogglePasswordVisible,
                modifier = Modifier
                    .size(56.dp)
                    .focusRequester(toggleFocusRequester)
                    .onFocusChanged { isToggleFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (isToggleFocused && keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                            textFieldFocusRequester.requestFocus()
                            return@onKeyEvent true
                        }
                        false
                    },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isToggleFocused) Color.White else Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Color.White
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = if (isToggleFocused) Color.Black else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TvVpnToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    TvSurfaceCard(
        onClick = { onToggle(!isEnabled) },
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (isEnabled) Icons.Default.Shield else Icons.Default.ShieldMoon,
                contentDescription = null,
                tint = if (isEnabled) ArenaOrange else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "VPN",
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = isEnabled,
                onCheckedChange = null,
                modifier = Modifier.scale(0.8f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ArenaOrange,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}

@Composable
private fun TvErrorBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFEF5350).copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = text,
                color = Color(0xFFEF5350),
                fontSize = 14.sp,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TvSyncProgressView(progress: SyncProgress) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            CircularProgressIndicator(
                color = ArenaOrange,
                strokeWidth = 4.dp,
                modifier = Modifier.fillMaxSize()
            )
            if (progress.countdown >= 0) {
                Text(
                    text = progress.countdown.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = if (progress.countdown >= 0) stringResource(R.string.login_sync_finishing)
                else stringResource(R.string.login_sync_preparing),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.alpha(0.6f)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvSyncStatItem(label = stringResource(R.string.nav_live_tv), count = progress.liveCount)
            Box(modifier = Modifier.size(1.dp, 24.dp).background(Color.White.copy(alpha = 0.08f)))
            TvSyncStatItem(label = stringResource(R.string.nav_movies), count = progress.movieCount)
            Box(modifier = Modifier.size(1.dp, 24.dp).background(Color.White.copy(alpha = 0.08f)))
            TvSyncStatItem(label = stringResource(R.string.nav_series), count = progress.seriesCount)
        }
    }
}

@Composable
private fun TvSyncStatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}