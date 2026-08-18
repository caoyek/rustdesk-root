package com.carriez.flutter_hbb

import android.content.Context
import android.util.Log

/**
 * 主服务 Root 模式集成助手
 * 提供无缝切换：当设备已 Root 时自动走底层高性能注入，无需强求用户开启无障碍服务
 */
object MainServiceRootIntegration {
    private const val TAG = "RustDesk_RootIntegrate"
    
    private var isRootModeActive = false

    fun init(context: Context) {
        if (RootInputManager.isRootAvailable()) {
            Log.i(TAG, "Root detected! Initializing Root Input Service...")
            isRootModeActive = RootInputManager.start(context)
        } else {
            Log.i(TAG, "Device is not rooted or SU denied. Fallback to Accessibility.")
            isRootModeActive = false
        }
    }

    fun isRootMode(): Boolean = isRootModeActive

    /**
     * 处理点击事件
     */
    fun handleTap(x: Int, y: Int): Boolean {
        if (isRootModeActive) {
            RootInputManager.tap(x, y)
            return true
        }
        return false
    }

    /**
     * 处理滑动事件
     */
    fun handleSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int = 200): Boolean {
        if (isRootModeActive) {
            RootInputManager.swipe(startX, startY, endX, endY, durationMs)
            return true
        }
        return false
    }

    /**
     * 处理按键事件
     */
    fun handleKeyEvent(keyCode: Int): Boolean {
        if (isRootModeActive) {
            RootInputManager.keyEvent(keyCode)
            return true
        }
        return false
    }

    /**
     * 销毁清理
     */
    fun destroy() {
        if (isRootModeActive) {
            RootInputManager.stop()
            isRootModeActive = false
        }
    }
}
