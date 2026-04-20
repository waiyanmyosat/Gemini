package com.gemini.ai

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Enable Edge-to-Edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        val rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        // 2. Initialize UI Components
        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = android.view.View.INVISIBLE 
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 8).apply {
                gravity = android.view.Gravity.TOP
            }
            max = 100
            visibility = android.view.View.GONE
        }

        rootLayout.addView(webViewLive)
        rootLayout.addView(progressBar)

        // 3. Handle Insets (Status Bar & Keyboard)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Apply Top Padding to avoid Status Bar overlap
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

            // Apply Bottom Margin to handle Navigation Bar and Keyboard
            val params = webViewLive.layoutParams as FrameLayout.LayoutParams
            val bottomPadding = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            
            if (params.bottomMargin != bottomPadding) {
                params.bottomMargin = bottomPadding
                webViewLive.layoutParams = params
            }

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
        // Ensure GeminiWebViewManager is defined in your project
        GeminiWebViewManager.configureGeminiWebView(wv)
        
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress < 100) android.view.View.VISIBLE else android.view.View.GONE
                progressBar.progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: return false
                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }
        
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                wv.visibility = android.view.View.VISIBLE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = request.url.host?.lowercase() ?: ""

                val isLogin = host == "accounts.google.com"
                val isGemini = host == "gemini.google.com"
                val isYoutubeAuth = host == "accounts.youtube.com" && url.contains("SetSID")

                return if (isLogin || isGemini || isYoutubeAuth) {
                    false 
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        true 
                    } catch (e: Exception) {
                        false 
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webViewLive.canGoBack()) {
            webViewLive.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webViewLive.saveState(outState)
    }
}
