package com.gemini.ai

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.Gravity
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webViewLive: WebView
    private lateinit var rootLayout: FrameLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Force the Window to be Edge-to-Edge and Transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        rootLayout = FrameLayout(this)
        syncWithSystemNow() // Initial color match
        setContentView(rootLayout)

        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            // CRITICAL: Transparent background for 0ms visual color bleed
            setBackgroundColor(Color.TRANSPARENT) 
        }
        rootLayout.addView(webViewLive)

        // Physical Layout Sync (Ensures website starts below the status bar)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            webViewLive.layoutParams = (webViewLive.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = statusBars.top
                bottomMargin = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            }
            insets
        }

        setupWebView(webViewLive)
        if (savedInstanceState == null) webViewLive.loadUrl("https://gemini.google.com/app")
    }

    // 2. THE QUICK SETTING TRIGGER (0ms Response)
    // This function captures the System Tile toggle without restarting the app
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Update the native background and icons immediately
        syncWithSystemNow()
        
        // Force the website to report its new theme color right now
        webViewLive.evaluateJavascript("if(window.sendColor) window.sendColor();", null)
    }

    private fun syncWithSystemNow() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // Instant match for Gemini's primary theme colors
        updateTheme(if (isDark) Color.parseColor("#131313") else Color.WHITE)
    }

    private fun setupWebView(wv: WebView) {
        GeminiWebViewManager.configureGeminiWebView(wv)
        
        // The Bridge: Receives the color from the website
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onColorDetected(rgbStr: String) {
                runOnUiThread { try { updateTheme(parseRgb(rgbStr)) } catch (e: Exception) {} }
            }
        }, "ColorBridge")

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress > 15) injectColorDetector(view)
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { injectColorDetector(view) }
        }
    }

    private fun injectColorDetector(view: WebView?) {
        val script = """
            (function() {
                window.sendColor = function() {
                    var bg = window.getComputedStyle(document.body).backgroundColor;
                    if (bg && bg !== 'rgba(0, 0, 0, 0)') {
                        ColorBridge.onColorDetected(bg);
                    }
                };
                window.sendColor();
                // Watch for the website changing its own theme internally
                if (!window.themeObserved) {
                    new MutationObserver(window.sendColor).observe(document.documentElement, { 
                        attributes: true, attributeFilter: ['class', 'style', 'data-theme'] 
                    });
                    window.themeObserved = true;
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun updateTheme(bgColor: Int) {
        // Update background color instantly
        rootLayout.setBackgroundColor(bgColor)
        
        // Update icon contrast instantly (prevents icons from disappearing)
        val r = Color.red(bgColor); val g = Color.green(bgColor); val b = Color.blue(bgColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = (luminance > 0.5) // Light bg = Dark icons
        }
    }

    private fun parseRgb(rgb: String): Int {
        val v = rgb.replace(Regex("[^0-9,]"), "").split(",")
        return Color.rgb(v[0].toInt(), v[1].toInt(), v[2].toInt())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webViewLive.canGoBack()) {
            webViewLive.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
} 
