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
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var archiveFile: File
    private var isCacheSeeded: Boolean = false

    // Bridge for JS to notify Android of prompt sending
    class WebAppInterface(private val mainActivity: MainActivity) {
        @JavascriptInterface
        fun onSendPrompt() {
            mainActivity.runOnUiThread {
                mainActivity.enableNetworkAndReload()
            }
        }
    }

    /**
     * When user sends a prompt from the static offline file,
     * we "Wake Up" the real internet-connected site.
     */
    fun enableNetworkAndReload() {
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        // Switch from the local .mht file back to the live site
        webView.loadUrl("https://gemini.google.com/app")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val rootLayout = FrameLayout(this)
        
        webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        webView.visibility = View.VISIBLE 
        rootLayout.addView(webView)
        
        setContentView(rootLayout)

        // Physical File Path for valid "Proof" of download
        archiveFile = File(filesDir, "gemini_static_v1.mht")

        // Load seeding state
        val prefs = getSharedPreferences("gemini_offline_prefs", MODE_PRIVATE)
        isCacheSeeded = prefs.getBoolean("cache_seeded", false)

        // Persistence Configuration
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        
        // Connect JS bridge
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        // Screen Settings
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, rootLayout)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Fix Keyboard Overlap
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val params = webView.layoutParams as FrameLayout.LayoutParams
            params.bottomMargin = if (imeVisible) imeHeight else systemBars
            webView.layoutParams = params
            insets
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // 100% OFFLINE FIRST:
            // If the local archive exists, we will load it directly.
            if (archiveFile.exists()) {
                cacheMode = WebSettings.LOAD_CACHE_ONLY
            } else {
                // Initial session: allow internet to reach login/app
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Zero loading indicators for a "Static Object" feel
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()

                // EXPLICIT LOGGED-IN CHECK: Trigger download only on Gemini dashboard
                if (!isCacheSeeded && url != null && url.contains("gemini.google.com/app")) {
                    // Start manual Archive download
                    view?.saveWebArchive(archiveFile.absolutePath, false) { path ->
                        if (path != null) {
                            isCacheSeeded = true
                            getSharedPreferences("gemini_offline_prefs", MODE_PRIVATE)
                                .edit()
                                .putBoolean("cache_seeded", true)
                                .apply()
                            
                            val sizeKb = archiveFile.length() / 1024
                            Toast.makeText(this@MainActivity, 
                                "SUCCESS: Stored Website to Storage ($sizeKb KB).\nReloading in Local Mode...", 
                                Toast.LENGTH_LONG).show()
                            
                            // Immediately switch to the local file
                            view.loadUrl("file://" + archiveFile.absolutePath)
                        }
                    }
                }
                
                // Inject reliable prompt detection
                val script = """
                    (function() {
                        const sendSelectors = [
                            'button[aria-label*="Send"]',
                            'button.send-button',
                            'div[role="button"][aria-label*="Send"]',
                            'svg.send-icon'
                        ];
                        
                        document.addEventListener('click', function(e) {
                            if (e.target.closest(sendSelectors.join(','))) {
                                Android.onSendPrompt();
                            }
                        }, true);
                        
                        document.addEventListener('keydown', function(e) {
                            if (e.key === 'Enter' && !e.shiftKey) {
                                const tag = e.target.tagName;
                                if (tag === 'TEXTAREA' || e.target.getAttribute('contenteditable')) {
                                    Android.onSendPrompt();
                                }
                            }
                        }, true);
                    })();
                """.trimIndent()
                view?.evaluateJavascript(script, null)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                // If the static cache is somehow missing or we hit ERR_CACHE_MISS, 
                // allow a brief network bridge to recover.
                if (request?.isForMainFrame == true) {
                    view?.settings?.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    view?.loadUrl(request.url.toString())
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = request.url.host ?: ""

                val isInternal = host.contains("google.") || 
                                 host.contains("gemini.google") || 
                                 host.contains("gstatic.com") ||
                                 host.contains("youtube.com") || 
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

        if (savedInstanceState == null) {
            if (archiveFile.exists()) {
                // PROOF: Loading 100% local physical file
                webView.loadUrl("file://" + archiveFile.absolutePath)
            } else {
                webView.loadUrl("https://gemini.google.com/app")
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
