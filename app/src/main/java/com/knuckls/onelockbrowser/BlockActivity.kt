package com.knuckls.onelockbrowser

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen block page. Shown instead of the requested site when the
 * enforcement engine (or a scam heuristic) says no. No override here on
 * purpose — b5 adds the parent-PIN path.
 */
class BlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Blocked"

        val title = TextView(this).apply {
            text = "🛡️"
            textSize = 48f
            gravity = Gravity.CENTER
        }
        val heading = TextView(this).apply {
            text = reason
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(48, 24, 48, 8)
        }
        val site = TextView(this).apply {
            text = url
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF8FB3C2.toInt())
            setPadding(48, 0, 48, 32)
        }
        val body = TextView(this).apply {
            text = "This site is blocked by OneLock. If you think this is a\n" +
                    "mistake, ask your parent to unblock it in the OneLock app."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFC9DEE8.toInt())
            setPadding(48, 0, 48, 40)
        }
        val back = Button(this).apply {
            text = "Go back"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        // Parent PIN gate: the parent's PIN (hash+salt delivered through the
        // signature-protected provider) unlocks THIS one site, once.
        val snap = RulesStore.snapshot()
        val unlock = Button(this).apply {
            visibility = if (snap.pinSet) View.VISIBLE else View.GONE
            text = "Parent unlock (PIN)"
            isAllCaps = false
            setOnClickListener { askPin(snap, url) }
        }
        val pinRow = TextView(this).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
            setTextColor(0xFFC9DEE8.toInt())
            textSize = 15f
            setPadding(48, 16, 48, 8)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF03121A.toInt())
            addView(title)
            addView(heading)
            addView(site)
            addView(body)
            addView(back, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(unlock, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(pinRow)
        }
        setContentView(root)
    }

    private fun askPin(snap: RulesStore.Snapshot, url: String) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Parent PIN"
            gravity = Gravity.CENTER
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Parent unlock")
            .setMessage("Enter the OneLock parent PIN to open this site once.")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val pin = input.text.toString()
                if (pin.isNotEmpty() && snap.pinSalt.isNotEmpty() &&
                    sha256Hex(snap.pinSalt + pin) == snap.pinHash
                ) {
                    setResult(RESULT_OK, intent.putExtra(BlockActivity.EXTRA_URL, url))
                    finish()
                } else {
                    android.widget.Toast.makeText(
                        this, "Wrong PIN", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sha256Hex(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_REASON = "reason"
    }
}
