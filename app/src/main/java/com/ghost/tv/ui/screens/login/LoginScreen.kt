package com.ghost.tv.ui.screens.login

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.tv.R
import com.ghost.tv.pairing.ProviderQrPairingState
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.ghost.tv.ui.components.AppBackground
import com.ghost.tv.ui.components.ArenaLiveLogo
import com.ghost.tv.util.isTelevisionDevice
import kotlinx.coroutines.delay

private val ArenaOrange = Color(0xFFA855F7)

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val state by viewModel.loginState.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val pairingState by viewModel.pairingState.collectAsState()
    val isSecureEnabled by viewModel.isSecureConnectionEnabled.collectAsState()
    val isByeDpiAvailable by viewModel.isByeDpiAvailable.collectAsState()

    val context = LocalContext.current
    val isTv = context.isTelevisionDevice()
    
    LaunchedEffect(state) {
        if (state is LoginState.Success) {
            FirebaseCrashlytics.getInstance().log("Login success - navigating to home/hub")
            onLoginSuccess()
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            viewModel.startQrPairing()
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                if (isTv) {
                    TvLoginLayout(
                        state = state,
                        syncProgress = syncProgress,
                        pairingState = pairingState,
                        user = user,
                        onUserChange = { user = it },
                        pass = pass,
                        onPassChange = { pass = it },
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                        isSecureEnabled = isSecureEnabled,
                        isByeDpiAvailable = isByeDpiAvailable,
                        onSecureToggle = { viewModel.setSecureConnectionEnabled(it) },
                        onSubmit = { viewModel.login(user, pass) }
                    )
                } else {
                    MobileLoginLayout(
                        state = state,
                        syncProgress = syncProgress,
                        user = user,
                        onUserChange = { user = it },
                        pass = pass,
                        onPassChange = { pass = it },
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                        isSecureEnabled = isSecureEnabled,
                        isByeDpiAvailable = isByeDpiAvailable,
                        onSecureToggle = { viewModel.setSecureConnectionEnabled(it) },
                        onSubmit = { viewModel.login(user, pass) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvLoginLayout(
    state: LoginState,
    syncProgress: SyncProgress,
    pairingState: ProviderQrPairingState,
    user: String,
    onUserChange: (String) -> Unit,
    pass: String,
    onPassChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    isSecureEnabled: Boolean,
    isByeDpiAvailable: Boolean,
    onSecureToggle: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier.widthIn(max = 940.dp),
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(64.dp)
        ) {
            // LEFT SIDE: Manual Login
            Box(modifier = Modifier.weight(1.1f)) {
                if (state is LoginState.Loading) {
                    SyncProgressView(syncProgress)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ArenaLiveLogo(textSize = 24.sp)
                        
                        Text(
                            text = stringResource(R.string.login_title),
                            color = ArenaOrange,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        
                        OutlinedTextField(
                            value = user,
                            onValueChange = onUserChange,
                            label = { Text(stringResource(R.string.login_username)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = loginFieldColors()
                        )
                        
                        OutlinedTextField(
                            value = pass,
                            onValueChange = onPassChange,
                            label = { Text(stringResource(R.string.login_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            colors = loginFieldColors()
                        )
                        
                        Button(
                            onClick = onSubmit,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ArenaOrange)
                        ) {
                            Text(stringResource(R.string.login_btn), fontWeight = FontWeight.Bold)
                        }

                        if (state is LoginState.Error) {
                            ErrorBanner(state.message)
                        }
                    }
                }
            }

            // Separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent)
                        )
                    )
            )

            // RIGHT SIDE: QR Code
            Column(
                modifier = Modifier.weight(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                pairingState.qrBitmap?.let { bitmap ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        modifier = Modifier.size(200.dp)
                    ) {
                        Box(modifier = Modifier.padding(14.dp)) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.login_qr_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.login_qr_msg),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.login_qr_footer),
                        style = MaterialTheme.typography.labelSmall,
                        color = ArenaOrange.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                } ?: run {
                    CircularProgressIndicator(color = ArenaOrange)
                    Text(stringResource(R.string.login_qr_generating), color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun MobileLoginLayout(
    state: LoginState,
    syncProgress: SyncProgress,
    user: String,
    onUserChange: (String) -> Unit,
    pass: String,
    onPassChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    isSecureEnabled: Boolean,
    isByeDpiAvailable: Boolean,
    onSecureToggle: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 500.dp)
                .verticalScroll(scrollState)
                .imePadding() // Only push the content that needs it
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            ArenaLiveLogo(textSize = 28.sp)

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state is LoginState.Loading) {
                        SyncProgressView(syncProgress)
                    } else {
                        // 1. Header: Sign In
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.login_title),
                                color = ArenaOrange,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = stringResource(R.string.login_subtitle),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 2. Input Fields (Optimized Row for Landscape Mobile)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = user,
                                onValueChange = onUserChange,
                                label = { Text(stringResource(R.string.login_username), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Person, null, tint = ArenaOrange.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) },
                                colors = loginFieldColors()
                            )
                            
                            OutlinedTextField(
                                value = pass,
                                onValueChange = onPassChange,
                                label = { Text(stringResource(R.string.login_password), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Lock, null, tint = ArenaOrange.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) },
                                trailingIcon = {
                                    IconButton(onClick = onTogglePasswordVisible, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.35f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = loginFieldColors()
                            )
                        }

                        // 3. Action Row: Login (Left) + VPN (Right) - Reduced Height
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onSubmit,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ArenaOrange)
                            ) {
                                Text(stringResource(R.string.login_btn), fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.5.sp)
                            }

                            Surface(
                                onClick = { if (isByeDpiAvailable) onSecureToggle(!isSecureEnabled) },
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(0.55f).height(44.dp).alpha(if (isByeDpiAvailable) 1f else 0.5f),
                                border = BorderStroke(1.dp, if (isSecureEnabled && isByeDpiAvailable) ArenaOrange else Color.White.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        if (isSecureEnabled && isByeDpiAvailable) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                        null,
                                        tint = if (isSecureEnabled && isByeDpiAvailable) ArenaOrange else Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isByeDpiAvailable) "FAST VPN" else "OFF",
                                        color = Color.White,
                                        fontSize = 9.sp, // Slightly smaller to fit "FAST VPN"
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Switch(
                                        checked = isSecureEnabled && isByeDpiAvailable,
                                        onCheckedChange = null,
                                        enabled = isByeDpiAvailable,
                                        modifier = Modifier.scale(0.6f),
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = ArenaOrange,
                                            disabledCheckedTrackColor = ArenaOrange.copy(alpha = 0.5f),
                                            disabledUncheckedTrackColor = Color.White.copy(alpha = 0.05f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state is LoginState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                ErrorBanner(state.message)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = ArenaOrange,
    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
    focusedContainerColor = Color.White.copy(alpha = 0.05f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
    cursorColor = ArenaOrange
)

@Composable
private fun ErrorBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFEF5350).copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
            Text(text = text, color = Color(0xFFEF5350), fontSize = 13.sp, textAlign = TextAlign.Start)
        }
    }
}

@Composable
private fun SyncProgressView(progress: SyncProgress) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            CircularProgressIndicator(
                color = ArenaOrange,
                strokeWidth = 3.dp,
                modifier = Modifier.fillMaxSize()
            )
            if (progress.countdown >= 0) {
                Text(
                    text = progress.countdown.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (progress.countdown >= 0) stringResource(R.string.login_sync_finishing) else stringResource(R.string.login_sync_preparing),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.alpha(0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SyncStatItem(label = stringResource(R.string.nav_live_tv), count = progress.liveCount)
            Box(modifier = Modifier.size(1.dp, 20.dp).background(Color.White.copy(alpha = 0.08f)))
            SyncStatItem(label = stringResource(R.string.nav_movies), count = progress.movieCount)
            Box(modifier = Modifier.size(1.dp, 20.dp).background(Color.White.copy(alpha = 0.08f)))
            SyncStatItem(label = stringResource(R.string.nav_series), count = progress.seriesCount)
        }
    }
}

@Composable
private fun SyncStatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
