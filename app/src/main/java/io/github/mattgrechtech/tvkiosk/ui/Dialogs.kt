package io.github.mattgrechtech.tvkiosk.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.mattgrechtech.tvkiosk.R
import java.security.MessageDigest

/** Small D-pad friendly dialogs built on the platform AlertDialog. */
object Dialogs {

    fun list(activity: Activity, title: String, items: List<Pair<String, () -> Unit>>) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun message(activity: Activity, title: String, message: String) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun confirm(activity: Activity, title: String, message: String, onYes: () -> Unit) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> onYes() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Runs [onOk] straight away when no PIN is set, otherwise after the right PIN is entered. */
    fun requirePin(activity: Activity, pin: String, onOk: () -> Unit) {
        if (pin.isEmpty()) {
            onOk()
            return
        }
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = activity.getString(R.string.pin_hint)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.pin_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (sameSecret(input.text.toString(), pin)) onOk()
                else Toast.makeText(activity, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun qr(activity: Activity, title: String, content: String, caption: String) {
        val size = (activity.resources.displayMetrics.heightPixels * 0.5).toInt()
        val image = ImageView(activity).apply { setImageBitmap(qrBitmap(content, size)) }
        val text = TextView(activity).apply {
            this.text = caption
            setTextIsSelectable(false)
            setPadding(0, 24, 0, 0)
        }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(image)
            addView(text)
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun qrBitmap(content: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    /** Constant-time comparison, so a PIN or token can't be guessed from timing. */
    fun sameSecret(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
