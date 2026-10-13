package com.openterminal.data

import android.content.Context
import android.content.SharedPreferences

/** Persistent command history plus a built-in list of commonly used Android shell commands. */
object CommandHistory {
    private const val MAX = 500
    private lateinit var prefs: SharedPreferences
    private val entries = ArrayDeque<String>()

    val builtin = listOf(
        "pm list packages", "pm list packages -3", "pm path ", "pm grant ", "pm revoke ", "pm clear ", "pm disable-user --user 0 ",
        "pm enable ", "pm uninstall --user 0 ", "pm install ", "am start -n ", "am start -a android.intent.action.VIEW -d ",
        "am force-stop ", "am broadcast -a ", "am kill-all", "dumpsys battery", "dumpsys activity activities", "dumpsys window",
        "dumpsys package ", "dumpsys meminfo ", "dumpsys cpuinfo", "dumpsys notification", "dumpsys wifi", "dumpsys display",
        "settings get global ", "settings put global ", "settings get secure ", "settings put system ", "getprop ro.build.version.release",
        "getprop", "setprop ", "logcat -d", "logcat -c", "logcat *:E", "input keyevent ", "input tap ", "input swipe ", "input text ",
        "screencap -p /sdcard/screen.png", "screenrecord /sdcard/rec.mp4", "wm size", "wm density", "cmd package list packages",
        "cmd appops set ", "cmd notification ", "ps -A", "top -n 1", "df -h", "free -m", "cat /proc/cpuinfo", "cat /proc/meminfo",
        "ls -la ", "cd /sdcard", "cd /data/local/tmp", "id", "whoami", "uname -a", "ip addr", "netstat -tulpn", "ifconfig",
        "svc wifi enable", "svc wifi disable", "svc data enable", "svc data disable", "reboot", "reboot recovery", "reboot bootloader",
        "monkey -p ", "content query --uri ", "service list", "getenforce", "uptime",
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
        prefs.getString("entries", "")?.split('\n')?.filter { it.isNotBlank() }?.let { entries.addAll(it) }
    }

    val all: List<String> get() = entries.toList().asReversed()

    fun add(command: String) {
        val c = command.trim()
        if (c.isEmpty()) return
        entries.remove(c)
        entries.addLast(c)
        while (entries.size > MAX) entries.removeFirst()
        prefs.edit().putString("entries", entries.joinToString("\n")).apply()
    }

    fun clear() {
        entries.clear()
        prefs.edit().remove("entries").apply()
    }

    /** Suggestions that extend [prefix], most recent history first, then built-ins. */
    fun suggest(prefix: String, limit: Int = 8): List<String> {
        if (prefix.isBlank()) return emptyList()
        val fromHistory = all.filter { it.startsWith(prefix) && it != prefix }
        val fromBuiltin = builtin.filter { it.startsWith(prefix) && it != prefix }
        return (fromHistory + fromBuiltin).distinct().take(limit)
    }
}
