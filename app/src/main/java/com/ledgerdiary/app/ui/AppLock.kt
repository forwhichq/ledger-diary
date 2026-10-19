package com.ledgerdiary.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ledgerdiary.app.util.AppSettings

/**
 * 应用锁状态。放在进程级单例里，这样所有页面共享同一个「已解锁」状态。
 * 冷启动默认锁定；App 退到后台（ProcessLifecycleOwner ON_STOP）后重新锁定。
 */
object LockState {
    var unlocked by mutableStateOf(false)
    private var observerInstalled = false

    /**
     * 正在系统选择器 / 分享面板里的计数。这类界面是「我们主动拉起的另一个 App」，
     * 回到本 App 不该被当成「切出去过」而重新上锁——否则用户点了保存回来看到的是锁屏，
     * 而且页面被销毁、保存回调拿不到状态，会留下 0 字节的坏文件。
     */
    private var externalDepth = 0

    fun beginExternalActivity() {
        externalDepth++
    }

    fun endExternalActivity() {
        if (externalDepth > 0) externalDepth--
    }

    fun install() {
        if (observerInstalled) return
        observerInstalled = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // 去系统选择器/分享面板时不上锁，真正切到别的 App 才上锁
                if (externalDepth == 0) unlocked = false
            }
        })
    }
}

@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val enabled = AppSettings.isLockEnabled(ctx)
    if (!enabled || LockState.unlocked) {
        content()
        return
    }

    var error by remember { mutableStateOf<String?>(null) }
    var prompting by remember { mutableStateOf(false) }

    val activity = ctx as? FragmentActivity
    val canAuth = remember {
        val manager = BiometricManager.from(ctx)
        manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt() {
        if (activity == null) {
            // 极端情况：拿不到 Activity 就直接放行，避免把自己锁死在外面
            LockState.unlocked = true
            return
        }
        prompting = true
        error = null
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val bp = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                LockState.unlocked = true
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    error = errString.toString()
                }
            }

            override fun onAuthenticationFailed() {
                error = "没识别出来，再试一次"
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁记账日记")
            .setSubtitle("用指纹或手机密码/图案解锁")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        runCatching { bp.authenticate(info) }
            .onFailure { prompting = false; error = it.message }
    }

    LaunchedEffect(Unit) {
        if (canAuth) prompt() else error = "这台手机现在没有可用的指纹或锁屏密码"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("记账日记已锁定", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "数据只在这台手机上，锁一下更安心",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { prompt() },
                enabled = !prompting && canAuth,
                modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
            ) { Text(if (prompting) "等待验证…" else "解锁") }
            error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center)
            }
            // 兜底：万一这台机器上验证能力被移除（删了指纹/密码），不能把用户永久锁在外面
            if (!canAuth) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { LockState.unlocked = true }) {
                    Text("暂时放行（可在设置里关掉应用锁）")
                }
            }
        }
    }
}
