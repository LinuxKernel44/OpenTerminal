package com.openterminal.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import android.text.InputType
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.openterminal.term.TermSession
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.view.TerminalRenderer
import kotlin.math.max
import kotlin.math.min

/** Sticky modifier toggles shared between the key bar and the terminal view. */
class StickyModifiers {
    var ctrl = false
    var alt = false
    var onChanged: (() -> Unit)? = null

    fun toggleCtrl() { ctrl = !ctrl; onChanged?.invoke() }
    fun toggleAlt() { alt = !alt; onChanged?.invoke() }
    fun clear() { if (ctrl || alt) { ctrl = false; alt = false; onChanged?.invoke() } }
}

/** Canvas based terminal view: renders a [TermSession], handles touch, selection, hardware keys and the IME. */
class TerminalView(context: Context) : View(context) {

    var session: TermSession? = null
        set(value) {
            if (field !== value) {
                field = value
                topRow = 0
                clearSelection()
                updateSize()
                invalidate()
            }
        }

    var modifiers: StickyModifiers = StickyModifiers()
    var onFontSizeChange: ((Int) -> Unit)? = null
    var haptics = true

    var fontSizePx = 14
        private set
    private var renderer: TerminalRenderer = TerminalRenderer(fontSizePx, Typeface.MONOSPACE)

    /** 0 = bottom (live screen); negative values scroll back into the transcript. */
    private var topRow = 0
    private var scrollRemainder = 0f

    private var selecting = false
    private var selX1 = -1
    private var selY1 = -1
    private var selX2 = -1
    private var selY2 = -1
    private var selAnchorX = 0
    private var selAnchorY = 0

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setFontSize(sp: Int) {
        val px = (sp * resources.displayMetrics.scaledDensity).toInt().coerceAtLeast(8)
        if (px == fontSizePx && renderer.fontWidth > 0) return
        fontSizePx = px
        renderer = TerminalRenderer(px, Typeface.MONOSPACE)
        updateSize()
        invalidate()
    }

    private fun updateSize() {
        val s = session ?: return
        if (width <= 0 || height <= 0) return
        val cols = max(4, (width / renderer.fontWidth).toInt())
        val rows = max(2, height / renderer.fontLineSpacing)
        s.resize(cols, rows, renderer.fontWidth.toInt(), renderer.fontLineSpacing)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateSize()
    }

    override fun onDraw(canvas: Canvas) {
        val s = session ?: return
        val emulator = s.emulator
        // Keep the viewport in range if the transcript shrank.
        topRow = topRow.coerceIn(-emulator.screen.activeTranscriptRows, 0)
        renderer.render(emulator, canvas, topRow, selY1, selY2, selX1, selX2)
    }

    // --- Touch ---

    private fun columnAt(x: Float) = (x / renderer.fontWidth).toInt().coerceIn(0, (session?.emulator?.mColumns ?: 1) - 1)
    private fun rowAt(y: Float) = topRow + (y / renderer.fontLineSpacing).toInt()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var factor = 1f
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { factor = 1f; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            factor *= detector.scaleFactor
            val current = fontSizePx / resources.displayMetrics.scaledDensity
            if (factor > 1.1f) { onFontSizeChange?.invoke(min(32, current.toInt() + 1)); factor = 1f }
            else if (factor < 0.9f) { onFontSizeChange?.invoke(max(8, current.toInt() - 1)); factor = 1f }
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (selecting || selX1 >= 0) { clearSelection(); invalidate(); return true }
            requestFocus()
            showKeyboard()
            val emu = session?.emulator
            if (emu != null && emu.isMouseTrackingActive) {
                val col = columnAt(e.x) + 1
                val row = (e.y / renderer.fontLineSpacing).toInt() + 1
                emu.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, col, row, true)
                emu.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, col, row, false)
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (selecting) return true
            val emu = session?.emulator ?: return true
            scrollRemainder += dy / renderer.fontLineSpacing
            val lines = scrollRemainder.toInt()
            if (lines == 0) return true
            scrollRemainder -= lines
            if (emu.isAlternateBufferActive) {
                // Full screen apps (vim, less, htop): scroll by sending arrow keys / mouse wheel.
                val up = lines < 0
                repeat(kotlin.math.abs(lines)) {
                    if (emu.isMouseTrackingActive) {
                        emu.sendMouseEvent(if (up) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, 1, 1, true)
                    } else {
                        session?.sendKey(if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN, 0)
                    }
                }
            } else {
                topRow = (topRow + lines).coerceIn(-emu.screen.activeTranscriptRows, 0)
                invalidate()
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val emu = session?.emulator ?: return
            if (haptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            selecting = true
            selAnchorX = columnAt(e.x)
            selAnchorY = rowAt(e.y)
            // Select the word under the finger to start with.
            val word = emu.screen.getWordAtLocation(selAnchorX, selAnchorY)
            var x1 = selAnchorX
            var x2 = selAnchorX
            if (!word.isNullOrEmpty()) {
                val line = emu.screen.getSelectedText(0, selAnchorY, emu.mColumns, selAnchorY)
                val idx = line.indexOf(word)
                if (idx >= 0) { x1 = idx; x2 = idx + word.length - 1 }
            }
            selX1 = x1; selX2 = x2; selY1 = selAnchorY; selY2 = selAnchorY
            invalidate()
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true
        if (selecting && event.actionMasked == MotionEvent.ACTION_MOVE) {
            val x = columnAt(event.x)
            val y = rowAt(event.y)
            if (y < selAnchorY || (y == selAnchorY && x < selAnchorX)) {
                selX1 = x; selY1 = y; selX2 = selAnchorX; selY2 = selAnchorY
            } else {
                selX1 = selAnchorX; selY1 = selAnchorY; selX2 = x; selY2 = y
            }
            invalidate()
            return true
        }
        if (selecting && (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)) {
            selecting = false
            copySelection()
            return true
        }
        return gestures.onTouchEvent(event)
    }

    private fun copySelection() {
        val emu = session?.emulator ?: return
        if (selX1 < 0) return
        val text = emu.screen.getSelectedText(selX1, selY1, selX2, selY2).trim()
        if (text.isNotEmpty()) {
            session?.onCopyTextToClipboard(text)
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearSelection() {
        selecting = false
        selX1 = -1; selY1 = -1; selX2 = -1; selY2 = -1
    }

    fun showKeyboard() {
        requestFocus()
        context.getSystemService(InputMethodManager::class.java).showSoftInput(this, 0)
    }

    // --- Keyboard ---

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, true) {
            private var composing = ""

            override fun getEditable() = null

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                composing = ""
                if (!text.isNullOrEmpty()) sendTyped(text.toString())
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                composing = text?.toString() ?: ""
                return true
            }

            override fun finishComposingText(): Boolean {
                if (composing.isNotEmpty()) { sendTyped(composing); composing = "" }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (composing.isNotEmpty()) { composing = ""; return true }
                repeat(max(1, beforeLength)) { session?.sendKey(KeyEvent.KEYCODE_DEL, 0) }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) handleKey(event.keyCode, event)
                return true
            }
        }
    }

    /** Text coming from the IME, with sticky Ctrl/Alt applied. */
    private fun sendTyped(text: String) {
        val s = session ?: return
        var out = text.replace("\n", "\r")
        if (modifiers.ctrl && out.length == 1) {
            val c = out[0]
            if (c in 'a'..'z' || c in 'A'..'Z' || c in "[\\]^_@") out = (c.uppercaseChar().code and 0x1f).toChar().toString()
        }
        if (modifiers.alt) out = "\u001b$out"
        modifiers.clear()
        s.sendText(out)
        if (topRow != 0) { topRow = 0; invalidate() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        return handleKey(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    private fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        val s = session ?: return false
        if (KeyEvent.isModifierKey(keyCode)) return false
        if (topRow != 0) { topRow = 0; invalidate() }
        var mods = 0
        if (event.isCtrlPressed || modifiers.ctrl) mods = mods or KeyHandler.KEYMOD_CTRL
        if (event.isAltPressed || modifiers.alt) mods = mods or KeyHandler.KEYMOD_ALT
        if (event.isShiftPressed) mods = mods or KeyHandler.KEYMOD_SHIFT
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            modifiers.clear(); s.sendText("\r"); return true
        }
        if (s.sendKey(keyCode, mods)) { modifiers.clear(); return true }
        val uc = event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv())
        if (uc != 0) {
            var text = String(Character.toChars(uc and KeyCharacterMap_COMBINING_MASK.inv()))
            if (mods and KeyHandler.KEYMOD_CTRL != 0 && text.length == 1) {
                val c = text[0]
                if (c in 'a'..'z' || c in 'A'..'Z' || c in "[\\]^_@") text = (c.uppercaseChar().code and 0x1f).toChar().toString()
            }
            if (mods and KeyHandler.KEYMOD_ALT != 0) text = "\u001b$text"
            modifiers.clear()
            s.sendText(text)
            return true
        }
        return false
    }

    private companion object {
        const val KeyCharacterMap_COMBINING_MASK = 0x80000000.toInt()
    }
}
