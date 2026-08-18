package com.carriez.flutter_hbb

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 交互式 Root 远程终端会话
 * 用于在 USB 调试关闭的情况下，通过 RustDesk 的网络通道直接提供 Root Shell 执行环境
 */
class RootTerminalSession(
    private val onOutput: (ByteArray) -> Unit,
    private val onExit: (Int) -> Unit
) {
    companion object {
        private const val TAG = "RustDesk_RootTerm"
    }

    private var process: Process? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var errorStream: InputStream? = null
    private val isRunning = AtomicBoolean(false)

    /**
     * 启动交互式 Root Shell
     */
    fun start(): Boolean {
        return try {
            Log.i(TAG, "Starting interactive root shell...")
            val pb = ProcessBuilder("su")
                .redirectErrorStream(true)
            
            // 设置环境变量
            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["PATH"] = "/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"
            
            val p = pb.start()
            process = p
            outputStream = p.outputStream
            inputStream = p.inputStream
            errorStream = p.errorStream
            isRunning.set(true)

            // 启动异步读取线程
            Thread({
                val buffer = ByteArray(4096)
                try {
                    while (isRunning.get()) {
                        val bytesRead = inputStream?.read(buffer) ?: -1
                        if (bytesRead > 0) {
                            val data = buffer.copyOf(bytesRead)
                            onOutput(data)
                        } else if (bytesRead == -1) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading from root shell: ${e.message}")
                } finally {
                    val exitCode = try { p.waitFor() } catch (_: Exception) { -1 }
                    onExit(exitCode)
                    stop()
                }
            }, "RustDesk-RootShell-Reader").start()

            Log.i(TAG, "RootTerminalSession initialized successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start RootTerminalSession: ${e.message}")
            stop()
            false
        }
    }

    /**
     * 向 Root Shell 写入输入数据 (如键盘按键、命令)
     */
    fun writeInput(data: ByteArray) {
        if (!isRunning.get()) return
        try {
            outputStream?.write(data)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write input to root shell: ${e.message}")
        }
    }

    /**
     * 关闭终端会话
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            outputStream?.write("exit\n".toByteArray())
            outputStream?.flush()
            outputStream?.close()
        } catch (_: Exception) {}

        try {
            inputStream?.close()
        } catch (_: Exception) {}

        try {
            process?.destroy()
        } catch (_: Exception) {}

        process = null
        outputStream = null
        inputStream = null
        Log.i(TAG, "RootTerminalSession stopped.")
    }
}
