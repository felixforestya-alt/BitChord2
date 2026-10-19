package com.music.bitchord.desktop

import com.music.bitchord.ui.AppActivity
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Frame
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities

/**
 * Keeps [AppActivity.quiet] true while nobody can see the window — minimised, put away to the
 * tray, on another virtual desktop, or covered whole by another window (a game in front of it,
 * typically) — so the app plays music without drawing anything for it.
 *
 * Losing focus alone does not count: a player on a second screen beside a game is being watched,
 * and freezes nothing. Whether it is covered is looked at twice a second while it does not have
 * focus, which costs nothing anyone would notice.
 */
internal object DesktopActivityWatch {

    fun attach(window: Window) {
        val listener = object : WindowAdapter() {
            override fun windowGainedFocus(e: WindowEvent) = update(window)
            override fun windowLostFocus(e: WindowEvent) = update(window)
            override fun windowActivated(e: WindowEvent) = update(window)
            override fun windowDeactivated(e: WindowEvent) = update(window)
            override fun windowIconified(e: WindowEvent) = update(window)
            override fun windowDeiconified(e: WindowEvent) = update(window)
            override fun windowStateChanged(e: WindowEvent) = update(window)
        }
        window.addWindowFocusListener(listener)
        window.addWindowListener(listener)
        window.addWindowStateListener(listener)
        window.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentShown(e: java.awt.event.ComponentEvent) = update(window)
            override fun componentHidden(e: java.awt.event.ComponentEvent) = update(window)
            override fun componentMoved(e: java.awt.event.ComponentEvent) = update(window)
            override fun componentResized(e: java.awt.event.ComponentEvent) = update(window)
        })
        // What covers the window changes without telling it: looked at again now and then.
        javax.swing.Timer(COVER_CHECK_MS) { update(window) }.apply { isRepeats = true }.start()
        update(window)
    }

    private fun update(window: Window) {
        // A popup of our own takes focus as the app is used: read who has it once it has moved.
        SwingUtilities.invokeLater {
            val minimised = window is Frame && (window.extendedState and Frame.ICONIFIED) != 0
            val active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            val ours = active != null && (active === window || SwingUtilities.getWindowAncestor(active) === window || active.owner === window)
            val hidden = !window.isShowing || minimised || (!ours && DesktopPlatform.isWindows && covered(window))
            // BITCHORD_NEVER_QUIET=1, for measuring: the window always counts as seen.
            val quiet = hidden && System.getenv("BITCHORD_NEVER_QUIET") != "1"
            if (AppActivity.quiet != quiet) {
                AppActivity.quiet = quiet
                DesktopTrackLog.log("window ${if (quiet) "out of sight: animations held" else "in sight: animations back"}")
            }
        }
    }

    /**
     * Whether the window is out of sight on Windows: cloaked (another virtual desktop), or the
     * window in front covers all of it.
     */
    private fun covered(window: Window): Boolean = runCatching {
        val ours = Native.getWindowPointer(window) ?: return false
        val cloaked = IntByReference(0)
        val dwm = NativeLibrary.getInstance("dwmapi").getFunction("DwmGetWindowAttribute")
        if (dwm.invokeInt(arrayOf(ours, DWMWA_CLOAKED, cloaked, 4)) == 0 && cloaked.value != 0) return true
        val user32 = NativeLibrary.getInstance("user32")
        val front = user32.getFunction("GetForegroundWindow").invokePointer(arrayOf()) ?: return false
        if (front == ours) return false
        // Only a window that is really there: visible, and not one of the shell's own.
        if (user32.getFunction("IsWindowVisible").invokeInt(arrayOf(front)) == 0) return false
        if (user32.getFunction("IsIconic").invokeInt(arrayOf(front)) != 0) return false
        val frontRect = rectOf(user32, front) ?: return false
        val ourRect = rectOf(user32, ours) ?: return false
        frontRect[0] <= ourRect[0] && frontRect[1] <= ourRect[1] && frontRect[2] >= ourRect[2] && frontRect[3] >= ourRect[3]
    }.getOrDefault(false)

    private fun rectOf(user32: NativeLibrary, hwnd: Pointer): IntArray? {
        val rect = Memory(16)
        if (user32.getFunction("GetWindowRect").invokeInt(arrayOf(hwnd, rect)) == 0) return null
        return IntArray(4) { rect.getInt(it * 4L) }
    }

    private const val COVER_CHECK_MS = 500
    private const val DWMWA_CLOAKED = 14
}
