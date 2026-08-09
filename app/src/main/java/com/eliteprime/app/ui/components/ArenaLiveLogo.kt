package com.eliteprime.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteprime.app.ui.theme.AppColors

@Composable
fun ArenaLiveLogo(
    modifier: Modifier = Modifier,
    textSize: TextUnit = 20.sp
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Arena",
                fontSize = textSize,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Live",
                fontSize = textSize,
                color = AppColors.ArenaOrange,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " IPTV",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )
        }
    }
}
