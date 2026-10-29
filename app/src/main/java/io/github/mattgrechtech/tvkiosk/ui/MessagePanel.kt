package io.github.mattgrechtech.tvkiosk.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * A full-screen message with optional buttons, for states the user must act on or wait through:
 * "Sign in", "Can't reach Home Assistant", "Signed in". The first button takes focus, so OK on
 * the remote does the obvious thing.
 */
class MessagePanel(context: Context) : FrameLayout(context) {

    class Action(val label: String, val primary: Boolean = false, val run: () -> Unit)

    private val icon = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
        gravity = Gravity.CENTER
    }
    private val spinner = ProgressBar(context).apply { isIndeterminate = true }
    private val title = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
    }
    private val message = TextView(context).apply {
        setTextColor(TvUi.TEXT_SECONDARY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.2f)
    }
    private val buttons = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    init {
        setBackgroundColor(0xF2101114.toInt())
        isClickable = true // swallow touches meant for the page underneath
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val gap = TvUi.dp(context, 18)
            addView(icon)
            addView(spinner, LinearLayout.LayoutParams(TvUi.dp(context, 56), TvUi.dp(context, 56)))
            addView(title, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
            addView(message, LinearLayout.LayoutParams(TvUi.dp(context, 760), LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
            addView(buttons, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap * 2 })
        }
        addView(column, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        visibility = View.GONE
    }

    /**
     * Shows the panel. [iconText] is an emoji or symbol shown above the title; with [busy] a
     * spinner is shown instead.
     */
    fun show(
        titleText: String,
        messageText: String = "",
        actions: List<Action> = emptyList(),
        iconText: String? = null,
        busy: Boolean = false,
    ) {
        icon.text = iconText ?: ""
        icon.visibility = if (iconText != null && !busy) View.VISIBLE else View.GONE
        spinner.visibility = if (busy) View.VISIBLE else View.GONE
        title.text = titleText
        message.text = messageText
        message.visibility = if (messageText.isEmpty()) View.GONE else View.VISIBLE

        buttons.removeAllViews()
        for (action in actions) {
            val button = TvUi.button(context, action.label, action.primary) { action.run() }
            buttons.addView(button, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = TvUi.dp(context, 10)
                marginEnd = TvUi.dp(context, 10)
            })
        }
        buttons.visibility = if (actions.isEmpty()) View.GONE else View.VISIBLE
        visibility = View.VISIBLE
        buttons.getChildAt(0)?.requestFocus()
    }

    fun hide() {
        visibility = View.GONE
        buttons.removeAllViews()
    }

    val isShowing get() = visibility == View.VISIBLE

    companion object {
        const val ICON_TV = "📺"
        const val ICON_WARNING = "⚠️"
        const val ICON_KEY = "🔑"
        const val ICON_DONE = "✅"
    }
}
