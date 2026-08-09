package com.eliteprime.app

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.eliteprime.app.data.local.entity.XtreamAccount
import com.eliteprime.app.data.local.entity.XtreamAccount_
import com.eliteprime.app.ui.MainShell
import com.eliteprime.app.ui.theme.ELITPRIMETheme
import dagger.hilt.android.AndroidEntryPoint
import io.objectbox.BoxStore
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var boxStore: BoxStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Removed applyImmersiveMode() from here to prevent crash on startup.
        // It will be triggered via onWindowFocusChanged once the window is ready.

        val accountBox = boxStore.boxFor(XtreamAccount::class.java)
        val hasActiveAccount = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() != null
        val startDest = if (hasActiveAccount) "home" else "login"

        setContent {
            ELITPRIMETheme {
                MainShell(startDestination = startDest)
            }
        }
    }

    private fun applyImmersiveMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Safer access to insets controller
                window.setDecorFitsSystemWindows(false)
                val controller = window.insetsController
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }
}
