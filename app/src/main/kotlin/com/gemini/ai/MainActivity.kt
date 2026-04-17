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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // Bridge for JS to notify Android of prompt sending
    class WebAppInterface(private val mainActivity: MainActivity) {
        @JavascriptInterface
        fun onSendPrompt() {
            mainActivity.runOnUiThread {
                mainActivity.enableNetworkForSession()
            }
        }
    }

    /**
     * Enables network access permanently for the current session 
     * after the user sends the first prompt.
     */
    fun enableNetworkForSession() {
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
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
            
            // 100% OFFLINE FIRST: Start by strictly using the pre-downloaded site
            // This prevents "Webpage not available" on start if internet is off.
            cacheMode = WebSettings.LOAD_CACHE_ONLY
            
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
                // If the static cache is somehow missing (first run), allow the internet to "Seed" it.
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
            webView.loadUrl("https://gemini.google.com/app")
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
