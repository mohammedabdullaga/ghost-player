package com.eliteprime.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.eliteprime.app.R
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay

@Composable
fun ModeSelectionDialog(
    onSelectMode: (isLite: Boolean) -> Unit
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var selectedLite by remember { mutableStateOf(isTv) } // Default to Lite on TV
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) {
            delay(200)
            try { firstFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF12151B),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.mode_select_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.mode_select_subtitle),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Lite Mode Option Card
                ModeOptionCard(
                    title = stringResource(R.string.mode_lite_title),
                    description = stringResource(R.string.mode_lite_desc),
                    isSelected = selectedLite,
                    isTv = isTv,
                    focusRequester = firstFocusRequester,
                    onClick = { selectedLite = true }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Standard Mode Option Card
                ModeOptionCard(
                    title = stringResource(R.string.mode_standard_title),
                    description = stringResource(R.string.mode_standard_desc),
                    isSelected = !selectedLite,
                    isTv = isTv,
                    focusRequester = null,
                    onClick = { selectedLite = false }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Continue Button
                if (isTv) {
                    var isBtnFocused by remember { mutableStateOf(false) }
                    TvSurface(
                        onClick = { onSelectMode(selectedLite) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .onFocusChanged { isBtnFocused = it.isFocused },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AppColors.ArenaOrange,
                            focusedContainerColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.mode_select_continue),
                                color = if (isBtnFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { onSelectMode(selectedLite) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.ArenaOrange)
                    ) {
                        Text(
                            text = stringResource(R.string.mode_select_continue),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    isTv: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        val modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)

        TvSurface(
            onClick = onClick,
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
                focusedContainerColor = Color.White
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                border = Border(BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange else Color.Transparent))
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = if (isFocused) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f),
            border = BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange else Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
