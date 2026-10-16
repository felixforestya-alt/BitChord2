package com.music.bitchord.desktop

import androidx.compose.ui.graphics.toAwtImage
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JDialog
import javax.swing.JPanel

/**
 * The tray's menu, drawn rather than left to the system: a dark rounded card with what is
 * playing, the transport as three round buttons, and the app's entries below, each lit under the
 * pointer. It closes when it loses focus, as a menu does.
 */
internal object DesktopTrayPopup {

    private var open: JDialog? = null

    /** Opens at [at] (screen pixels), kept on the screen it was asked for. */
    fun show(at: Point, art: Image?) {
        EventQueue.invokeLater {
            open?.dispose()
            val menu = DesktopTrayMenu.snapshot()
            val coverUrl = DesktopTrayMenu.artworkUrl
            val dialog = JDialog().apply {
                isUndecorated = true
                type = java.awt.Window.Type.POPUP
                isAlwaysOnTop = true
                background = Color(0, 0, 0, 0)
            }
            // The cover when there is one, held or fetched; the app's icon until it arrives.
            val held = DesktopArtworkCache.peek(coverUrl)?.toAwtImage()
            val panel = MenuPanel(menu, held ?: art, rounded = held != null) { dialog.dispose() }
            if (held == null && coverUrl != null) {
                Thread {
                    val loaded = kotlinx.coroutines.runBlocking { DesktopArtworkCache.load(coverUrl) }?.toAwtImage()
                    if (loaded != null) EventQueue.invokeLater { panel.showCover(loaded) }
                }.apply { isDaemon = true }.start()
            }
            dialog.contentPane = panel
            dialog.pack()
            dialog.location = placed(at, dialog.size)
            dialog.addWindowFocusListener(object : java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) = Unit
                override fun windowLostFocus(e: WindowEvent?) = dialog.dispose()
            })
            dialog.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent?) {
                    if (open === dialog) open = null
                }
            })
            open = dialog
            dialog.isVisible = true
            dialog.toFront()
            dialog.requestFocus()
        }
    }

    /** Above and to the left of the pointer, the way the taskbar's own menus open. */
    private fun placed(at: Point, size: Dimension): Point {
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .map { it.defaultConfiguration }
            .firstOrNull { it.bounds.contains(at) }
        val bounds = screen?.bounds ?: Rectangle(Toolkit.getDefaultToolkit().screenSize)
        val insets = screen?.let { Toolkit.getDefaultToolkit().getScreenInsets(it) } ?: java.awt.Insets(0, 0, 0, 0)
        val work = Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom,
        )
        var x = at.x - size.width + 12
        var y = at.y - size.height - 6
        if (y < work.y) y = at.y + 6
        x = x.coerceIn(work.x + 4, work.x + work.width - size.width - 4)
        y = y.coerceIn(work.y + 4, work.y + work.height - size.height - 4)
        return Point(x, y)
    }

    internal class MenuPanel(
        private val menu: DesktopTrayMenu.Snapshot,
        private var art: Image?,
        private var rounded: Boolean = false,
        private val close: () -> Unit,
    ) : JPanel() {

        fun showCover(cover: Image) {
            art = cover
            rounded = true
            repaint()
        }


        private sealed class Hit(val bounds: Rectangle, val action: () -> Unit) {
            class Round(bounds: Rectangle, action: () -> Unit, val kind: Int) : Hit(bounds, action)
            class Row(bounds: Rectangle, action: () -> Unit, val label: String, val danger: Boolean) : Hit(bounds, action)
        }

        private val hits = mutableListOf<Hit>()
        private var hovered: Hit? = null

        private val panelWidth = 280
        private val headerHeight = 64
        private val transportHeight = 64
        private val rowHeight = 36

        /** Turned over by the round button at once, ahead of the player catching up. */
        private var playing = menu.playing

        init {
            isOpaque = false
            cursor = Cursor.getDefaultCursor()
            var y = 10 + headerHeight
            val buttons = listOf(
                0 to menu.previous,
                1 to menu.playPause,
                2 to menu.next,
            )
            val centre = panelWidth / 2
            buttons.forEachIndexed { i, (kind, action) ->
                val size = if (kind == 1) 46 else 38
                val cx = centre + (i - 1) * 64
                hits += Hit.Round(Rectangle(cx - size / 2, y + (transportHeight - size) / 2, size, size), action, kind)
            }
            y += transportHeight + 9
            fun row(label: String, danger: Boolean = false, action: () -> Unit) {
                hits += Hit.Row(Rectangle(8, y, panelWidth - 16, rowHeight), action, label, danger)
                y += rowHeight
            }
            row(DesktopStrings["widget_open_bitchord", "Open BitChord"], action = menu.openPlayer)
            row(DesktopStrings["settings", "Settings"], action = menu.openSettings)
            y += 9
            row(DesktopStrings["d_quit_bitchord", "Quit BitChord"], danger = true, action = menu.quit)
            preferredSize = Dimension(panelWidth, y + 8)

            val mouse = object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val now = hits.firstOrNull { it.bounds.contains(e.point) }
                    if (now !== hovered) {
                        hovered = now
                        cursor = if (now != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
                        repaint()
                    }
                }

                override fun mouseExited(e: MouseEvent) {
                    hovered = null
                    repaint()
                }

                override fun mouseReleased(e: MouseEvent) {
                    val hit = hits.firstOrNull { it.bounds.contains(e.point) } ?: return
                    // The transport keeps the menu open to be clicked again; the rest are done.
                    if (hit is Hit.Row) close()
                    EventQueue.invokeLater(hit.action)
                    if (hit is Hit.Round && hit.kind == 1) {
                        playing = !playing
                        repaint()
                    }
                }
            }
            addMouseListener(mouse)
            addMouseMotionListener(mouse)
        }

        override fun paintComponent(graphics: Graphics) {
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            val card = RoundRectangle2D.Float(0.5f, 0.5f, panelWidth - 1f, height - 1f, 22f, 22f)
            g.color = Color(0x1C, 0x1B, 0x22)
            g.fill(card)
            g.paint = java.awt.GradientPaint(0f, 0f, Color(0x7C, 0x6C, 0xFF, 70), panelWidth.toFloat(), 90f, Color(0x7C, 0x6C, 0xFF, 0))
            g.fill(card)
            g.color = Color(255, 255, 255, 28)
            g.stroke = BasicStroke(1f)
            g.draw(card)

            // What is playing.
            val title = menu.title
            art?.let { image ->
                val clip = g.clip
                if (rounded) g.clip(RoundRectangle2D.Float(16f, 18f, 40f, 40f, 10f, 10f))
                g.drawImage(image, 16, 18, 40, 40, null)
                g.clip = clip
            }
            g.color = Color.WHITE
            g.font = Font("Segoe UI Semibold", Font.PLAIN, 14)
            g.drawString(ellipsize(g, title ?: "BitChord", panelWidth - 84), 68, 35)
            g.color = Color(255, 255, 255, 140)
            g.font = Font("Segoe UI", Font.PLAIN, 12)
            g.drawString(
                if (title == null) {
                    DesktopStrings["d_nothing_playing", "Nothing playing"]
                } else if (playing) {
                    DesktopStrings["d_home_now_playing", "NOW PLAYING"].lowercase().replaceFirstChar { it.uppercase() }
                } else {
                    DesktopStrings["widget_pause", "Pause"]
                },
                68,
                53,
            )

            hits.forEach { hit ->
                val lit = hit === hovered
                when (hit) {
                    is Hit.Round -> {
                        val b = hit.bounds
                        if (hit.kind == 1) {
                            g.color = if (lit) Color(0xEE, 0xEE, 0xF4) else Color.WHITE
                            g.fillOval(b.x, b.y, b.width, b.height)
                            g.color = Color(0x16, 0x16, 0x1A)
                            if (playing) {
                                val w = 4.5f
                                g.fill(RoundRectangle2D.Float(b.centerX.toFloat() - 7f, b.centerY.toFloat() - 8f, w, 16f, 3f, 3f))
                                g.fill(RoundRectangle2D.Float(b.centerX.toFloat() + 2.5f, b.centerY.toFloat() - 8f, w, 16f, 3f, 3f))
                            } else {
                                g.fill(triangle(b.centerX.toFloat() + 2f, b.centerY.toFloat(), 9f, true))
                            }
                        } else {
                            if (lit) {
                                g.color = Color(255, 255, 255, 30)
                                g.fillOval(b.x, b.y, b.width, b.height)
                            }
                            g.color = Color.WHITE
                            val forward = hit.kind == 2
                            val cx = b.centerX.toFloat()
                            val cy = b.centerY.toFloat()
                            g.fill(triangle(if (forward) cx - 1f else cx + 1f, cy, 7f, forward))
                            g.fill(RoundRectangle2D.Float(if (forward) cx + 5f else cx - 7.5f, cy - 7f, 2.5f, 14f, 2f, 2f))
                        }
                    }
                    is Hit.Row -> {
                        val b = hit.bounds
                        if (lit) {
                            g.color = if (hit.danger) Color(255, 90, 90, 40) else Color(255, 255, 255, 22)
                            g.fill(RoundRectangle2D.Float(b.x.toFloat(), b.y.toFloat(), b.width.toFloat(), b.height.toFloat(), 12f, 12f))
                        }
                        g.color = if (hit.danger) Color(0xFF, 0x7A, 0x7A) else Color(255, 255, 255, 230)
                        g.font = Font("Segoe UI", Font.PLAIN, 13)
                        g.drawString(hit.label, b.x + 14, b.y + 23)
                    }
                }
            }
            // The lines between the groups.
            g.color = Color(255, 255, 255, 20)
            val firstRow = hits.filterIsInstance<Hit.Row>()
            firstRow.firstOrNull()?.let { g.fillRect(16, it.bounds.y - 5, panelWidth - 32, 1) }
            firstRow.lastOrNull()?.let { g.fillRect(16, it.bounds.y - 5, panelWidth - 32, 1) }
            g.dispose()
        }

        private fun triangle(cx: Float, cy: Float, r: Float, right: Boolean) = Path2D.Float().apply {
            if (right) {
                moveTo(cx - r * 0.8f, cy - r)
                lineTo(cx + r * 0.9f, cy)
                lineTo(cx - r * 0.8f, cy + r)
            } else {
                moveTo(cx + r * 0.8f, cy - r)
                lineTo(cx - r * 0.9f, cy)
                lineTo(cx + r * 0.8f, cy + r)
            }
            closePath()
        }

        private fun ellipsize(g: Graphics2D, text: String, max: Int): String {
            val metrics = g.fontMetrics
            if (metrics.stringWidth(text) <= max) return text
            var end = text.length
            while (end > 0 && metrics.stringWidth(text.substring(0, end) + "…") > max) end--
            return text.substring(0, end) + "…"
        }
    }
}
