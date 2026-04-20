package com.gemini.ai

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Gravity
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webViewLive: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var rootLayout: FrameLayout
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Transparent Edge-to-Edge Setup
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        rootLayout = FrameLayout(this)
        // Set initial background based on current system theme
        syncBackgroundToSystem()
        setContentView(rootLayout)

        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT) // Let rootLayout show through
        }
        rootLayout.addView(webViewLive)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 12).apply {
                gravity = Gravity.TOP
            }
            visibility = View.GONE
        }
        rootLayout.addView(progressBar)

        // 2. Native Inset Management (0ms Offset)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            val webParams = webViewLive.layoutParams as FrameLayout.LayoutParams
            webParams.topMargin = statusBars.top
            webParams.bottomMargin = ime.bottom
            webViewLive.layoutParams = webParams

            val progressParams = progressBar.layoutParams as FrameLayout.LayoutParams
            progressParams.topMargin = statusBars.top
            progressBar.layoutParams = progressParams
            
            insets
        }

        setupWebView(webViewLive)

        if (savedInstanceState == null) {
            webViewLive.loadUrl("https://gemini.google.com/app")
        } else {
            webViewLive.restoreState(savedInstanceState)
        }
    }

    // 3. THIS HANDLES QUICK SETTINGS TOGGLE (NO RESTART)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncBackgroundToSystem()
        // Trigger JS to check theme again in case website changed color too
        webViewLive.evaluateJavascript("if(typeof sendColor === 'function') sendColor();", null)
    }

    private fun syncBackgroundToSystem() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // Gemini's specific dark hex is #131313
        val color = if (isDark) Color.parseColor("#131313") else Color.WHITE
        updateSystemUI(color)
    }

    private fun setupWebView(wv: WebView) {
        GeminiWebViewManager.configureGeminiWebView(wv)

        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onColorDetected(rgbStr: String) {
                runOnUiThread {
                    try {
                        val color = parseRgb(rgbStr)
                        updateSystemUI(color)
                    } catch (e: Exception) { }
                }
            }
        }, "ColorBridge")

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                if (newProgress > 15) injectColorDetector(view)
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectColorDetector(view)
            }
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
                sendColor();
                new MutationObserver(sendColor).observe(document.documentElement, { 
                    attributes: true, 
                    attributeFilter: ['class', 'style', 'data-theme'] 
                });
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun updateSystemUI(bgColor: Int) {
        rootLayout.setBackgroundColor(bgColor)
        
        // Luminance Detection for Icon Contrast
        val r = Color.red(bgColor)
        val g = Color.green(bgColor)
        val b = Color.blue(bgColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = (luminance > 0.5)
    }

    private fun parseRgb(rgb: String): Int {
        val values = rgb.replace("rgb", "").replace("a", "")
                        .replace("(", "").replace(")", "")
                        .split(",")
        return Color.rgb(values[0].trim().toInt(), values[1].trim().toInt(), values[2].trim().toInt())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webViewLive.canGoBack()) {
            webViewLive.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
