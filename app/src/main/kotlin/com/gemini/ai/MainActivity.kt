package com.gemini.ai

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
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
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure the layout doesn't crawl under the status bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        
        val rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(webViewLive)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 12).apply {
                gravity = android.view.Gravity.TOP
            }
            visibility = android.view.View.GONE
        }
        rootLayout.addView(progressBar)

        // Fix the overlap (Padding at top for Status Bar, Margin at bottom for Keyboard)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            view.setPadding(0, statusBars.top, 0, 0)
            
            val params = webViewLive.layoutParams as FrameLayout.LayoutParams
            params.bottomMargin = ime.bottom
            webViewLive.layoutParams = params
            
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
        
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) android.view.View.VISIBLE else android.view.View.GONE
            }

            override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                filePathCallback = callback
                filePickerLauncher.launch(params?.createIntent())
                return true
            }
        }
        
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val url = uri.toString()
                val host = uri.host?.lowercase() ?: ""

                // THE BROADER LOGIN FILTER
                // This catches gemini, accounts, and the youtube/myaccount session handovers
                val isInternal = host.contains("gemini.google.com") || 
                                 host.contains("accounts.google.com") ||
                                 host.contains("accounts.youtube.com") ||
                                 host.contains("myaccount.google.com") ||
                                 url.contains("SetSID")

                return if (isInternal) {
                    false // LOAD INTERNALLY (This fixes the logout/redirect loop)
                } else {
                    // EXTERNAL: Open actual links/sources in the browser
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        startActivity(intent)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                // Critical for keeping the session active
                CookieManager.getInstance().flush()
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
} 
