package com.openterminal.term

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.openterminal.shizuku.ShizukuManager

/** Holds the open terminal tabs. Lives for the whole process so rotation never kills a shell. */
object SessionManager {
    val sessions = mutableStateListOf<TermSession>()
    var activeIndex by mutableIntStateOf(0)
    private var counter = 0

    val active: TermSession? get() = sessions.getOrNull(activeIndex)

    fun newSession(context: Context): TermSession? {
        val service = ShizukuManager.service ?: return null
        val session = runCatching { TermSession(service, context.applicationContext, ++counter) }.getOrNull() ?: return null
        sessions += session
        activeIndex = sessions.lastIndex
        return session
    }

    fun close(index: Int) {
        val s = sessions.getOrNull(index) ?: return
        s.close()
        sessions.removeAt(index)
        activeIndex = activeIndex.coerceIn(0, (sessions.size - 1).coerceAtLeast(0))
    }

    fun closeAll() {
        sessions.forEach { it.close() }
        sessions.clear()
        activeIndex = 0
    }
}
