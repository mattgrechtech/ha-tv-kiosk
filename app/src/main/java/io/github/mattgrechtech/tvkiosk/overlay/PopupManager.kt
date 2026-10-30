package io.github.mattgrechtech.tvkiosk.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shows [Popup]s in the screen corners. With "display over other apps" they are drawn in an
 * overlay window above any app; without it, only inside the dashboard while it is open.
 * Up to [MAX_PER_CORNER] stack in each corner; a pop-up with the id of one on screen replaces it.
 * Main thread only.
 */
class PopupManager(private val context: Context) {

    enum class Shown { OVER_EVERYTHING, IN_DASHBOARD, NOWHERE }

    private class Corner(val container: LinearLayout, val inWindow: Boolean)

    private class Entry(val popup: Popup, val view: View, val corner: Popup.Corner, val dismiss: Runnable)

    private val main = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val corners = mutableMapOf<Popup.Corner, Corner>()
    private val entries = linkedMapOf<String, Entry>()
    private var dashboardFrame: FrameLayout? = null

    /** The dashboard's root view, used when the overlay permission is missing. */
    fun attachDashboard(frame: FrameLayout) {
        dashboardFrame = frame
    }

    fun detachDashboard(frame: FrameLayout) {
        if (dashboardFrame !== frame) return
        // Pop-ups living inside the dashboard go with it.
        corners.filterValues { !it.inWindow }.keys.toList().forEach { removeCorner(it) }
        dashboardFrame = null
    }

    fun show(popup: Popup): Shown {
        val overWindows = Settings.canDrawOverlays(context)
        if (!overWindows && dashboardFrame == null) return Shown.NOWHERE

        entries.remove(popup.id)?.let { remove(it) }
        val corner = cornerFor(popup.corner, overWindows) ?: return Shown.NOWHERE

        while (corner.container.childCount >= MAX_PER_CORNER) {
            val oldest = entries.values.firstOrNull { it.corner == popup.corner } ?: break
            entries.remove(oldest.popup.id)
            remove(oldest)
        }

        val view = buildView(popup)
        val atBottom = popup.corner == Popup.Corner.BOTTOM_START || popup.corner == Popup.Corner.BOTTOM_END
        // Newest nearest the screen edge.
        corner.container.addView(view, if (atBottom) corner.container.childCount else 0)
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(200).start()

        val dismiss = Runnable { entries[popup.id]?.let { if (it.view === view) clear(popup.id) } }
        entries[popup.id] = Entry(popup, view, popup.corner, dismiss)
        main.postDelayed(dismiss, popup.durationSeconds * 1000L)
        return if (corner.inWindow) Shown.OVER_EVERYTHING else Shown.IN_DASHBOARD
    }

    /** Removes one pop-up by id, or all of them when [id] is null. */
    fun clear(id: String?) {
        if (id == null) {
            entries.values.toList().forEach { remove(it) }
            entries.clear()
        } else {
            entries.remove(id)?.let { remove(it) }
        }
    }

    private fun remove(entry: Entry) {
        main.removeCallbacks(entry.dismiss)
        val corner = corners[entry.corner] ?: return
        corner.container.removeView(entry.view)
        if (corner.container.childCount == 0) removeCorner(entry.corner)
    }

    private fun cornerFor(which: Popup.Corner, inWindow: Boolean): Corner? {
        corners[which]?.let { existing ->
            if (existing.inWindow == inWindow) return existing
            // The permission changed since this corner was created: start it again.
            entries.values.filter { it.corner == which }.forEach { entries.remove(it.popup.id); main.removeCallbacks(it.dismiss) }
            removeCorner(which)
        }

        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val gravity = gravityFor(which)
        val margin = dp(32)
        try {
            if (inWindow) {
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    // Not FLAG_NOT_TOUCHABLE: Android 12+ forces such windows to 80 % opacity. The
                    // window is only as big as its cards, and touches outside it pass through.
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    this.gravity = gravity
                    x = margin
                    y = margin
                }
                windowManager.addView(container, params)
            } else {
                val frame = dashboardFrame ?: return null
                frame.addView(container, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity,
                ).apply { setMargins(margin, margin, margin, margin) })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Can't add the pop-up container", e)
            return null
        }
        return Corner(container, inWindow).also { corners[which] = it }
    }

    private fun removeCorner(which: Popup.Corner) {
        val corner = corners.remove(which) ?: return
        try {
            if (corner.inWindow) windowManager.removeView(corner.container)
            else (corner.container.parent as? ViewGroup)?.removeView(corner.container)
        } catch (e: Exception) {
            Log.w(TAG, "Removing the pop-up container failed", e)
        }
    }

    private fun buildView(popup: Popup): View {
        val accent = popup.color ?: DEFAULT_ACCENT
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD_COLOR)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), accent)
            }
            clipToOutline = true
            elevation = dp(8).toFloat()
        }

        if (popup.image != null) {
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }
            card.addView(image, LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), dp(IMAGE_HEIGHT_DP)))
            ImageLoader.load(popup.image, maxSidePx = 1280) { bitmap ->
                main.post {
                    if (bitmap != null && image.isAttachedToWindow) {
                        image.setImageBitmap(bitmap)
                        image.visibility = View.VISIBLE
                    }
                }
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(18), dp(14))
        }
        iconView(popup, accent)?.let {
            row.addView(it, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
        }

        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (popup.title.isNotEmpty()) texts.addView(text(popup.title, 21f, Color.WHITE, bold = true, maxLines = 2))
        if (popup.message.isNotEmpty()) texts.addView(text(popup.message, 17f, 0xFFE0E0E0.toInt(), bold = false, maxLines = 5))
        if (popup.source.isNotEmpty()) texts.addView(text(popup.source, 13f, 0xFF9E9E9E.toInt(), bold = false, maxLines = 1))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        card.addView(row, LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT))

        // Spacing between stacked cards.
        return FrameLayout(context).apply {
            setPadding(0, dp(6), 0, dp(6))
            addView(card)
        }
    }

    private fun iconView(popup: Popup, accent: Int): View? {
        val icon = popup.icon ?: return null
        if (icon.startsWith("mdi:")) {
            val emoji = MdiIcons.emojiFor(icon) ?: return null
            return TextView(context).apply {
                text = emoji
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            }
        }
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.INVISIBLE
        }
        ImageLoader.load(icon, maxSidePx = 128) { bitmap ->
            main.post {
                if (bitmap != null && image.isAttachedToWindow) {
                    image.setImageBitmap(bitmap)
                    if (popup.color != null) image.setColorFilter(accent)
                    image.visibility = View.VISIBLE
                }
            }
        }
        return image
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean, maxLines: Int) =
        TextView(context).apply {
            text = value
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            this.maxLines = maxLines
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun gravityFor(corner: Popup.Corner) = when (corner) {
        Popup.Corner.TOP_START -> Gravity.TOP or Gravity.START
        Popup.Corner.TOP_END -> Gravity.TOP or Gravity.END
        Popup.Corner.BOTTOM_START -> Gravity.BOTTOM or Gravity.START
        Popup.Corner.BOTTOM_END -> Gravity.BOTTOM or Gravity.END
    }

    private fun dp(value: Int) = Math.round(value * context.resources.displayMetrics.density)

    private companion object {
        const val TAG = "PopupManager"
        const val MAX_PER_CORNER = 3
        const val CARD_WIDTH_DP = 400
        const val IMAGE_HEIGHT_DP = 225
        val CARD_COLOR = 0xEE1E1F22.toInt()
        val DEFAULT_ACCENT = 0xFF03A9F4.toInt()
    }
}
