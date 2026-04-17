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
            mainActivity.runOnUiThread { mainActivity.triggerInstantSwap() }
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
        Toast.makeText(this, "Optimizing UI for Perfect Offline Capture...", Toast.LENGTH_SHORT).show()
        
        /**
         * "Perfect Copy" Protocol:
         * Instead of a flaky MHT, we serialize the entire DOM and INLINE all computed styles.
         * This ensures the "skin" (CSS) is baked into the "bone" (HTML).
         */
        val perfectSkinScript = """
            (function() {
                try {
                    // Inline all stylesheets into the head
                    let styles = '';
                    for (let i = 0; i < document.styleSheets.length; i++) {
                        const sheet = document.styleSheets[i];
                        try {
                            const rules = sheet.cssRules || sheet.rules;
                            for (let j = 0; j < rules.length; j++) {
                                styles += rules[j].cssText + '\n';
                            }
                        } catch (e) { /* Cross-origin CSS ignored */ }
                    }
                    const styleTag = document.createElement('style');
                    styleTag.innerHTML = styles;
                    
                    // Clone the document to sanitize
                    const clone = document.documentElement.cloneNode(true);
                    const cloneHead = clone.querySelector('head');
                    
                    // Remove external links/scripts to prevent broken relative loads
                    cloneHead.querySelectorAll('link[rel="stylesheet"], script[src]').forEach(el => el.remove());
                    cloneHead.appendChild(styleTag);
                    
                    // Force mobile viewport meta
                    let vMeta = cloneHead.querySelector('meta[name="viewport"]');
                    if (!vMeta) {
                        vMeta = document.createElement('meta');
                        vMeta.name = 'viewport';
                        cloneHead.appendChild(vMeta);
                    }
                    vMeta.content = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';
                    
                    return '<!DOCTYPE html>\n' + clone.outerHTML;
                } catch (e) { return 'ERROR: ' + e.message; }
            })();
        """.trimIndent()
        
        webViewLive.evaluateJavascript(perfectSkinScript) { html ->
            if (html != null && !html.startsWith("\"ERROR") && html != "null") {
                // Decode the JSON string returned by evaluateJavascript
                val sanitizedHtml = try {
                    android.util.JsonReader(java.io.StringReader(html)).use { reader ->
                        reader.nextString()
                    }
                } catch (e: Exception) { html }

                archiveFile = File(filesDir, "gemini_perfect_v1.html")
                archiveFile.writeText(sanitizedHtml)
                
                isCacheSeeded = true
                getSharedPreferences("gemini_offline_prefs", MODE_PRIVATE).edit().putBoolean("cache_seeded", true).apply()
                verifyButton?.visibility = View.GONE
                Toast.makeText(this@MainActivity, "SUCCESS: Perfect UI Capture Complete.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Capture Failed. Retrying standard mode...", Toast.LENGTH_SHORT).show()
                // Fallback to MHT if serialization fails (though HTML is preferred)
                webViewLive.saveWebArchive(File(filesDir, "gemini_static_v1.mht").absolutePath, false) { _ -> }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup the view hierarchy first
        val rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        // 1. COMPACT MODE: Restore immersive view
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, rootLayout)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 2. ULTRA-ROBUST KEYBOARD HANDLING: WindowInsets Listener
        // We use adjustNothing in the manifest and manually shift the view using Insets
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            
            // Adjust margins based on exact IME height
            val margin = if (imeVisible) imeHeight else 0
            
            val paramsLive = webViewLive.layoutParams as FrameLayout.LayoutParams
            if (paramsLive.bottomMargin != margin) {
                paramsLive.bottomMargin = margin
                webViewLive.layoutParams = paramsLive
            }
            webViewOffline?.let { wv ->
                val paramsOff = wv.layoutParams as FrameLayout.LayoutParams
                if (paramsOff.bottomMargin != margin) {
                    paramsOff.bottomMargin = margin
                    wv.layoutParams = paramsOff
                }
            }
            insets
        }

        archiveFile = File(filesDir, "gemini_perfect_v1.html")
        val altArchive = File(filesDir, "gemini_static_v1.mht")
        val prefs = getSharedPreferences("gemini_offline_prefs", MODE_PRIVATE)
        isCacheSeeded = prefs.getBoolean("cache_seeded", false)

        // 1. CREATE LIVE WEBVIEW
        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            alpha = if (archiveFile.exists() || altArchive.exists()) 0.0f else 1.0f 
        }
        rootLayout.addView(webViewLive)
        setupWebView(webViewLive)

        // 2. CREATE OFFLINE WEBVIEW
        if (archiveFile.exists() || altArchive.exists()) {
            webViewOffline = WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            rootLayout.addView(webViewOffline)
            setupWebView(webViewOffline!!)
            webViewOffline?.settings?.cacheMode = WebSettings.LOAD_CACHE_ONLY
            
            if (archiveFile.exists()) {
                webViewOffline?.loadDataWithBaseURL("https://gemini.google.com", archiveFile.readText(), "text/html", "UTF-8", null)
            } else {
                webViewOffline?.loadUrl("file://" + altArchive.absolutePath)
            }
        }
        
        // 3. Add Freeze Button
        if (!isCacheSeeded && !archiveFile.exists() && !altArchive.exists()) {
            verifyButton = Button(this).apply {
                text = "FREEZE UI FOR OFFLINE"
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    setMargins(0, 0, 0, 100)
                }
                setOnClickListener { triggerPhysicalDownload() }
            }
            rootLayout.addView(verifyButton)
        }

        // Apply Immersive Mode to the DecorView (More stable for full-screen)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
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
                if (request?.isForMainFrame == true && view == webViewLive) {
                    // If live fails, allow it to try again or stay on the offline view
                    view.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
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
