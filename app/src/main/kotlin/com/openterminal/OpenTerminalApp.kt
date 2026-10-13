package com.openterminal

import android.app.Application
import com.openterminal.data.CommandHistory
import com.openterminal.data.Settings
import com.openterminal.shizuku.ShizukuManager

class OpenTerminalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        CommandHistory.init(this)
        ShizukuManager.init(this)
    }
}
