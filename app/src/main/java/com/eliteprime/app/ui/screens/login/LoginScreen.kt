package com.eliteprime.app.ui.screens.login

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.ArenaLiveLogo

private val ArenaOrange = Color(0xFFFF6029)

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

    LaunchedEffect(state) {
        if (state is LoginState.Success) {
            onLoginSuccess()
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    ArenaLiveLogo(textSize = 26.sp)
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                        .offset(y = (-40).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedContent(
                                targetState = state is LoginState.Loading,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "login-form-vs-sync"
                            ) { isSyncing ->
                                if (isSyncing) {
                                    SyncProgressView(syncProgress)
                                } else {
                                    LoginForm(
                                        username = user,
                                        onUsernameChange = { user = it },
                                        password = pass,
                                        onPasswordChange = { pass = it },
                                        passwordVisible = passwordVisible,
                                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                                        onSubmit = { viewModel.login(user, pass) }
                                    )
                                }
                            }
                        }
                    }

                    if (state is LoginState.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ErrorBanner((state as LoginState.Error).message)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "SIGN IN",
            color = ArenaOrange,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            letterSpacing = 1.4.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter your credentials to start",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            placeholder = { Text("Username", color = Color.White.copy(alpha = 0.35f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.35f)) },
            colors = loginFieldColors()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = { Text("Password", color = Color.White.copy(alpha = 0.35f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.35f)) },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisible) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f)
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = loginFieldColors()
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ArenaOrange)
        ) {
            Text("LOGIN", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
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
            text = if (progress.countdown >= 0) "FINISHING SETUP..." else "PREPARING CONTENT...",
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
            SyncStatItem(label = "LIVE TV", count = progress.liveCount)
            Box(modifier = Modifier.size(1.dp, 20.dp).background(Color.White.copy(alpha = 0.08f)))
            SyncStatItem(label = "MOVIES", count = progress.movieCount)
            Box(modifier = Modifier.size(1.dp, 20.dp).background(Color.White.copy(alpha = 0.08f)))
            SyncStatItem(label = "SERIES", count = progress.seriesCount)
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
