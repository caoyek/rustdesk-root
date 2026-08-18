package com.carriez.flutter_hbb

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Root 模式输入管理器
 * 专为在关闭无障碍服务及 USB 调试时，通过底层持久化 su 管道向 Android 系统注入触控与按键事件
 */
object RootInputManager {
    private const val TAG = "RustDesk_RootInput"
    
    private var suProcess: Process? = null
    private var suOutputStream: DataOutputStream? = null
    private var suBufferedReader: BufferedReader? = null
    
    private val isRunning = AtomicBoolean(false)
    private val commandQueue = ConcurrentLinkedQueue<String>()
    private var workerThread: Thread? = null

    /**
     * 检查设备是否具备可用的 Root 权限
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            Log.e(TAG, "Check root error: ${e.message}")
            false
        }
    }

    /**
     * 初始化持久化 Root 输入服务
     */
    @Synchronized
    fun start(context: Context): Boolean {
        if (isRunning.get() && suProcess != null) {
            return true
        }

        try {
            Log.i(TAG, "Starting persistent SU process for Root input...")
            val process = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()
            
            suProcess = process
            suOutputStream = DataOutputStream(process.outputStream)
            suBufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            
            isRunning.set(true)

            // 启动异步指令队列消费线程，防止高频点击阻塞主线程
            workerThread = Thread({
                while (isRunning.get()) {
                    try {
                        val cmd = commandQueue.poll()
                        if (cmd != null && suOutputStream != null) {
                            suOutputStream!!.writeBytes("$cmd\n")
                            suOutputStream!!.flush()
                        } else {
                            Thread.sleep(2)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing to SU stream: ${e.message}")
                        if (isRunning.get()) {
                            restart(context)
                            break
                        }
                    }
                }
            }, "RustDesk-RootInput-Worker").apply {
                isDaemon = true
                start()
            }

            // 自动尝试静默授予屏幕录制权限 (Android 10+)
            val pkgName = context.packageName
            executeCommand("cmd appops set $pkgName PROJECT_MEDIA allow")
            executeCommand("appops set $pkgName PROJECT_MEDIA allow")

            Log.i(TAG, "RootInputManager started successfully!")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start RootInputManager: ${e.message}")
            stop()
            return false
        }
    }

    /**
     * 发送原始 Shell 命令
     */
    fun executeCommand(command: String) {
        if (!isRunning.get()) return
        commandQueue.offer(command)
    }

    /**
     * 模拟单点触摸/点击
     */
    fun tap(x: Int, y: Int) {
        executeCommand("input tap $x $y")
    }

    /**
     * 模拟滑动操作
     */
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int = 200) {
        executeCommand("input swipe $startX $startY $endX $endY $durationMs")
    }

    /**
     * 模拟长按操作
     */
    fun longPress(x: Int, y: Int, durationMs: Int = 800) {
        executeCommand("input swipe $x $y $x $y $durationMs")
    }

    /**
     * 模拟发送按键码
     */
    fun keyEvent(keyCode: Int) {
        executeCommand("input keyevent $keyCode")
    }

    /**
     * 常用按键封装
     */
    fun pressBack() = keyEvent(4)      // KEYCODE_BACK
    fun pressHome() = keyEvent(3)      // KEYCODE_HOME
    fun pressMenu() = keyEvent(82)     // KEYCODE_MENU
    fun pressRecentApps() = keyEvent(187) // KEYCODE_APP_SWITCH
    fun pressPower() = keyEvent(26)    // KEYCODE_POWER

    /**
     * 模拟输入文本
     */
    fun inputText(text: String) {
        // 对特殊字符进行转义
        val escaped = text.replace(" ", "%s")
            .replace("&", "\\&")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace("\"", "\\\"")
        executeCommand("input text \"$escaped\"")
    }

    /**
     * 重启 Root 守护进程
     */
    @Synchronized
    private fun restart(context: Context) {
        stop()
        Thread.sleep(500)
        start(context)
    }

    /**
     * 停止 Root 输入服务
     */
    @Synchronized
    fun stop() {
        isRunning.set(false)
        try {
            suOutputStream?.writeBytes("exit\n")
            suOutputStream?.flush()
            suOutputStream?.close()
        } catch (_: Exception) {}

        try {
            suProcess?.destroy()
        } catch (_: Exception) {}

        suProcess = null
        suOutputStream = null
        suBufferedReader = null
        commandQueue.clear()
        Log.i(TAG, "RootInputManager stopped.")
    }
}
