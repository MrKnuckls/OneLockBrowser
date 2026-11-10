package com.knuckls.onelockbrowser

import android.content.Context
import android.database.Cursor
import android.net.Uri
import org.json.JSONObject

/**
 * The enforcement rules snapshot OneLock (parent app) publishes to us.
 *
 * Source of truth: the parent app's signature-protected ContentProvider
 * (content://com.knuckls.onelock.rules/rules) — only OneLock-signed apps can
 * read it, so no network auth is needed and the snapshot is available offline.
 *
 * b4 consumes [snapshot] on every navigation; b5 consumes [bedtime]/[school].
 */
object RulesStore {

    const val AUTHORITY = "com.knuckls.onelock.rules"
    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/rules")

    data class Snapshot(
        val rev: Int = 0,
        val cats: Map<String, Boolean> = emptyMap(),
        val custom: List<String> = emptyList(),
        val unblocked: List<String> = emptyList(),
        val pinSet: Boolean = false,
        val pinSalt: String = "",
        val pinHash: String = "",
        val bedtime: Window? = null,
        val school: Window? = null,
    )

    data class Window(val on: Boolean, val startMin: Int, val endMin: Int)

    @Volatile private var cached: Snapshot = Snapshot()

    /** Last known snapshot. Safe to call from any thread. */
    fun snapshot(): Snapshot = cached

    /**
     * Pull a fresh snapshot from OneLock's provider. Returns true when new rules
     * were loaded, false when OneLock isn't installed / gave us nothing (in that
     * case the last snapshot stays — never navigate un-protected because of a
     * failed read).
     */
    fun refresh(ctx: Context): Boolean {
        val raw = readProvider(ctx) ?: return false
        return try {
            cached = parse(raw)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readProvider(ctx: Context): String? = try {
        val c: Cursor? = ctx.contentResolver.query(CONTENT_URI, null, null, null, null)
        c?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex("rules")
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
    } catch (_: Exception) {
        null // parent not installed, permission revoked, or provider down
    }

    fun parse(json: String): Snapshot {
        val o = JSONObject(json)
        val cats = HashMap<String, Boolean>()
        val co = o.optJSONObject("cats") ?: JSONObject()
        co.keys().forEach { cats[it] = co.optBoolean(it, false) }
        return Snapshot(
            rev = o.optInt("rev", 0),
            cats = cats,
            custom = jsonList(o, "custom"),
            unblocked = jsonList(o, "unblocked"),
            pinSet = o.optBoolean("pin_set", false),
            pinSalt = o.optString("pin_salt", ""),
            pinHash = o.optString("pin_hash", ""),
            bedtime = window(o.optJSONObject("bedtime")),
            school = window(o.optJSONObject("school")),
        )
    }

    private fun window(w: JSONObject?): Window? = w?.let {
        Window(
            on = it.optBoolean("on", false),
            startMin = it.optInt("start", 0),
            endMin = it.optInt("end", 0),
        )
    }

    private fun jsonList(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
    }
}
