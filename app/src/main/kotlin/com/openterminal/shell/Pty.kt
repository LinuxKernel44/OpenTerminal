package com.openterminal.shell

/** JNI bindings for PTY creation. Only ever called inside the Shizuku user service. */
internal object Pty {
    @JvmStatic external fun nativeOpen(shell: String, cwd: String, env: Array<String>, rows: Int, cols: Int): IntArray
    @JvmStatic external fun nativeSetSize(fd: Int, rows: Int, cols: Int)
    @JvmStatic external fun nativeWaitFor(pid: Int): Int
    @JvmStatic external fun nativeKill(pid: Int)
}
