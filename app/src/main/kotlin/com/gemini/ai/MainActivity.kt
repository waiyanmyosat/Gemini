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
        
        // Root layout to hold WebView and ProgressBar
        val rootLayout = FrameLayout(this)
        rootLayout.id = View.generateViewId()
        
        webView = WebView(this)
        // Ensure webview layout params are set for matching parent
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        rootLayout.addView(webView)
        
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            12 // Slightly taller for visibility
        )
        progressBar.progressDrawable.setTint(resources.getColor(android.R.color.holo_blue_light, theme))
        rootLayout.addView(progressBar)
        
        setContentView(rootLayout)

        // Login Persistence: Enable Persistent Cookies
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Hide status bar and navigation bars (Immersive Mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, rootLayout)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Fix Keyboard Overlap (Issue #1) - Using Margins instead of Padding for better compatibility
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            
            val params = webView.layoutParams as FrameLayout.LayoutParams
            if (imeVisible) {
                // When keyboard is up, we should NOT be in immersive mode for the navigation bar area
                // to allow the keyboard to measure correctly, but here we just adjust bottom margin
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
            
            // Performance & Persistence Optimizations
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            // Modern User Agent
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 95) View.GONE else View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                // Persist cookies after page load
                CookieManager.getInstance().flush()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = request.url.host ?: ""

                // Define internal domains that stay inside the app
                // Broadened Google Login matching to catch redirections during "Next" clicks
                val isInternal = host.contains("gemini.google.com") || 
                                host.contains("accounts.google") || 
                                host.contains("myaccount.google") ||
                                host.contains("google.com/accounts") ||
                                url.contains("/ServiceLogin") ||
                                url.contains("/InteractiveLogin") ||
                                host.endsWith("googleusercontent.com")

                if (isInternal) {
                    return false // Load in WebView
                } else {
                    // External link: Open in default browser
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        return true // WebView shouldn't load this
                    } catch (e: Exception) {
                        return false // Fallback if browser can't be opened
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
