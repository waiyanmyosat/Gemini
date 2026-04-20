package com.gemini.ai

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webViewLive: WebView
    private lateinit var rootLayout: FrameLayout
    private var lastSyncedColor: Int = Color.TRANSPARENT

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(Color.TRANSPARENT)
        }
        rootLayout.addView(webViewLive)

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        webViewLive.evaluateJavascript("if(window.syncColor) window.syncColor();", null)
    }

    private fun setupWebView(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
        }

        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onColorDetected(rgbStr: String) {
                val newColor = parseRgb(rgbStr)
                // Only bridge to UI thread if color actually changed
                if (newColor != lastSyncedColor) {
                    lastSyncedColor = newColor
                    runOnUiThread { updateStatusBar(newColor) }
                }
            }
        }, "ColorBridge")

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress > 25) injectRealTimeListener(view)
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectRealTimeListener(view)
            }
        }
    }

    private fun injectRealTimeListener(view: WebView?) {
        val script = """
            (function() {
                if (window.isListening) return;
                window.isListening = true;

                let lastSent = "";
                window.syncColor = function() {
                    // requestAnimationFrame is the gold standard for high-refresh displays
                    requestAnimationFrame(() => {
                        const bg = window.getComputedStyle(document.body).backgroundColor;
                        if (bg && bg !== lastSent && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') {
                            lastSent = bg;
                            ColorBridge.onColorDetected(bg);
                        }
                    });
                };

                // Instant trigger on any DOM change
                const observer = new MutationObserver(window.syncColor);
                observer.observe(document.documentElement, { 
                    attributes: true, 
                    subtree: true, 
                    childList: true 
                });

                // 8ms interval for 120Hz displays (High-speed fallback)
                setInterval(window.syncColor, 8);
                window.syncColor();
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun updateStatusBar(bgColor: Int) {
        rootLayout.setBackgroundColor(bgColor)
        
        // Optimized Luminance check
        val r = Color.red(bgColor)
        val g = Color.green(bgColor)
        val b = Color.blue(bgColor)
        val luminance = (r * 0.299 + g * 0.587 + b * 0.114) / 255
        
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = (luminance > 0.5)
        }
    }

    private fun parseRgb(rgb: String): Int {
        return try {
            val vals = rgb.substringAfter("(").substringBefore(")").split(",")
            Color.rgb(vals[0].trim().toInt(), vals[1].trim().toInt(), vals[2].trim().toInt())
        } catch (e: Exception) {
            Color.BLACK
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webViewLive.canGoBack()) {
            webViewLive.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
