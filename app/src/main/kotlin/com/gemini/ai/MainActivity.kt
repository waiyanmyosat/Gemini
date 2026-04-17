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

import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebResourceResponseCompat
import java.io.FileInputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var syncButton: Button? = null 
    private lateinit var archiveFile: File
    private var isLoginMode: Boolean = false
    private var isCacheSeeded: Boolean = false
    private var pendingPrompt: String? = null

    private lateinit var assetLoader: WebViewAssetLoader

    class WebAppInterface(private val mainActivity: MainActivity) {
        @JavascriptInterface
        fun onSendPrompt(text: String) {
            mainActivity.runOnUiThread { mainActivity.handleOfflinePrompt(text) }
        }
    }

    /**
     * When we are in the "Frozen Shell", intercept the message 
     * and send it via the hidden live layer.
     */
    fun handleOfflinePrompt(text: String) {
        if (!isLoginMode) {
            pendingPrompt = text
            // Temporarily disable the "Frozen" intercept for the prompt reload
            isLoginMode = true 
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
            webView.loadUrl("https://gemini.google.com/app")
        }
    }

    private fun triggerPhysicalDownload() {
        Toast.makeText(this, "Freezing current UI into the Hybrid Shell...", Toast.LENGTH_SHORT).show()
        webView.saveWebArchive(archiveFile.absolutePath, false) { path ->
            if (path != null) {
                isCacheSeeded = true
                getSharedPreferences("gemini_offline_prefs", MODE_PRIVATE).edit().putBoolean("cache_seeded", true).apply()
                isLoginMode = false // Switch to Hybrid Mode immediately
                Toast.makeText(this@MainActivity, "HYBRID SHELL READY: Logic is now dynamic.", Toast.LENGTH_LONG).show()
                webView.loadUrl("https://gemini.google.com/app") // This will now be intercepted
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val rootLayout = FrameLayout(this)
        archiveFile = File(filesDir, "gemini_static_v1.mht")
        val prefs = getSharedPreferences("gemini_offline_prefs", MODE_PRIVATE)
        isCacheSeeded = prefs.getBoolean("cache_seeded", false)
        
        // Use Live mode initially if no archive exists
        isLoginMode = !archiveFile.exists()

        // 1. CONFIGURE ASSET LOADER
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("gemini.google.com") // Fake domain matching for CORS
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        rootLayout.addView(webView)

        // 2. ADD "LIVE SYNC" TOGGLE (FAB style)
        syncButton = Button(this).apply {
            text = if (isLoginMode) "LOCK FROZEN SHELL" else "SYNC LIVE / LOGIN"
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 50)
            }
            alpha = 0.6f
            setOnClickListener { 
                toggleLoginMode()
            }
        }
        rootLayout.addView(syncButton)
        
        setContentView(rootLayout)

        setupWebView()
        
        if (savedInstanceState == null) {
            webView.loadUrl("https://gemini.google.com/app")
        }
    }

    private fun toggleLoginMode() {
        isLoginMode = !isLoginMode
        if (isLoginMode) {
            syncButton?.text = "LOCK FROZEN SHELL"
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
            webView.loadUrl("https://gemini.google.com/app")
            Toast.makeText(this, "ENTERING LIVE SYNC MODE (Update Login/Prompts)", Toast.LENGTH_SHORT).show()
        } else {
            syncButton?.text = "SYNC LIVE / LOGIN"
            // If the user wants to "lock" it, and we are live, let's offer to freeze it
            if (webView.url?.contains("gemini.google.com/app") == true) {
                triggerPhysicalDownload()
            } else {
                webView.loadUrl("https://gemini.google.com/app")
            }
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        }
        
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
        
        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                
                // CORE INTERCEPT: If not in login mode, serve the static Dashboard Shell
                if (!isLoginMode && (url.contains("gemini.google.com/app") || url == "https://gemini.google.com/")) {
                    if (archiveFile.exists()) {
                        try {
                            val fis = FileInputStream(archiveFile)
                            return WebResourceResponse("multipart/related", "UTF-8", fis)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
                
                // Handle static assets (CSS/JS) via WebViewAssetLoader if needed
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                
                // Inject prompt bridge into the Hybrid Shell
                val script = """
                    (function() {
                        const textarea = document.querySelector('textarea, div[contenteditable="true"]');
                        const sendBtn = document.querySelector('button[aria-label*="Send"], svg.send-icon');
                        
                        if (sendBtn) {
                            sendBtn.addEventListener('click', function(e) {
                                if (window.location.protocol === 'file:' || document.title.includes('mht')) {
                                     Android.onSendPrompt(textarea.value || textarea.innerText);
                                }
                            }, true);
                        }
                    })();
                """.trimIndent()
                view?.evaluateJavascript(script, null)

                // Handle re-injection if we just transitioned from offline
                pendingPrompt?.let { prompt ->
                    isLoginMode = false // Return to intercept mode after injection
                    val escapedText = prompt.replace("'", "\\'").replace("\n", "\\n")
                    val injection = """
                        (function() {
                            const textarea = document.querySelector('textarea, div[contenteditable="true"]');
                            if (textarea) {
                                textarea.value = '$escapedText';
                                textarea.innerText = '$escapedText';
                                textarea.dispatchEvent(new Event('input', { bubbles: true }));
                                setTimeout(() => {
                                    const btn = document.querySelector('button[aria-label*="Send"], button[type="submit"]');
                                    if (btn) btn.click();
                                }, 600);
                            }
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(injection, null)
                    pendingPrompt = null
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }
}
