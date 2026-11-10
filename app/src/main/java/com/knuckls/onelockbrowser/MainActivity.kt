package com.knuckls.onelockbrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * OneLock Browser — b3 core.
 *
 * Tabs-lite: up to [MAX_TABS] WebView-backed tabs shown as chips in a strip.
 * Every tab is a plain WebView — no incognito/private mode exists in this
 * browser, by design (this is a supervised kids' browser).
 *
 * b4 hooks navigation into enforcement (block page + scam interstitial); the
 * WebViewClient plumbing below is written so b4 only has to add the verdict
 * check inside [shouldOverrideUrlLoading].
 */
class MainActivity : AppCompatActivity() {

    private class Tab(val id: Int, val web: WebView) {
        var title: String = "New tab"
    }

    private lateinit var tabStrip: LinearLayout
    private lateinit var tabScroller: HorizontalScrollView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var contentView: LinearLayout

    private val tabs = ArrayList<Tab>()
    private var active: Tab? = null
    private var nextId = 1

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---- top: address bar -------------------------------------------------
        address = EditText(this).apply {
            setSingleLine()
            hint = "Search or enter address"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF6B93A5.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(24, 16, 24, 16)
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF062B3A.toInt())
            gravity = Gravity.CENTER_VERTICAL
            addView(address, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        // ---- progress line ----------------------------------------------------
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }

        // ---- tab strip --------------------------------------------------------
        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF04202C.toInt())
        }
        tabScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabStrip, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // ---- content stack: one WebView per tab, visibility switches ----------
        contentView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF03121A.toInt())
            addView(bar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(progress, ViewGroup.LayoutParams.MATCH_PARENT, 8)
            addView(tabScroller, ViewGroup.LayoutParams.MATCH_PARENT, dp(36))
            addView(contentView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        address.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                go(address.text.toString()); true
            } else false
        }

        // Rules snapshot for enforcement (b4). Pull now and refresh on resume;
        // a failed read keeps the last snapshot — never navigate unprotected.
        RulesStore.refresh(this)

        if (handleIntent(intent)) {
            // opened from a link; tab already created + loaded
        } else {
            newTab().loadHome()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        RulesStore.refresh(this)
    }

    /** @return true if the intent carried a URL that was opened in a tab. */
    private fun handleIntent(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        val url = data.toString()
        if (data.scheme !in setOf("http", "https")) return false
        val t = newTab()
        t.web.loadUrl(url)
        return true
    }

    // ---- tabs -----------------------------------------------------------------

    private fun newTab(): Tab {
        if (tabs.size >= MAX_TABS) closeTab(tabs.first())

        @SuppressLint("SetJavaScriptEnabled")
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = TabClient()
            webChromeClient = Chrome()
        }
        val t = Tab(nextId++, web)
        tabs.add(t)
        contentView.addView(web, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        select(t)
        return t
    }

    private fun select(t: Tab) {
        active = t
        tabs.forEach { it.web.visibility = if (it === t) View.VISIBLE else View.GONE }
        address.setText(when {
            t.web.url != null -> t.web.url
            t.web.originalUrl != null -> t.web.originalUrl
            else -> ""
        })
        renderTabs()
    }

    private fun closeTab(t: Tab) {
        tabs.remove(t)
        contentView.removeView(t.web)
        t.web.destroy()
        if (active === t) {
            if (tabs.isNotEmpty()) select(tabs.last()) else newTab().loadHome()
        } else renderTabs()
    }

    private fun renderTabs() {
        tabStrip.removeAllViews()
        tabs.forEach { t ->
            val isActive = t === active
            val chip = TextView(this).apply {
                text = (if (isActive) "✕  " else "") + (t.title.ifBlank { "New tab" })
                maxLines = 1
                setPadding(dp(12), 0, dp(12), 0)
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (isActive) Color.WHITE else 0xFF8FB3C2.toInt())
                setBackgroundColor(if (isActive) 0xFF0EA5E9.toInt() else Color.TRANSPARENT)
                // closing tab: one tap on the active chip's ✕; switching otherwise.
                setOnClickListener {
                    if (isActive && tabs.size > 1) closeTab(t) else select(t)
                }
            }
            tabStrip.addView(chip, ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { marginEnd = 2 })
        }
        tabScroller.post { tabScroller.fullScroll(View.FOCUS_RIGHT) }
        // "+" to open a new tab, always at the end of the strip.
        val plus = TextView(this).apply {
            text = "+"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { newTab().loadHome() }
        }
        tabStrip.addView(plus, ViewGroup.MarginLayoutParams(dp(40), dp(32)))
    }

    // ---- navigation ------------------------------------------------------------

    private fun go(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        val url = when {
            q.startsWith("http://") || q.startsWith("https://") -> q
            q.contains(".") && !q.contains(" ") -> "https://$q"
            else -> "https://duckduckgo.com/?q=" +
                    java.net.URLEncoder.encode(q, "UTF-8") + "&kp=-2"
        }
        active?.web?.loadUrl(url)
    }

    private fun Tab.loadHome() {
        title = "New tab"
        web.loadUrl(HOME_URL)
    }

    // ---- WebView plumbing --------------------------------------------------------

    private inner class TabClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // b4: verdict check (blocklist + scam heuristics + schedules) here.
            return enforce(view, request.url.toString())
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            if (view === active?.web) address.setText(url)
            // Catch in-page JS navigation that skips shouldOverrideUrlLoading.
            if (url != null) enforce(view, url, silentWhenBlocked = true)
        }

        /**
         * Runs the verdict and, on a block, shows the block screen and stops the
         * load. Returns true when the load was stopped (WebViewClient contract).
         */
        private fun enforce(view: WebView, url: String, silentWhenBlocked: Boolean = false): Boolean {
            val verdict = Enforcement.check(url, RulesStore.snapshot())
            if (verdict is Enforcement.Verdict.Allow) return false
            val reason = when (verdict) {
                is Enforcement.Verdict.Block -> verdict.reason
                is Enforcement.Verdict.Warn -> "Possible scam — this address looks fake"
                else -> "Blocked"
            }
            if (silentWhenBlocked) {
                // Already-loading page hit mid-navigation; stop it visually too.
                view.stopLoading()
                address.setText(view.url ?: url)
            }
            val i = android.content.Intent(this@MainActivity, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_URL, url)
                .putExtra(BlockActivity.EXTRA_REASON, reason)
            startActivityForResult(i, REQ_UNLOCK)
            return true
        }

        override fun shouldInterceptRequest(
            view: WebView, request: WebResourceRequest
        ): android.webkit.WebResourceResponse? {
            // Ads: cheap offline drop. Return an empty 200 rather than failing,
            // so pages render without endless stalled subresource requests.
            if (Enforcement.isAd(request.url.toString())) {
                return android.webkit.WebResourceResponse(
                    "text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun onPageFinished(view: WebView, url: String?) {
            view.title?.let { t ->
                tabs.firstOrNull { it.web === view }?.title = t.take(24)
                renderTabs()
            }
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            progress.progress = newProgress
        }
    }

    // ---- back / app bar ------------------------------------------------------------

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Parent PIN unlocked this site once — load it.
        if (requestCode == REQ_UNLOCK && resultCode == RESULT_OK) {
            data?.getStringExtra(BlockActivity.EXTRA_URL)?.let { active?.web?.loadUrl(it) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val t = active ?: return super.onBackPressed()
        if (t.web.canGoBack()) t.web.goBack() else super.onBackPressed()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_TABS = 5
        private const val HOME_URL = "https://duckduckgo.com/?kp=-2"
        private const val REQ_UNLOCK = 4001
    }
}
