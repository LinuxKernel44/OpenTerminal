package com.openterminal.ui

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.openterminal.data.CommandHistory
import com.openterminal.data.Settings
import com.openterminal.term.SessionManager
import com.openterminal.term.TermSession

@Composable
fun TerminalScreen(backdrop: Backdrop, colors: GlassColors) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (SessionManager.sessions.isEmpty()) SessionManager.newSession(context)
    }
    val session = SessionManager.active
    val modifiers = remember { StickyModifiers() }
    var modRevision by remember { mutableIntStateOf(0) }
    modifiers.onChanged = { modRevision++ }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TabBar(backdrop, colors, onHistory = { showHistory = true }, onSettings = { showSettings = true })

        GlassPanel(backdrop, colors, Modifier.weight(1f).fillMaxWidth(), cornerRadius = 24.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
            val tvKey = session?.number ?: 0
            AndroidView(
                factory = { ctx -> TerminalView(ctx) },
                modifier = Modifier.fillMaxSize().padding(10.dp),
                update = { view ->
                    view.modifiers = modifiers
                    view.haptics = Settings.haptics
                    view.onFontSizeChange = { Settings.fontSize = it; Settings.save() }
                    view.setFontSize(Settings.fontSize)
                    view.session = session
                    // Reading these makes Compose re-run `update` on every screen change.
                    session?.revision
                    view.invalidate()
                    if (session != null && view.tag != tvKey) { view.tag = tvKey; view.post { view.showKeyboard() } }
                },
                onRelease = { it.session = null },
            )
            session?.exitCode?.let { code ->
                Text(
                    "Process exited ($code). Close this tab or open a new one.",
                    color = colors.onGlassDim, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }

        if (Settings.showSuggestions && session != null) Suggestions(session, backdrop, colors)
        if (Settings.showKeyBar && session != null) KeyBar(session, modifiers, modRevision, backdrop, colors)
    }

    if (showHistory) HistorySheet(backdrop, colors, onRun = { cmd ->
        SessionManager.active?.sendText(cmd + "\r"); showHistory = false
    }, onDismiss = { showHistory = false })
    if (showSettings) SettingsSheet(backdrop, colors) { showSettings = false }
}

@Composable
private fun TabBar(backdrop: Backdrop, colors: GlassColors, onHistory: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionManager.sessions.forEachIndexed { i, s ->
                val selected = i == SessionManager.activeIndex
                GlassButton({ SessionManager.activeIndex = i }, backdrop, colors, selected = selected) {
                    Text(
                        s.title.take(18), color = colors.onGlass, fontSize = 13.sp, maxLines = 1,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text("✕", color = colors.onGlassDim, fontSize = 13.sp, modifier = Modifier.clickable { SessionManager.close(i) }.padding(start = 4.dp))
                }
            }
            GlassButton({ SessionManager.newSession(context) }, backdrop, colors) { Text("+", color = colors.onGlass, fontSize = 18.sp) }
        }
        GlassButton(onHistory, backdrop, colors) { Text("History", color = colors.onGlass, fontSize = 13.sp) }
        GlassButton(onSettings, backdrop, colors) { Text("⚙", color = colors.onGlass, fontSize = 16.sp) }
    }
}

@Composable
private fun Suggestions(session: TermSession, backdrop: Backdrop, colors: GlassColors) {
    val line = session.inputLine
    val items = CommandHistory.suggest(line)
    if (items.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { s ->
            GlassButton({ session.sendText(s.removePrefix(line)) }, backdrop, colors, height = 36.dp) {
                Text(s, color = colors.onGlass, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun KeyBar(session: TermSession, mods: StickyModifiers, modRevision: Int, backdrop: Backdrop, colors: GlassColors) {
    val context = LocalContext.current
    @Composable
    fun key(label: String, selected: Boolean = false, onClick: () -> Unit) {
        GlassButton(onClick, backdrop, colors, selected = selected, height = 38.dp) {
            Text(label, color = colors.onGlass, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
    fun k(code: Int) { session.sendKey(code, 0) }
    modRevision // recomposed when sticky modifiers change
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        key("ESC") { session.sendText("\u001b") }
        key("TAB") { session.sendText("\t") }
        key("CTRL", mods.ctrl) { mods.toggleCtrl() }
        key("ALT", mods.alt) { mods.toggleAlt() }
        key("↑") { k(KeyEvent.KEYCODE_DPAD_UP) }
        key("↓") { k(KeyEvent.KEYCODE_DPAD_DOWN) }
        key("←") { k(KeyEvent.KEYCODE_DPAD_LEFT) }
        key("→") { k(KeyEvent.KEYCODE_DPAD_RIGHT) }
        key("HOME") { k(KeyEvent.KEYCODE_MOVE_HOME) }
        key("END") { k(KeyEvent.KEYCODE_MOVE_END) }
        key("PGUP") { k(KeyEvent.KEYCODE_PAGE_UP) }
        key("PGDN") { k(KeyEvent.KEYCODE_PAGE_DOWN) }
        key("^C") { session.sendCtrl('c') }
        key("^D") { session.sendCtrl('d') }
        key("^Z") { session.sendCtrl('z') }
        key("^L") { session.sendCtrl('l') }
        listOf("|", "/", "-", "~", "_", "\\", "$", "&", "*", "\"", "'", "`").forEach { c -> key(c) { session.sendText(c) } }
        key("PASTE") {
            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
            cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { session.paste(it) }
        }
    }
}
