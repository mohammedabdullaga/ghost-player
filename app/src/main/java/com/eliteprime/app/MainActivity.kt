package com.eliteprime.app

import android.R
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.eliteprime.app.data.local.entity.XtreamAccount
import com.eliteprime.app.data.local.entity.XtreamAccount_
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.player.GlobalPlayerState
import com.eliteprime.app.player.PlayerEngine
import com.eliteprime.app.player.PlayerViewMode
import com.eliteprime.app.ui.MainShell
import com.eliteprime.app.ui.tv.TvMainShell
import com.eliteprime.app.ui.theme.ArenaLiveTheme
import com.eliteprime.app.util.DeviceRenderProfile
import com.eliteprime.app.util.LocalRenderTier
import com.eliteprime.app.util.RenderTier
import com.eliteprime.app.util.isTelevisionDevice
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var boxStore: BoxStore
    @Inject lateinit var globalPlayerState: GlobalPlayerState
    @Inject lateinit var playerEngine: PlayerEngine
    @Inject lateinit var preferenceRepository: PreferenceRepository

    private val _pictureInPictureModeFlow = MutableStateFlow(false)
    val pictureInPictureModeFlow: StateFlow<Boolean> = _pictureInPictureModeFlow.asStateFlow()

    private var pipAspectRatio: Rational? = null
    private var pipEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        FirebaseCrashlytics.getInstance().log("MainActivity onCreate started")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = "android.permission.POST_NOTIFICATIONS"
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }

        preferenceRepository.applySavedLocale()
        
        // Initialize render profile taking saved Lite Mode into account
        val isLite = preferenceRepository.isLiteModeEnabled.value
        DeviceRenderProfile.initialize(this, liteMode = isLite)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val accountBox = boxStore.boxFor(XtreamAccount::class.java)
        val hasActiveAccount = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() != null
        val startDest = if (hasActiveAccount) {
            // Lite Hub is now default for both TV and Mobile if Lite Mode is enabled
            if (isLite) "hub" else "home"
        } else "login"

        _pictureInPictureModeFlow.value = isInPictureInPictureMode

        setContent {
            val isTv = isTelevisionDevice()
            val currentLiteMode by preferenceRepository.isLiteModeEnabled.collectAsState()
            
            // LITE tier applies to all devices if the setting is enabled.
            val renderTier = if (currentLiteMode) {
                RenderTier.LITE
            } else {
                remember { DeviceRenderProfile.getTier() }
            }
            
            CompositionLocalProvider(LocalRenderTier provides renderTier) {
                ArenaLiveTheme {
                    if (isTv) {
                        TvMainShell(
                            playerEngine = playerEngine,
                            globalPlayerState = globalPlayerState,
                            startDestination = startDest
                        )
                    } else {
                        MainShell(
                            playerEngine = playerEngine,
                            globalPlayerState = globalPlayerState,
                            startDestination = startDest
                        )
                    }
                }
            }
        }
    }

    fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        finishAffinity()
        startActivity(intent)
    }

    private fun applyImmersiveMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
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
            window.setBackgroundDrawableResource(R.color.black)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        // Ensure keyboard is hidden on resume
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            globalPlayerState.activeEngine.value?.stop()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipEnabled && globalPlayerState.activeEngine.value != null && globalPlayerState.mode.value == PlayerViewMode.FULLSCREEN) {
            enterPlayerPictureInPictureMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _pictureInPictureModeFlow.value = isInPictureInPictureMode
    }

    fun updatePipState(enabled: Boolean, width: Int, height: Int) {
        this.pipEnabled = enabled
        if (width > 0 && height > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val rational = videoAspectRatioOrNull(width, height)
            if (rational != null) {
                pipAspectRatio = rational
                if (isInPictureInPictureMode) {
                    setPictureInPictureParams(PictureInPictureParams.Builder().setAspectRatio(rational).build())
                }
            }
        }
    }

    fun enterPlayerPictureInPictureMode(): Boolean {
        if (!supportsPictureInPicture() || isInPictureInPictureMode) {
            return false
        }
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
            pipAspectRatio?.let { builder.setAspectRatio(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
                builder.setSeamlessResizeEnabled(true)
            }
            val params = builder.build()
            setPictureInPictureParams(params)
            enterPictureInPictureMode(params)
        } else {
            false
        }
    }

    private fun videoAspectRatioOrNull(width: Int, height: Int): Rational? {
        if (width <= 0 || height <= 0) return null
        val ratio = width.toFloat() / height.toFloat()
        val clamped = ratio.coerceIn(0.41f, 2.39f)
        val numerator = (clamped * 10_000).toInt()
        return Rational(numerator, 10_000)
    }

    private fun supportsPictureInPicture(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
}
