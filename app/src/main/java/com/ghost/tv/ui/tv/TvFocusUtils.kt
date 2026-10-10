package com.ghost.tv.ui.tv

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

fun Modifier.dpadFocus(
    focusRequester: FocusRequester,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null
): Modifier = this
    .focusRequester(focusRequester)
    .focusProperties {
        up?.let { this.up = it }
        down?.let { this.down = it }
        left?.let { this.left = it }
        right?.let { this.right = it }
    }
