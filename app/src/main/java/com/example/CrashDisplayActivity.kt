package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Completely dependency-free fallback Activity for startup and uncaught runtime crashes.
 * Uses pure standard Android Views (No Compose, no Room, no DI, no custom themes, no Coroutines).
 * Cannot crash on missing themes or compose initialization failure.
 */
class CrashDisplayActivity : Activity() {

    companion object {
        const val EXTRA_ERROR_CLASS = "extra_error_class"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"

        fun start(context: Context, throwable: Throwable) {
            if (!BuildConfig.ENABLE_CRASH_DIAGNOSTICS_UI) return
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                val intent = Intent(context, CrashDisplayActivity::class.java).apply {
                    putExtra(EXTRA_ERROR_CLASS, throwable.javaClass.name)
                    putExtra(EXTRA_ERROR_MESSAGE, throwable.message ?: "(No message provided)")
                    putExtra(EXTRA_STACK_TRACE, stackTrace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(intent)
            } catch (_: Throwable) {
                // Last ditch: print to stderr
                throwable.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BuildConfig.ENABLE_CRASH_DIAGNOSTICS_UI) {
            finish()
            return
        }

        // Pure black background
        window.decorView.setBackgroundColor(Color.BLACK)

        val errClass = intent.getStringExtra(EXTRA_ERROR_CLASS) ?: "UnknownException"
        val errMsg = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "No error message"
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace available"

        val fullReport = buildString {
            append("=== VAULTKEEP STARTUP / RUNTIME DIAGNOSTIC ===\n")
            append("Exception: ").append(errClass).append("\n")
            append("Message: ").append(errMsg).append("\n\n")
            append("--- STACK TRACE ---\n")
            append(stackTrace)
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            val pad = dpToPx(16)
            setPadding(pad, pad, pad, pad)
        }

        // Header
        val headerView = TextView(this).apply {
            text = "APPLICATION DIAGNOSTIC LOG"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            val bottomPad = dpToPx(8)
            setPadding(0, dpToPx(8), 0, bottomPad)
        }
        rootLayout.addView(headerView)

        // Subheader
        val subheaderView = TextView(this).apply {
            text = "An uncaught startup or runtime error occurred. Detailed diagnostic below:"
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val bottomPad = dpToPx(12)
            setPadding(0, 0, 0, bottomPad)
        }
        rootLayout.addView(subheaderView)

        // Action Buttons Row (Copy & Restart)
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val bottomPad = dpToPx(12)
            setPadding(0, 0, 0, bottomPad)
        }

        val copyButton = Button(this).apply {
            text = "Copy Diagnostic Log"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("Crash Diagnostic", fullReport)
                cm?.setPrimaryClip(clip)
                Toast.makeText(this@CrashDisplayActivity, "Diagnostic copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        buttonRow.addView(copyButton)

        rootLayout.addView(buttonRow)

        // Scrollable Log Box
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.argb(255, 20, 20, 20))
            val pad = dpToPx(12)
            setPadding(pad, pad, pad, pad)
        }

        val logTextView = TextView(this).apply {
            text = fullReport
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(logTextView)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
