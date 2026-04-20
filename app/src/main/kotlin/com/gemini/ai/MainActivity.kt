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
        // Initial fallback color based on system
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        rootLayout.setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)
        setContentView(rootLayout)

        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Make WebView transparent so rootLayout color shows through while loading
            setBackgroundColor(Color.TRANSPARENT)
        }
        rootLayout.addView(webViewLive)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 12).apply {
                gravity = Gravity.TOP
            }
            visibility = View.GONE
        }
        rootLayout.addView(progressBar)

        // 2. Native Inset Management (0ms Content Offset)
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

    private fun setupWebView(wv: WebView) {
        GeminiWebViewManager.configureGeminiWebView(wv)

        // 3. Real-time Bridge (Crucial for "No Restart" Sync)
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
                // Inject early and often to catch theme changes
                if (newProgress > 10) injectColorDetector(view)
            }
            override fun onShowFileChooser(view: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                filePathCallback = cb
                filePickerLauncher.launch(p?.createIntent())
                return true
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
                var lastDetectedColor = "";
                function sendColor() {
                    var bg = window.getComputedStyle(document.body).backgroundColor;
                    if (bg && bg !== lastDetectedColor && bg !== 'rgba(0, 0, 0, 0)') {
                        lastDetectedColor = bg;
                        ColorBridge.onColorDetected(bg);
                    }
                }
                sendColor();
                // This observer watches for class changes on the HTML tag (Gemini's dark mode trigger)
                new MutationObserver(sendColor).observe(document.documentElement, { 
                    attributes: true, 
                    attributeFilter: ['class', 'style', 'data-theme'] 
                });
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun updateSystemUI(bgColor: Int) {
        // 4. Update Native Background (0ms sync)
        rootLayout.setBackgroundColor(bgColor)
        
        // 5. Luminance Detection for Icon Visibility
        val r = Color.red(bgColor)
        val g = Color.green(bgColor)
        val b = Color.blue(bgColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // If site is Light (high luminance), set Dark Icons. Else set White Icons.
        controller.isAppearanceLightStatusBars = (luminance > 0.5)
    }

    private fun parseRgb(rgb: String): Int {
        val values = rgb.replace("rgb", "").replace("a", "")
                        .replace("(", "").replace(")", "")
                        .split(",")
        val r = values[0].trim().toInt()
        val g = values[1].trim().toInt()
        val b = values[2].trim().toInt()
        return Color.rgb(r, g, b)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webViewLive.canGoBack()) {
            webViewLive.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
} 
