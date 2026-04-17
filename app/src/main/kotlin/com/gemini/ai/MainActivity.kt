package com.gemini.ai

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Root layout
        val rootLayout = FrameLayout(this)
        rootLayout.id = View.generateViewId()
        
        // WebView - Immediate visibility for cached content
        webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        webView.visibility = View.VISIBLE 
        rootLayout.addView(webView)
        
        // Progress Bar (Subtle at the very top)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            6
        )
        progressBar.progressDrawable.setTint(android.graphics.Color.parseColor("#4285F4"))
        rootLayout.addView(progressBar)
        
        setContentView(rootLayout)

        // Persistent Login Configuration
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Immersive Mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, rootLayout)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Keyboard Handling
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            
            val params = webView.layoutParams as FrameLayout.LayoutParams
            if (imeVisible) {
                params.bottomMargin = imeHeight
            } else {
                params.bottomMargin = systemBars
            }
            webView.layoutParams = params
            insets
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // EXTREME CACHE PERSISTENCE: Download and store 100% of everything encountered.
            // This enables the "Instant Static" feel by reusing cached data without checking the network.
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            // Additional Storage & Persistence Settings
            saveFormData = true
            setGeolocationEnabled(true)
            
            // Modern Device Profile for Pixel 8 Pro
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
            
            // Performance Boosters - Allow Background JS to pre-process
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                // Persist all data for next "Instant" launch
                CookieManager.getInstance().flush()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = request.url.host ?: ""

                // EXTREME LOGIN PROTECTION: If it's ANY Google-related domain or auth keyword, STAY in app.
                // This covers all regional domains (google.com.br, google.fr, etc.) and auth redirects.
                val isGoogleRelated = host.contains("google") || 
                                     host.contains("gemini") || 
                                     host.contains("gstatic") ||
                                     url.contains("signin") ||
                                     url.contains("auth") ||
                                     url.contains("login") ||
                                     url.contains("AccountChooser") ||
                                     url.contains("identifier")

                if (isGoogleRelated) {
                    return false // Stay in app 100%
                } else {
                    // Truly external links (non-Google) go to browser
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        return false
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl("https://gemini.google.com")
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
