package com.gemini.ai

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
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

        // UI Setup: Edge-to-edge and Transparency
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(Color.TRANSPARENT)
        }
        rootLayout.addView(webViewLive)

        // 1. BACK BUTTON LOGIC: Handle internal history vs app exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webViewLive.canGoBack()) {
                    webViewLive.goBack()
                } else {
                    // Disable this callback so the next back press exits the app
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // 2. KEYBOARD & STATUS BAR INSETS: Prevents UI overlapping
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            webViewLive.layoutParams = (webViewLive.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = statusBars.top
                bottomMargin = imeBottom
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
            // Allow cookies for login persistence
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // JS Bridge for Status Bar Color Sync
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onColorDetected(rgbStr: String) {
                val newColor = parseRgb(rgbStr)
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

        // 3. URL HANDLING: Keep Google/Login inside, send Reddit/External out
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                
                // Whitelist Google-owned domains to ensure Login and Gemini work
                val isInternal = url.contains("google.com") || 
                                 url.contains("gstatic.com") || 
                                 url.contains("google.ac") ||
                                 url.contains("youtube.com") // Required for SID login sync

                return if (isInternal) {
                    false // Stay in WebView
                } else {
                    // Open Reddit and other external links in default browser
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        true 
                    } catch (e: Exception) {
                        false // Fallback to WebView if browser is unavailable
                    }
                }
            }

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
                    requestAnimationFrame(() => {
                        const bg = window.getComputedStyle(document.body).backgroundColor;
                        if (bg && bg !== lastSent && bg !== 'transparent' && bg !== 'rgba(0,0,0,0)') {
                            lastSent = bg;
                            ColorBridge.onColorDetected(bg);
                        }
                    });
                };
                const observer = new MutationObserver(window.syncColor);
                observer.observe(document.documentElement, { attributes: true, subtree: true, childList: true });
                setInterval(window.syncColor, 100);
                window.syncColor();
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun updateStatusBar(bgColor: Int) {
        rootLayout.setBackgroundColor(bgColor)
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
}
