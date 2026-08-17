package com.eliteprime.app.ui.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import com.eliteprime.app.R
import com.eliteprime.app.ui.screens.login.LoginState
import com.eliteprime.app.ui.screens.login.LoginViewModel
import com.eliteprime.app.ui.tv.core.*

@Composable
fun TvLoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val loginState by viewModel.loginState.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) onLoginSuccess()
    }

    Box(modifier = Modifier.fillMaxSize().background(TvColors.Background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            
            // --- LEFT: BRANDING & QR LOGIN ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(56.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.QrCodeScanner, 
                    null, 
                    tint = TvColors.Primary, 
                    modifier = Modifier.size(120.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    stringResource(R.string.login_qr_title).uppercase(), 
                    color = Color.White, 
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    stringResource(R.string.login_qr_pairing), 
                    color = TvColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                TvSurfaceCard(
                    onClick = { viewModel.startQrPairing() },
                    modifier = Modifier.width(200.dp).height(52.dp),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.login_generate_code), color = Color.Black, fontWeight = FontWeight.Black)
                }
            }

            // --- RIGHT: MANUAL LOGIN ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(56.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.login_title), 
                    style = MaterialTheme.typography.displaySmall, 
                    color = Color.White, 
                    fontWeight = FontWeight.Black
                )
                Text(stringResource(R.string.login_subtitle), color = TvColors.TextTertiary)

                Spacer(modifier = Modifier.height(48.dp))

                TvLoginTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.login_username),
                    icon = Icons.Default.Person
                )

                Spacer(modifier = Modifier.height(16.dp))

                TvLoginTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.login_password),
                    icon = Icons.Default.Lock,
                    isPassword = true
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (loginState is LoginState.Loading) {
                    CircularProgressIndicator(color = TvColors.Primary)
                } else {
                    TvSurfaceCard(
                        onClick = { viewModel.login(username, password) },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = TvColors.Primary,
                            focusedContainerColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.login_btn), color = Color.Black, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
                
                if (loginState is LoginState.Error) {
                    Text((loginState as LoginState.Error).message, color = Color.Red, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun TvLoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        label = { Text(label, color = if(isFocused) TvColors.Primary else TvColors.TextTertiary) },
        leadingIcon = { Icon(icon, null, tint = if(isFocused) TvColors.Primary else Color.White.copy(alpha = 0.3f)) },
        visualTransformation = if(isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            hintLocales = LocaleList(Locale("en"), Locale("ar"))
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = TvColors.Primary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Color.White.copy(alpha = 0.05f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
        ),
        shape = RoundedCornerShape(12.dp)
    )
}
