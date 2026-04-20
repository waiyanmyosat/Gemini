package com.gemini.ai

import android.annotation.SuppressLint
import android.content.Intent
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

        // 1. Enable Edge-to-Edge and Transparent Status Bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        // 2. Setup Native Root Layout (Acts as the status bar background)
        rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        // 3. Initialize WebView
        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(webViewLive)

        // 4. Initialize Progress Bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 12).apply {
                gravity = Gravity.TOP
            }
            visibility = View.GONE
        }
        rootLayout.addView(progressBar)

        // 5. Native Inset Handling (The "0ms Sync" Secret)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            // Push WebView content down natively so it never overlaps icons
            val webParams = webViewLive.layoutParams as FrameLayout.LayoutParams
            webParams.topMargin = statusBars.top
            webParams.bottomMargin = ime.bottom
            webViewLive.layoutParams = webParams

            // Position progress bar exactly under status bar
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

        // 6. JavaScript Bridge for Real-time Color & Icon Contrast
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onColorDetected(rgbStr: String) {
                runOnUiThread {
                    try {
                        val color = parseRgb(rgbStr)
                        updateSystemUI(color)
                    } catch (e: Exception) {
                        // Fallback: Check system theme if detection fails
                        val isDark = (resources.configuration.uiMode and 
                                     android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                                     android.content.res.Configuration.UI_MODE_NIGHT_YES
                        updateSystemUI(if (isDark) Color.BLACK else Color.WHITE)
                    }
                }
            }
        }, "ColorBridge")

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                // Early injection to get color as soon as DOM starts rendering
                if (newProgress > 15) injectColorDetector(view)
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
                function sendColor() {
                    var bg = window.getComputedStyle(document.body).backgroundColor;
                    // Ensure we don't send transparent/null backgrounds
                    if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
                        ColorBridge.onColorDetected(bg);
                    }
                }
                sendColor();
                // Real-time Mutation Observer: Detects theme toggles without reload
                new MutationObserver(sendColor).observe(document.documentElement, { 
                    attributes: true, 
                    attributeFilter: ['class', 'style', 'data-theme'] 
                });
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun updateSystemUI(bgColor: Int) {
        // Match the native container background to the website instantly
        rootLayout.setBackgroundColor(bgColor)
        webViewLive.setBackgroundColor(bgColor)
        
        // Luminance Detection: Calculate if background is Light or Dark
        val r = Color.red(bgColor)
        val g = Color.green(bgColor)
        val b = Color.blue(bgColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // If luminance > 0.5 (Light background), show DARK icons. Else show WHITE icons.
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
