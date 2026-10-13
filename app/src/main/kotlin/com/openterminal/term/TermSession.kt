package com.openterminal.term

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.openterminal.data.CommandHistory
import com.openterminal.data.Settings
import com.openterminal.shell.IShellService
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

/** One shell running on a PTY inside the Shizuku user service, rendered by a Termux [TerminalEmulator]. */
class TermSession(
    private val service: IShellService,
    private val appContext: Context,
    val number: Int,
    initialRows: Int = 24,
    initialCols: Int = 80,
) : TerminalOutput() {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val id: Int = service.openSession(initialRows, initialCols, "/data/local/tmp")
    private val master: ParcelFileDescriptor = service.getMasterFd(id)
    private val output = FileOutputStream(master.fileDescriptor)

    /** Bumped whenever the screen content changes so Compose redraws. */
    var revision by mutableIntStateOf(0)
        private set
    var title by mutableStateOf("shell $number")
        private set
    var exitCode by mutableStateOf<Int?>(null)
        private set

    /** What the user has typed on the current prompt line (best effort, used for suggestions). */
    var inputLine by mutableStateOf("")
        private set

    @Volatile private var closed = false

    val emulator: TerminalEmulator = TerminalEmulator(
        this, initialCols, initialRows, 10, 20, Settings.scrollbackLines, NoopClient,
    ).also { applyPalette(it) }

    init {
        Thread({ readLoop() }, "pty-reader-$id").apply { isDaemon = true }.start()
        Thread({
            val code = runCatching { service.waitFor(id) }.getOrDefault(-1)
            main.post { exitCode = code; revision++ }
        }, "pty-wait-$id").apply { isDaemon = true }.start()
    }

    private fun readLoop() {
        val input = FileInputStream(service.getMasterFd(id).fileDescriptor)
        val buf = ByteArray(8192)
        try {
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                val chunk = buf.copyOf(n)
                main.post { emulator.append(chunk, n); revision++ }
            }
        } catch (_: Exception) {
            // EIO when the child exits.
        }
    }

    // --- TerminalOutput (emulator -> shell) ---

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (closed) return
        val copy = data.copyOfRange(offset, offset + count)
        io.execute { runCatching { output.write(copy); output.flush() } }
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        title = newTitle?.takeIf { it.isNotBlank() } ?: "shell $number"
    }

    override fun onCopyTextToClipboard(text: String?) {
        val cm = appContext.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("OpenTerminal", text ?: ""))
    }

    override fun onPasteTextFromClipboard() {
        val cm = appContext.getSystemService(ClipboardManager::class.java)
        cm.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString()?.let { paste(it) }
    }

    override fun onBell() {}

    override fun onColorsChanged() { revision++ }

    // --- User input ---

    /** Text typed by the user (or inserted via the key bar / suggestions). */
    fun sendText(text: String) {
        trackInput(text)
        write(text)
    }

    fun paste(text: String) {
        if (text.isEmpty()) return
        val normalized = text.replace("\r\n", "\n").replace('\n', '\r')
        emulator.paste(normalized)
        trackInput(normalized)
    }

    fun sendKey(keyCode: Int, mods: Int): Boolean {
        val code = KeyHandler.getCode(keyCode, mods, emulator.isCursorKeysApplicationMode, emulator.isKeypadApplicationMode)
            ?: return false
        sendText(code)
        return true
    }

    /** Sends Ctrl+<letter>. */
    fun sendCtrl(c: Char) = sendText((c.uppercaseChar().code and 0x1f).toChar().toString())

    private fun trackInput(text: String) {
        var line = inputLine
        for (ch in text) {
            when {
                ch == '\r' || ch == '\n' -> { CommandHistory.add(line); line = "" }
                ch == '\u007f' || ch == '\b' -> line = line.dropLast(1)
                ch == '\u0003' || ch == '\u0015' || ch == '\u0004' -> line = ""
                ch == '\u001b' -> { line = ""; break }
                ch >= ' ' -> line += ch
            }
        }
        inputLine = line
    }

    // --- Layout / lifecycle ---

    fun resize(cols: Int, rows: Int, cellW: Int, cellH: Int) {
        if (cols < 1 || rows < 1) return
        if (cols == emulator.mColumns && rows == emulator.mRows) return
        emulator.resize(cols, rows, cellW, cellH)
        io.execute { runCatching { service.resize(id, rows, cols) } }
        revision++
    }

    fun applyPalette() { applyPalette(emulator); revision++ }

    private fun applyPalette(e: TerminalEmulator) {
        val p = Settings.palette
        e.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = p.fg
        e.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = p.bg
        e.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = p.cursor
    }

    fun close() {
        if (closed) return
        closed = true
        io.execute {
            runCatching { service.closeSession(id) }
            runCatching { output.close() }
            runCatching { master.close() }
        }
        io.shutdown()
    }

    private object NoopClient : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {}
        override fun onTitleChanged(changedSession: TerminalSession) {}
        override fun onSessionFinished(finishedSession: TerminalSession) {}
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }
}
