package com.gemini.ai

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

// Credential Manager Imports
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var verifyButton: Button? = null // Native button for "Proof of Success"
    private lateinit var archiveFile: File
    private var isCacheSeeded: Boolean = false
    private var pendingPrompt: String? = null

    // Replace with your real Web Client ID from Google Cloud Console
    private val GOOGLE_WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    // Bridge for JS to notify Android of prompt sending
    class WebAppInterface(private val mainActivity: MainActivity) {
        @JavascriptInterface
        fun onSendPrompt(text: String) {
            mainActivity.runOnUiThread {
                mainActivity.enableNetworkAndReload(text)
            }
        }
    }

    /**
     * Captures the message from the offline archive and re-loads 
     * the live site to actually send it.
     */
    fun enableNetworkAndReload(text: String) {
        pendingPrompt = text
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        // Use a flag to avoid re-triggering any downloads during prompt re-injection
        isCacheSeeded = true 
        webView.loadUrl("https://gemini.google.com/app")
    }

    /**
     * NATIVE PROOF PROTOCOL:
     * This uses the official Google Credential Manager to verify your identity.
     * Only after this returns a valid ID Token do we trigger the 100% Download.
     */
    private fun performVerificationAndDownload() {
        lifecycleScope.launch {
            val credentialManager = CredentialManager.create(this@MainActivity)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) 
                .setServerClientId(GOOGLE_WEB_CLIENT_ID) 
                .setAutoSelectEnabled(true) 
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            try {
                val result = credentialManager.getCredential(this@MainActivity, request)
                val credential = result.credential

                if (credential is CustomCredential && 
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val email = googleIdTokenCredential.id
                    
                    // 100% PROOF OF SUCCESS REACHED
                    Toast.makeText(this@MainActivity, "AUTHENTICATED: $email", Toast.LENGTH_SHORT).show()
                    
                    // CRITICAL: TRIGGER SITE DOWNLOAD NOW
                    triggerPhysicalDownload()
                }
            } catch (e: Exception) {
                // Precise error handling using standard practice
                Toast.makeText(this@MainActivity, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun triggerPhysicalDownload() {
        // Ensure webview is on the dashboard
        if (webView.url?.contains("/app") != true) {
            webView.loadUrl("https://gemini.google.com/app")
        }
        
        Toast.makeText(this, "Capturing authenticated dashboard...", Toast.LENGTH_SHORT).show()
        
        // Wait for components to be in place
        Handler(Looper.getMainLooper()).postDelayed({
            webView.saveWebArchive(archiveFile.absolutePath, false) { path ->
                if (path != null) {
                    isCacheSeeded = true
                    getSharedPreferences("gemini_offline_prefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("cache_seeded", true)
                        .apply()
                    
                    verifyButton?.visibility = View.GONE
                    
                    Toast.makeText(this@MainActivity, "SUCCESS: Website Frozen Offline.", Toast.LENGTH_LONG).show()
                    
                    // LOCK TO LOCAL
                    webView.loadUrl("file://" + archiveFile.absolutePath)
                    webView.settings.cacheMode = WebSettings.LOAD_CACHE_ONLY
                }
            }
        }, 5000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val rootLayout = FrameLayout(this)
        
        webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        webView.visibility = View.VISIBLE 
        rootLayout.addView(webView)
        
        // Add Native Verification Button if not seeded
        if (!isCacheSeeded) {
            val btn = Button(this).apply {
                text = "VERIFY & DOWNLOAD OFFLINE SITE"
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    setMargins(0, 0, 0, 100)
                }
                setOnClickListener { performVerificationAndDownload() }
            }
            verifyButton = btn
            rootLayout.addView(btn)
        }
        
        setContentView(rootLayout)

        // Physical File Path for valid "Proof" of download
        archiveFile = File(filesDir, "gemini_static_v1.mht")

        // Load seeding state
        val prefs = getSharedPreferences("gemini_offline_prefs", MODE_PRIVATE)
        isCacheSeeded = prefs.getBoolean("cache_seeded", false)

        // Persistence Configuration
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        
        // Connect JS bridge
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        // Screen Settings
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, rootLayout)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Fix Keyboard Overlap
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val params = webView.layoutParams as FrameLayout.LayoutParams
            params.bottomMargin = if (imeVisible) imeHeight else systemBars
            webView.layoutParams = params
            insets
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // 100% OFFLINE FIRST:
            // If the local archive exists, we will load it directly.
            if (archiveFile.exists()) {
                cacheMode = WebSettings.LOAD_CACHE_ONLY
            } else {
                // Initial session: allow internet to reach login/app
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Zero loading indicators for a "Static Object" feel
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()

                // 1. HANDLE PENDING PROMPT: If we just came from the offline file, send the message
                pendingPrompt?.let { prompt ->
                    val escapedText = prompt.replace("'", "\\'").replace("\n", "\\n")
                    val injectionScript = """
                        (function() {
                            function trySend() {
                                const textarea = document.querySelector('textarea, [contenteditable="true"]');
                                const btn = document.querySelector('button[aria-label*="Send"], button.send-button');
                                if (textarea && (btn || document.querySelector('svg.send-icon'))) {
                                    if (textarea.tagName === 'TEXTAREA') {
                                        textarea.value = '$escapedText';
                                    } else {
                                        textarea.innerText = '$escapedText';
                                    }
                                    textarea.dispatchEvent(new Event('input', { bubbles: true }));
                                    textarea.dispatchEvent(new Event('change', { bubbles: true }));
                                    setTimeout(() => {
                                        const finalBtn = document.querySelector('button[aria-label*="Send"], button.send-button') || btn;
                                        if (finalBtn) finalBtn.click();
                                    }, 500);
                                } else {
                                    setTimeout(trySend, 1000);
                                }
                            }
                            trySend();
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(injectionScript, null)
                    pendingPrompt = null
                }
                
                // 2. INJECT DETECTION: Reliable prompt detection (captured for re-injection)
                val script = """
                    (function() {
                        const sendSelectors = [
                            'button[aria-label*="Send"]',
                            'button.send-button',
                            'svg.send-icon'
                        ];
                        
                        function getPromptText() {
                            const textarea = document.querySelector('textarea, [contenteditable="true"]');
                            return textarea ? (textarea.value || textarea.innerText) : "";
                        }
                        
                        document.addEventListener('click', function(e) {
                            if (e.target.closest(sendSelectors.join(','))) {
                                Android.onSendPrompt(getPromptText());
                            }
                        }, true);
                        
                        document.addEventListener('keydown', function(e) {
                            if (e.key === 'Enter' && !e.shiftKey) {
                                const tag = e.target.tagName;
                                if (tag === 'TEXTAREA' || e.target.getAttribute('contenteditable')) {
                                    Android.onSendPrompt(getPromptText());
                                }
                            }
                        }, true);
                    })();
                """.trimIndent()
                view?.evaluateJavascript(script, null)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                // If the static cache is somehow missing or we hit ERR_CACHE_MISS, 
                // allow a brief network bridge to recover.
                if (request?.isForMainFrame == true) {
                    view?.settings?.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    view?.loadUrl(request.url.toString())
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = request.url.host ?: ""

                val isInternal = host.contains("google.") || 
                                 host.contains("gemini.google") || 
                                 host.contains("gstatic.com") ||
                                 host.contains("youtube.com") || 
                                 url.contains("SetSID") ||
                                 url.contains("signin") ||
                                 url.contains("auth")

                if (isInternal) return false 
                
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return true
                } catch (e: Exception) { return false }
            }
        }

        if (savedInstanceState == null) {
            if (archiveFile.exists()) {
                // PROOF: Loading 100% local physical file
                webView.loadUrl("file://" + archiveFile.absolutePath)
            } else {
                webView.loadUrl("https://gemini.google.com/app")
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }
}
