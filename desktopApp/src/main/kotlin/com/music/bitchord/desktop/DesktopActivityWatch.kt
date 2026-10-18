package com.music.bitchord.desktop

import com.music.bitchord.ui.AppActivity
import java.awt.Frame
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities

/**
 * Keeps [AppActivity.quiet] true while nobody is looking at the window — another window in front
 * (a game, typically), the window minimised, or put away to the tray — so the app plays music in
 * the background without drawing anything for it.
 *
 * Losing focus to one of the app's own popups (a menu, a dialog) does not count: those are the
 * app being looked at.
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
        })
        update(window)
    }

    private fun update(window: Window) {
        // A popup of our own takes focus as the app is used: read who has it once it has moved.
        SwingUtilities.invokeLater {
            val minimised = window is Frame && (window.extendedState and Frame.ICONIFIED) != 0
            val active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            val ours = active != null && (active === window || SwingUtilities.getWindowAncestor(active) === window || active.owner === window)
            // BITCHORD_NEVER_QUIET=1, for measuring: the window always counts as looked at.
            val quiet = (!window.isShowing || minimised || !ours) && System.getenv("BITCHORD_NEVER_QUIET") != "1"
            if (AppActivity.quiet != quiet) {
                AppActivity.quiet = quiet
                DesktopTrackLog.log("window ${if (quiet) "in the background: animations held" else "in front: animations back"}")
            }
        }
    }
}
