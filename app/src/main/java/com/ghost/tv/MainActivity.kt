package com.ghost.tv

import android.R
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import com.ghost.tv.data.config.ConfigRepository
import com.ghost.tv.data.local.entity.XtreamAccount
import com.ghost.tv.data.local.entity.XtreamAccount_
import com.ghost.tv.data.repository.PreferenceRepository
import com.ghost.tv.data.update.AppUpdateManager
import com.ghost.tv.player.GlobalPlayerState
import com.ghost.tv.player.PlayerEngine
import com.ghost.tv.player.PlayerViewMode
import com.ghost.tv.ui.MainShell
import com.ghost.tv.ui.components.AppUpdateDialog
import com.ghost.tv.ui.tv.TvMainShell
import com.ghost.tv.ui.theme.ArenaLiveTheme
import com.ghost.tv.util.DeviceRenderProfile
import com.ghost.tv.util.LocalRenderTier
import com.ghost.tv.util.RenderTier
import com.ghost.tv.util.isTelevisionDevice
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
    @Inject lateinit var configRepository: ConfigRepository
    @Inject lateinit var appUpdateManager: AppUpdateManager

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
        val hasActiveAccount = runCatching {
            accountBox.query().equal(XtreamAccount_.isActive, true).build().use { it.findFirst() } != null
        }.getOrDefault(false)
        val startDest = if (hasActiveAccount) {
            // Use the resolved DeviceRenderProfile value, not raw SharedPrefs
            // — DeviceRenderProfile already blocks Lite mode on mobile devices.
            if (DeviceRenderProfile.isLiteMode()) "hub" else "home"
        } else "login"

        _pictureInPictureModeFlow.value = isInPictureInPictureMode

        setContent {
            val isTv = isTelevisionDevice()
            val currentLiteMode by preferenceRepository.isLiteModeEnabled.collectAsState()
            val availableUpdate by configRepository.availableUpdate.collectAsState()
            // Resolved tier: LITE only on TV; mobile always uses device tier.
            val renderTier = if (isTv && currentLiteMode) RenderTier.LITE else DeviceRenderProfile.getTier()
            
            CompositionLocalProvider(LocalRenderTier provides renderTier) {
                ArenaLiveTheme {
                    availableUpdate?.let { updateConfig ->
                        AppUpdateDialog(
                            updateConfig = updateConfig,
                            appUpdateManager = appUpdateManager,
                            onDismissRequest = {
                                configRepository.dismissUpdateForSession()
                            }
                        )
                    }
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

    override fun onPause() {
        super.onPause()
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        Log.i("MainActivity", "[$ts] [Lifecycle] MainActivity onPause() - App backgrounded")
    }

    override fun onResume() {
        super.onResume()
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        Log.i("MainActivity", "[$ts] [Lifecycle] MainActivity onResume() - App foregrounded")
        applyImmersiveMode()
        appUpdateManager.recheckPermissionOnResume()
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
        // TV devices do not support PiP — skip entirely.
        if (isTelevisionDevice()) return
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
        // TV devices don't support PiP.
        if (isTelevisionDevice()) return
        if (width > 0 && height > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val rational = videoAspectRatioOrNull(width, height)
            if (rational != null) {
                pipAspectRatio = rational
                if (isInPictureInPictureMode) {
                    try {
                        setPictureInPictureParams(PictureInPictureParams.Builder().setAspectRatio(rational).build())
                    } catch (e: Exception) {
                        Log.w("PIP", "setPictureInPictureParams failed", e)
                    }
                }
            }
        }
    }

    fun enterPlayerPictureInPictureMode(): Boolean {
        // TV devices don't support PiP.
        if (isTelevisionDevice()) return false
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
            try {
                setPictureInPictureParams(params)
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Log.w("PIP", "enterPictureInPictureMode failed — device may not support PiP", e)
                false
            }
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
