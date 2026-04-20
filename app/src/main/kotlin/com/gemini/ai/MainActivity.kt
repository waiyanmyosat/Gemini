package com.gemini.ai

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.Gravity
import android.webkit.*
import android.widget.FrameLayout
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
        setContentView(rootLayout)

        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(Color.TRANSPARENT) // Essential for 0ms color bleed
        }
        rootLayout.addView(webViewLive)

        // Physical Layout Sync (0ms Space Reservation)
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

    // 2. THE TRIGGER: Detects System Tile Toggle Instantly
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Shouts at the website: "The system changed, tell me your color NOW!"
        webViewLive.evaluateJavascript("if(window.syncColor) window.syncColor();", null)
    }

    private fun setupWebView(wv: WebView) {
        GeminiWebViewManager.configureGeminiWebView(wv)
        
        // The Bridge: Receives real-time color updates
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onColorDetected(rgbStr: String) {
                runOnUiThread { try { updateStatusBar(parseRgb(rgbStr)) } catch (e: Exception) {} }
            }
        }, "ColorBridge")

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress > 10) injectRealTimeListener(view)
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { injectRealTimeListener(view) }
        }
    }

    private fun injectRealTimeListener(view: WebView?) {
        val script = """
            (function() {
                if (window.isListening) return;
                window.isListening = true;

                window.syncColor = function() {
                    var bg = window.getComputedStyle(document.body).backgroundColor;
                    if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
                        ColorBridge.onColorDetected(bg);
                    }
                };

                // 1. Listen for DOM changes (Theme toggles)
                new MutationObserver(window.syncColor).observe(document.documentElement, { 
                    attributes: true, attributeFilter: ['class', 'style', 'data-theme'] 
                });

                // 2. Continuous Polling (The 0ms "Always Listening" Safety Net)
                // Checks every 100ms to ensure the status bar never falls behind
                setInterval(window.syncColor, 100);
                
                window.syncColor();
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun updateStatusBar(bgColor: Int) {
        // Change native background instantly
        rootLayout.setBackgroundColor(bgColor)
        
        // Instant Icon Contrast Flip (Luminance check)
        val r = Color.red(bgColor); val g = Color.green(bgColor); val b = Color.blue(bgColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = (luminance > 0.5)
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
