package com.gemini.ai

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webViewLive: WebView
    private var webViewOffline: WebView? = null
    private var verifyButton: Button? = null 
    private lateinit var archiveFile: File
    private var isCacheSeeded: Boolean = false
    private var pendingPrompt: String? = null
    private var isReadyToSwap = false

    class WebAppInterface(private val mainActivity: MainActivity) {
        @JavascriptInterface
        fun onSendPrompt(text: String) {
            mainActivity.runOnUiThread { mainActivity.enableNetworkAndReload(text) }
        }

        @JavascriptInterface
        fun onDashboardReady() {
            // Automatic swap disabled to respect "Offline First" preference.
            // Only swap when a prompt is sent or explicitly requested.
        }
    }

    fun enableNetworkAndReload(text: String) {
        pendingPrompt = text
        webViewLive.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webViewLive.loadUrl("https://gemini.google.com/app")
        // If we were offline, ensure the live view becomes visible if it wasn't yet
        webViewLive.alpha = 1.0f
        webViewOffline?.visibility = View.GONE
    }

    /**
     * Fades out the offline archive and reveals the live dashboard
     * without any visible reload flicker.
     */
    fun triggerInstantSwap() {
        if (isReadyToSwap || webViewOffline == null) return
        isReadyToSwap = true
        
        webViewLive.animate().alpha(1.0f).setDuration(500).start()
        webViewOffline?.animate()?.alpha(0.0f)?.setDuration(500)?.withEndAction {
            webViewOffline?.visibility = View.GONE
        }?.start()
    }

    private fun triggerPhysicalDownload() {
        Toast.makeText(this, "Capturing every UI piece (HTML+CSS+JS)...", Toast.LENGTH_SHORT).show()
        
        // Before capturing, ensure we force a layout pass for dynamic elements
        val prepareScript = "window.dispatchEvent(new Event('resize'));"
        
        webViewLive.evaluateJavascript(prepareScript) {
            webViewLive.saveWebArchive(archiveFile.absolutePath, false) { path ->
                if (path != null) {
                    isCacheSeeded = true
                    getSharedPreferences("gemini_offline_prefs", MODE_PRIVATE).edit().putBoolean("cache_seeded", true).apply()
                    verifyButton?.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "OFFLINE SUCCESS: Dash Frozen Locally.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val rootLayout = FrameLayout(this)
        
        // 1. COMPACT MODE: Hide system bars for an immersive feel
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, rootLayout)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        archiveFile = File(filesDir, "gemini_static_v1.mht")
        val prefs = getSharedPreferences("gemini_offline_prefs", MODE_PRIVATE)
        isCacheSeeded = prefs.getBoolean("cache_seeded", false)

        // 1. CREATE LIVE WEBVIEW (Background)
        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            alpha = if (archiveFile.exists()) 0.0f else 1.0f 
        }
        rootLayout.addView(webViewLive)

        // 2. CREATE OFFLINE WEBVIEW (Foreground Instant)
        if (archiveFile.exists()) {
            webViewOffline = WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            rootLayout.addView(webViewOffline)
            setupWebView(webViewOffline!!)
            webViewOffline?.settings?.cacheMode = WebSettings.LOAD_CACHE_ONLY
            webViewOffline?.loadUrl("file://" + archiveFile.absolutePath)
        }
        
        // 3. Add Freeze Button
        if (!isCacheSeeded && !archiveFile.exists()) {
            verifyButton = Button(this).apply {
                text = "FREEZE UI (OFFLINE MODE)"
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    setMargins(0, 0, 0, 100)
                }
                setOnClickListener { triggerPhysicalDownload() }
            }
            rootLayout.addView(verifyButton)
        }
        
        setContentView(rootLayout)

        setupWebView(webViewLive)
        
        if (savedInstanceState == null) {
            webViewLive.loadUrl("https://gemini.google.com/app")
        }
    }

    private fun setupWebView(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        }
        
        wv.addJavascriptInterface(WebAppInterface(this), "Android")
        wv.webChromeClient = WebChromeClient()
        
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()

                // Protocol: Only the LIVE view signals it's ready to replace the offline view
                if (view == webViewLive && url?.contains("gemini.google.com") == true) {
                    val swapCheck = """
                        (function() {
                            const isDashboard = !!document.querySelector('textarea, div[contenteditable="true"]');
                            if (isDashboard) {
                                Android.onDashboardReady();
                            }
                        })();
                    """.trimIndent()
                    view.evaluateJavascript(swapCheck, null)
                }

                if (view == webViewLive) handlePrompts(view)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    if (view == webViewLive) {
                        // Background live failed? Stealthily keep it updated with cache
                        view.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    } else if (view == webViewOffline) {
                        // Offline file critical error (rare) -> Fallback to live immediately
                        mainActivity.runOnUiThread {
                            webViewLive.alpha = 1.0f
                            webViewOffline?.visibility = View.GONE
                        }
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = request.url.host ?: ""

                val isInternal = host.contains("google.") || 
                                 host.contains("gemini.google") || 
                                 host.contains("gstatic.com") ||
                                 url.contains("SetSID") ||
                                 url.contains("signin") ||
                                 url.contains("auth")

                if (isInternal) return false 
                
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return true
                } catch (e: Exception) { return false }
            }
        }
    }

    private fun handlePrompts(view: WebView?) {
        pendingPrompt?.let { prompt ->
            val escapedText = prompt.replace("'", "\\'").replace("\n", "\\n")
            val script = """
                (function() {
                    const textarea = document.querySelector('textarea, div[contenteditable="true"], .ql-editor');
                    const btn = document.querySelector('button[aria-label*="Send"], button[type="submit"]');
                    if (textarea) {
                        textarea.value = '$escapedText';
                        textarea.innerText = '$escapedText';
                        ['input', 'change'].forEach(e => textarea.dispatchEvent(new Event(e, { bubbles: true })));
                        setTimeout(() => { if (btn) btn.click(); }, 500);
                    }
                })();
            """.trimIndent()
            view?.evaluateJavascript(script, null)
            pendingPrompt = null
        }
        
        val detectionScript = """
            (function() {
                const sendSelectors = ['button[aria-label*="Send"]', 'svg.send-icon', '.send-button-container'];
                document.addEventListener('click', function(e) {
                    const btn = e.target.closest(sendSelectors.join(','));
                    if (btn) {
                        const textarea = document.querySelector('textarea, div[contenteditable="true"], .ql-editor');
                        if (textarea) Android.onSendPrompt(textarea.value || textarea.innerText);
                    }
                }, true);
            })();
        """.trimIndent()
        view?.evaluateJavascript(detectionScript, null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webViewLive.canGoBack()) {
            webViewLive.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webViewLive.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webViewLive.restoreState(savedInstanceState)
    }
}
