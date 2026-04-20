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
    private lateinit var progressBar: ProgressBar
    private lateinit var rootLayout: FrameLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. FORCED NATIVE TRANSPARENCY (Bypasses themes.xml)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        rootLayout = FrameLayout(this)
        // Instant sync on boot
        syncUiToSystemTheme() 
        setContentView(rootLayout)

        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            // CRITICAL: Transparent background lets rootLayout color show through instantly
            setBackgroundColor(Color.TRANSPARENT) 
        }
        rootLayout.addView(webViewLive)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(-1, 12).apply { gravity = Gravity.TOP }
            visibility = android.view.View.GONE
        }
        rootLayout.addView(progressBar)

        // 2. PHYSICAL INSET MANAGEMENT (The 0ms Gap)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            // This pushes the content below the status bar physically
            webViewLive.layoutParams = (webViewLive.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = statusBars.top
                bottomMargin = ime.bottom
            }
            progressBar.layoutParams = (progressBar.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = statusBars.top
            }
            insets
        }

        setupWebView(webViewLive)
        if (savedInstanceState == null) webViewLive.loadUrl("https://gemini.google.com/app")
    }

    // 3. THE NO-RESTART HANDLER (Quick Settings Toggle)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Update native background and icons immediately
        syncUiToSystemTheme()
        // Shouts at the website to tell us the new color if it changed
        webViewLive.evaluateJavascript("if(window.sendColor) window.sendColor();", null)
    }

    private fun syncUiToSystemTheme() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // Gemini's specific dark hex: #131313
        updateStatusBar(if (isDark) Color.parseColor("#131313") else Color.WHITE)
    }

    private fun setupWebView(wv: WebView) {
        GeminiWebViewManager.configureGeminiWebView(wv)
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onColorDetected(rgbStr: String) {
                runOnUiThread { try { updateStatusBar(parseRgb(rgbStr)) } catch (e: Exception) {} }
            }
        }, "ColorBridge")

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) android.view.View.VISIBLE else android.view.View.GONE
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

    private fun updateStatusBar(bgColor: Int) {
        rootLayout.setBackgroundColor(bgColor)
        // Icon visibility logic
        val r = Color.red(bgColor); val g = Color.green(bgColor); val b = Color.blue(bgColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = (luminance > 0.5)
    }

    private fun parseRgb(rgb: String): Int {
        val v = rgb.replace(Regex("[^0-9,]"), "").split(",")
        return Color.rgb(v[0].toInt(), v[1].toInt(), v[2].toInt())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webViewLive.canGoBack()) { webViewLive.goBack(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
