package com.carriez.flutter_hbb

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 主服务 Root 模式集成助手
 * 利用 Root 权限实现：
 * 1. 自动静默开启系统无障碍服务（com.carriez.flutter_hbb.InputService）
 * 2. 自动配置电池优化白名单（Doze 忽略）与后台运行权限
 * 3. 自动授予系统悬浮窗与通知等权限
 */
object MainServiceRootIntegration {
    private const val TAG = "RustDesk_RootIntegrate"
    private const val SERVICE_COMPONENT = "com.carriez.flutter_hbb/com.carriez.flutter_hbb.InputService"
    
    private var isRootModeActive = false

    fun init(context: Context) {
        kotlin.concurrent.thread(start = true, name = "RootInitThread") {
            try {
                if (RootInputManager.isRootAvailable()) {
                    Log.i(TAG, "Root detected! Initializing Root Permission & Service Enabler...")
                    isRootModeActive = true
                    applyRootOptimizations(context)
                } else {
                    Log.i(TAG, "Device is not rooted or SU denied.")
                    isRootModeActive = false
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error initializing Root: ${e.message}", e)
                isRootModeActive = false
            }
        }
    }

    fun isRootMode(): Boolean = isRootModeActive

    /**
     * 自动通过 Root 开启无障碍服务与各项后台系统权限
     */
    fun applyRootOptimizations(context: Context) {
        kotlin.concurrent.thread(start = true, name = "RootOptThread") {
            try {
                // 1. 自动激活 InputService 无障碍服务
                enableAccessibilityViaRootInternal()

                // 2. 加入电池优化白名单 (忽略电池优化，支持后台长效运行)
                RootInputManager.runRootCommand("dumpsys deviceidle whitelist +com.carriez.flutter_hbb")

                // 3. 授予后台运行 AppOps 权限
                RootInputManager.runRootCommand("cmd appops set com.carriez.flutter_hbb RUN_IN_BACKGROUND allow 2>/dev/null || appops set com.carriez.flutter_hbb RUN_IN_BACKGROUND allow")
                RootInputManager.runRootCommand("cmd appops set com.carriez.flutter_hbb RUN_ANY_IN_BACKGROUND allow 2>/dev/null || appops set com.carriez.flutter_hbb RUN_ANY_IN_BACKGROUND allow")
                RootInputManager.runRootCommand("cmd appops set com.carriez.flutter_hbb SYSTEM_ALERT_WINDOW allow 2>/dev/null || appops set com.carriez.flutter_hbb SYSTEM_ALERT_WINDOW allow")
                RootInputManager.runRootCommand("cmd appops set com.carriez.flutter_hbb POST_NOTIFICATION allow 2>/dev/null || appops set com.carriez.flutter_hbb POST_NOTIFICATION allow")
                RootInputManager.runRootCommand("cmd appops set com.carriez.flutter_hbb PROJECT_MEDIA allow 2>/dev/null || appops set com.carriez.flutter_hbb PROJECT_MEDIA allow")

                // 4. 授予系统运行时权限
                RootInputManager.runRootCommand("pm grant com.carriez.flutter_hbb android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null")
                RootInputManager.runRootCommand("pm grant com.carriez.flutter_hbb android.permission.POST_NOTIFICATIONS 2>/dev/null")
                RootInputManager.runRootCommand("pm grant com.carriez.flutter_hbb android.permission.FOREGROUND_SERVICE 2>/dev/null")

                Log.i(TAG, "Root optimizations and accessibility auto-grant applied successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed applying root optimizations: ${e.message}")
            }
        }
    }

    /**
     * 供 Flutter / MainActivity 调用：静默开启无障碍服务
     */
    fun enableAccessibilityViaRoot(context: Context) {
        kotlin.concurrent.thread(start = true, name = "EnableA11yThread") {
            try {
                enableAccessibilityViaRootInternal()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable accessibility via root: ${e.message}")
            }
        }
    }

    private fun enableAccessibilityViaRootInternal() {
        val svc = SERVICE_COMPONENT
        val cmd = "cur=\$(settings get secure enabled_accessibility_services); " +
                "if [ \"\$cur\" = \"null\" ] || [ -z \"\$cur\" ]; then " +
                "settings put secure enabled_accessibility_services \"$svc\"; " +
                "else case \"\$cur\" in *\"$svc\"*) ;; *) settings put secure enabled_accessibility_services \"\$cur:$svc\" ;; esac; fi; " +
                "settings put secure accessibility_enabled 1"
        RootInputManager.runRootCommand(cmd)
    }

    fun destroy() {
        if (isRootModeActive) {
            isRootModeActive = false
        }
    }

    fun notifyInputState() {
        Handler(Looper.getMainLooper()).post {
            MainActivity.flutterMethodChannel?.invokeMethod(
                "on_state_changed",
                mapOf("name" to "input", "value" to InputService.isOpen.toString())
            )
        }
    }
}
