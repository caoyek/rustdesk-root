package com.carriez.flutter_hbb

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 主服务 Root 模式集成助手
 * 提供无缝切换：当设备已 Root 时自动走底层高性能注入，无需强求用户开启无障碍服务
 */
object MainServiceRootIntegration {
    private const val TAG = "RustDesk_RootIntegrate"
    
    private var isRootModeActive = false

    private var isPointerDown = false
    private var pointerStartX = 0
    private var pointerStartY = 0
    private var lastPointerX = 0
    private var lastPointerY = 0
    private var pointerDownTime = 0L

    fun init(context: Context) {
        kotlin.concurrent.thread(start = true, name = "RootInitThread") {
            try {
                if (RootInputManager.isRootAvailable()) {
                    Log.i(TAG, "Root detected! Initializing Root Input Service...")
                    isRootModeActive = RootInputManager.start(context)
                    if (isRootModeActive) {
                        notifyInputState()
                    }
                } else {
                    Log.i(TAG, "Device is not rooted or SU denied. Fallback to Accessibility.")
                    isRootModeActive = false
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error initializing Root: ${e.message}", e)
                isRootModeActive = false
            }
        }
    }

    fun notifyInputState() {
        Handler(Looper.getMainLooper()).post {
            MainActivity.flutterMethodChannel?.invokeMethod(
                "on_state_changed",
                mapOf("name" to "input", "value" to "true")
            )
        }
    }

    fun isRootMode(): Boolean = isRootModeActive

    /**
     * 分发统一触控与鼠标事件
     */
    fun handlePointerInput(kind: Int, mask: Int, x: Int, y: Int): Boolean {
        if (!isRootModeActive) return false
        
        when (kind) {
            1 -> { // 鼠标事件
                when (mask) {
                    1 -> { // LEFT_DOWN
                        isPointerDown = true
                        pointerStartX = x
                        pointerStartY = y
                        lastPointerX = x
                        lastPointerY = y
                        pointerDownTime = System.currentTimeMillis()
                    }
                    2 -> { // LEFT_MOVE
                        if (isPointerDown) {
                            lastPointerX = x
                            lastPointerY = y
                        }
                    }
                    0 -> { // LEFT_UP
                        if (isPointerDown) {
                            isPointerDown = false
                            val duration = System.currentTimeMillis() - pointerDownTime
                            val dx = kotlin.math.abs(lastPointerX - pointerStartX)
                            val dy = kotlin.math.abs(lastPointerY - pointerStartY)
                            if (dx < 15 && dy < 15) {
                                if (duration > 600) {
                                    RootInputManager.longPress(pointerStartX, pointerStartY, duration.toInt())
                                } else {
                                    RootInputManager.tap(pointerStartX, pointerStartY)
                                }
                            } else {
                                val swipeDur = kotlin.math.max(100, kotlin.math.min(duration.toInt(), 500))
                                RootInputManager.swipe(pointerStartX, pointerStartY, lastPointerX, lastPointerY, swipeDur)
                            }
                        }
                    }
                    3 -> { // RIGHT_UP (返回键)
                        RootInputManager.pressBack()
                    }
                    523331 -> { // WHEEL_DOWN (向下滚屏)
                        RootInputManager.swipe(x, y, x, kotlin.math.max(0, y - 350), 100)
                    }
                    963 -> { // WHEEL_UP (向上滚屏)
                        RootInputManager.swipe(x, y, x, y + 350, 100)
                    }
                }
                return true
            }
            0 -> { // 触屏手势
                when (mask) {
                    4 -> { // TOUCH_PAN_START
                        isPointerDown = true
                        pointerStartX = x
                        pointerStartY = y
                        lastPointerX = x
                        lastPointerY = y
                        pointerDownTime = System.currentTimeMillis()
                    }
                    5 -> { // TOUCH_PAN_UPDATE (RustDesk 传入相对位移)
                        if (isPointerDown) {
                            lastPointerX -= x
                            lastPointerY -= y
                        }
                    }
                    6 -> { // TOUCH_PAN_END
                        if (isPointerDown) {
                            isPointerDown = false
                            val duration = System.currentTimeMillis() - pointerDownTime
                            val dx = kotlin.math.abs(lastPointerX - pointerStartX)
                            val dy = kotlin.math.abs(lastPointerY - pointerStartY)
                            if (dx < 15 && dy < 15) {
                                RootInputManager.tap(pointerStartX, pointerStartY)
                            } else {
                                val swipeDur = kotlin.math.max(100, kotlin.math.min(duration.toInt(), 500))
                                RootInputManager.swipe(pointerStartX, pointerStartY, lastPointerX, lastPointerY, swipeDur)
                            }
                        }
                    }
                }
                return true
            }
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
