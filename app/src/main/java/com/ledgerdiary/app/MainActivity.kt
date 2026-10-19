package com.ledgerdiary.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgerdiary.app.ui.AppLockGate
import com.ledgerdiary.app.ui.AppRoot
import com.ledgerdiary.app.ui.AppViewModel
import com.ledgerdiary.app.ui.LockState
import com.ledgerdiary.app.ui.screens.OnboardingScreen
import com.ledgerdiary.app.ui.theme.LedgerDiaryTheme
import com.ledgerdiary.app.util.AppSettings

/**
 * 继承 FragmentActivity 而不是 ComponentActivity：
 * androidx.biometric.BiometricPrompt 需要 FragmentActivity（应用锁用）。
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LockState.install()
        setContent { Root() }
    }
}

@Composable
private fun Root() {
    val vm: AppViewModel = viewModel()
    val ctx = LocalContext.current
    // 首次启动走引导页（可跳过）。引导完成后写一个标记，之后不再出现。
    var onboarded by remember { mutableStateOf(AppSettings.hasOnboarded(ctx)) }

    LedgerDiaryTheme {
        AppLockGate {
            if (onboarded) {
                AppRoot(vm)
            } else {
                OnboardingScreen(vm) {
                    AppSettings.setOnboarded(ctx)
                    onboarded = true
                }
            }
        }
    }
}
