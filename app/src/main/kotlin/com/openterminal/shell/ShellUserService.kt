package com.openterminal.shell

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/**
 * Runs inside the Shizuku server's process space as the shell user (uid 2000) or root.
 * It owns the PTYs and hands the master side to the app through Binder.
 */
class ShellUserService(context: Context) : IShellService.Stub() {

    private class Session(val master: ParcelFileDescriptor, val pid: Int)

    private val sessions = ConcurrentHashMap<Int, Session>()
    private val nextId = AtomicInteger(1)

    init {
        try {
            System.loadLibrary("openterminal")
        } catch (_: UnsatisfiedLinkError) {
            System.load(File(context.applicationInfo.nativeLibraryDir, "libopenterminal.so").absolutePath)
        }
    }

    override fun destroy() {
        sessions.keys.forEach { closeSession(it) }
        exitProcess(0)
    }

    override fun openSession(rows: Int, cols: Int, cwd: String): Int {
        val isRoot = Process.myUid() == 0
        val home = if (isRoot) "/data/local/tmp" else "/data/local/tmp"
        val env = arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=$home",
            "TMPDIR=/data/local/tmp",
            "PATH=/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin",
            "ANDROID_ROOT=/system",
            "ANDROID_DATA=/data",
            "LANG=en_US.UTF-8",
            "USER=" + (if (isRoot) "root" else "shell"),
        )
        val workDir = if (File(cwd).isDirectory) cwd else home
        val (fd, pid) = Pty.nativeOpen("/system/bin/sh", workDir, env, rows, cols).let { it[0] to it[1] }
        val id = nextId.getAndIncrement()
        sessions[id] = Session(ParcelFileDescriptor.adoptFd(fd), pid)
        return id
    }

    private fun session(id: Int) = sessions[id] ?: error("No such session: $id")

    override fun getMasterFd(id: Int): ParcelFileDescriptor = session(id).master.dup()

    override fun getPid(id: Int): Int = session(id).pid

    override fun resize(id: Int, rows: Int, cols: Int) {
        Pty.nativeSetSize(session(id).master.fd, rows, cols)
    }

    override fun waitFor(id: Int): Int = Pty.nativeWaitFor(session(id).pid)

    override fun closeSession(id: Int) {
        val s = sessions.remove(id) ?: return
        Pty.nativeKill(s.pid)
        runCatching { s.master.close() }
    }

    override fun getUid(): Int = Process.myUid()
}
