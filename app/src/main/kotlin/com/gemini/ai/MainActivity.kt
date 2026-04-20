package com.gemini.ai

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
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
        enableEdgeToEdge() // Standard Edge-to-Edge
        super.onCreate(savedInstanceState)
        
        val rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        window.statusBarColor = Color.TRANSPARENT

        webViewLive = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = View.INVISIBLE 
        }
        rootLayout.addView(webViewLive)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 12)
            max = 100
            visibility = View.GONE
        }
        rootLayout.addView(progressBar)

        // UI FIX: Apply top padding to webview so content clears status bar icons
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

            val progressParams = progressBar.layoutParams as FrameLayout.LayoutParams
            progressParams.topMargin = statusBars.top
            progressBar.layoutParams = progressParams

            // THIS FIXES THE OVERLAP IN YOUR SCREENSHOT
            webViewLive.setPadding(0, statusBars.top, 0, 0)
            
            val webParams = webViewLive.layoutParams as FrameLayout.LayoutParams
            webParams.topMargin = 0 
            webParams.bottomMargin = if (imeVisible) imeHeight else systemBars.bottom
            webViewLive.layoutParams = webParams

            insets
        }

        setupWebView(webViewLive)
        setupNetworkObserver()

        if (savedInstanceState == null) {
            webViewLive.loadUrl("https://gemini.google.com/app")
        } else {
            webViewLive.restoreState(savedInstanceState)
        }
    }

    private fun setupNetworkObserver() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    if (webViewLive.url == null || !webViewLive.url!!.contains("google.com")) {
                        webViewLive.loadUrl("https://gemini.google.com/app")
                    } else {
                        webViewLive.reload()
                    }
                }
            }
        })
    }

    private fun setupWebView(wv: WebView) {
        GeminiWebViewManager.configureGeminiWebView(wv)
        
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                progressBar.progress = newProgress
            }

            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                try {
                    filePickerLauncher.launch(fileChooserParams?.createIntent())
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
                wv.visibility = View.VISIBLE

                // REDIRECT FIX: If stuck on YouTube SetSID or MyAccount, kick back to Gemini
                if (url != null && (url.contains("myaccount.google.com") || url.contains("accounts.youtube.com")) && !url.contains("SetSID") && !url.contains("SetOSID")) {
                    wv.loadUrl("https://gemini.google.com/app")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = request.url.host?.lowercase() ?: ""

                // THE FIX: Allow ALL google/youtube auth domains to remain internal
                val isInternalAuth = host.contains("google.com") || 
                                     host.contains("youtube.com") || 
                                     host.contains("gstatic.com") || 
                                     host.contains("googleapis.com")

                if (isInternalAuth) {
                    return false 
                }

                // Handle other app intents (External apps)
                try {
                    val intent = if (url.startsWith("intent://")) {
                        Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    }

                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                        return true
                    }
                    
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (fallbackUrl != null) {
                        view?.loadUrl(fallbackUrl)
                        return true
                    }
                } catch (e: Exception) {
                    return false
                }
                return false
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
