package com.openterminal.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.openterminal.BuildConfig
import com.openterminal.shell.IShellService
import com.openterminal.shell.ShellUserService
import rikka.shizuku.Shizuku

enum class ShizukuState { NotInstalled, NotRunning, NoPermission, Connecting, Ready }

/** Tracks the Shizuku server, the runtime permission and the bound shell user service. */
object ShizukuManager {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4242

    private lateinit var appContext: Context

    var state by mutableStateOf(ShizukuState.NotRunning)
        private set
    var service: IShellService? by mutableStateOf(null)
        private set
    var shellUid by mutableStateOf(-1)
        private set

    private var binding = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            binding = false
            val s = IShellService.Stub.asInterface(binder)
            shellUid = runCatching { s.uid }.getOrDefault(-1)
            service = s
            state = ShizukuState.Ready
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            binding = false
            refresh()
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        binding = false
        refresh()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun init(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun isShizukuInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Re-evaluates the state and, when everything is in place, binds the user service. */
    fun refresh() {
        if (service != null && Shizuku.pingBinder()) {
            state = ShizukuState.Ready
            return
        }
        if (!Shizuku.pingBinder()) {
            state = if (isShizukuInstalled()) ShizukuState.NotRunning else ShizukuState.NotInstalled
            return
        }
        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            state = ShizukuState.NoPermission
            return
        }
        state = ShizukuState.Connecting
        bind()
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) return
        Shizuku.requestPermission(REQUEST_CODE)
    }

    fun openShizukuApp(): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        return true
    }

    private fun bind() {
        if (binding) return
        binding = true
        val args = Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        try {
            Shizuku.bindUserService(args, connection)
        } catch (t: Throwable) {
            android.util.Log.e("OpenTerminal", "bindUserService failed", t)
            binding = false
            state = ShizukuState.NoPermission
        }
    }
}
