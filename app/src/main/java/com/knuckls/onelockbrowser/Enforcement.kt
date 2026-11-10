package com.knuckls.onelockbrowser

import android.net.Uri
import java.util.Calendar

/**
 * Enforcement engine for every navigation (b4).
 *
 * Two layers, both offline:
 *  1. Rules — the OneLock parent's snapshot (RulesStore): category flags +
 *     custom blocked sites + per-site unblocks, and the bedtime/school windows.
 *  2. Scam heuristics — the same lookalike/bait/punycode checks the backend's
 *     `url_check` performs, ported client-side so the browser never has to wait
 *     on (or authenticate to) the network to judge a page. The backend check
 *     stays as the reference implementation; these must stay in sync.
 *
 * Verdicts are synchronous — [check] is called inside
 * shouldOverrideUrlLoading, which must return an answer immediately.
 */
object Enforcement {

    sealed class Verdict {
        object Allow : Verdict()
        data class Block(val reason: String) : Verdict()
        /** Local heuristic fired but not certain — page still loads. b5 may gate. */
        data class Warn(val reason: String) : Verdict()
    }

    fun check(url: String, snap: RulesStore.Snapshot): Verdict {
        val h = hostOf(url) ?: return Verdict.Allow
        if (h.isEmpty()) return Verdict.Allow

        // ---- schedules first: bedtime/school shut everything except the
        //      per-site unblocks (a parent may keep homework sites open).
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        for (w in listOf("bedtime" to snap.bedtime, "school" to snap.school)) {
            val win = w.second ?: continue
            if (!win.on || !inWindow(minutes, win)) continue
            val label = if (w.first == "bedtime") "Bedtime" else "School time"
            if (snap.unblocked.none { matches(h, it) }) {
                return Verdict.Block("$label is on — this device is locked right now")
            }
        }

        // ---- per-site unblocks beat categories and custom blocks
        if (snap.unblocked.any { matches(h, it) }) return Verdict.Allow

        // ---- parent's custom blocked list
        if (snap.custom.any { matches(h, it) }) {
            return Verdict.Block("Blocked by your parent")
        }

        // ---- category lists, per the snapshot flags
        fun catOn(key: String) = snap.cats[key] == true
        if (catOn("social") && Lists.SOCIAL.any { matches(h, it) }) return Verdict.Block("Social media is blocked")
        if (catOn("video") && Lists.VIDEO.any { matches(h, it) }) return Verdict.Block("Video sites are blocked")
        if (catOn("gambling") && Lists.GAMBLING.any { matches(h, it) }) return Verdict.Block("Gambling is blocked")
        if (catOn("dating") && Lists.DATING.any { matches(h, it) }) return Verdict.Block("Dating sites are blocked")
        // ads never get a block page — just checked at resource level by callers
        // that want it; navigations to pure ad hosts are rare and harmless.

        // ---- scam heuristics (mirror of backend url_check)
        scam(url)?.let { return it }

        return Verdict.Allow
    }

    /** True when this URL's host is on the ad list — used to skip ad resources. */
    fun isAd(url: String): Boolean {
        val h = hostOf(url) ?: return false
        return Lists.ADS.any { matches(h, it) }
    }

    /**
     * Local port of the backend's url_check scam logic:
     *  1. brand lookalikes (paypa1-verify, amaz0n-giveaway…)
     *  2. prize/verify bait words in the domain
     *  3. punycode (xn--) lookalikes
     */
    private fun scam(url: String): Verdict? {
        val host = hostOf(url) ?: return null
        val bare = host.removePrefix("www.")

        // 1) lookalike brand squats
        val brands = listOf(
            "paypal", "amazon", "microsoft", "facebook", "instagram", "netflix",
            "apple", "google", "chase", "wellsfargo", "coinbase", "cashapp",
            "venmo", "walmart", "ebay",
        )
        val swapped = bare
            .replace("0", "o").replace("1", "l").replace("3", "e")
            .replace("4", "a").replace("5", "s").replace("7", "t")
            .replace("@", "a").replace("vv", "w")
        if (swapped != bare) {
            for (brand in brands) {
                if (swapped.contains(brand) && !bare.contains(brand)) {
                    return Verdict.Warn("This address is pretending to be $brand")
                }
            }
        }

        // 2) bait words in the domain
        if (Regex("(prize|winner|claimnow|freegift|giftcard|rewardcenter|verify-account|account-verify|secure-login|login-secure)")
                .containsMatchIn(bare)
        ) {
            return Verdict.Warn("Prize/verify bait in the address")
        }

        // 3) punycode lookalikes (except .рф which legitimately uses xn--p1ai)
        if (host.contains("xn--") && !host.endsWith("xn--p1ai")) {
            return Verdict.Warn("Lookalike (punycode) address")
        }
        return null
    }

    fun hostOf(url: String): String? = try {
        Uri.parse(url).host?.lowercase()
    } catch (_: Exception) {
        null
    }

    /** Suffix match: "site.com" blocks "www.site.com" and "m.site.com". */
    private fun matches(host: String, site: String): Boolean {
        val s = site.trim().lowercase()
            .removePrefix("http://").removePrefix("https://")
            .substringBefore("/").removeSuffix(".")
        if (s.isEmpty()) return false
        return host == s || host.endsWith(".$s")
    }

    /** Window [startMin, endMin) crossing midnight. */
    private fun inWindow(minutes: Int, w: RulesStore.Window): Boolean =
        if (w.startMin <= w.endMin) minutes in w.startMin until w.endMin
        else minutes >= w.startMin || minutes < w.endMin
}
