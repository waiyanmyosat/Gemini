package com.gemini.ai

import android.content.Context
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewOutcomeReceiver
import java.util.concurrent.Executors

/**
 * Consolidated WebView Optimization for Gemini
 * Target: Near-instant initial launch
 */
object GeminiWebViewManager {

    private const val GEMINI_URL = "https://gemini.google.com/app"
    private val executor = Executors.newSingleThreadExecutor()

    // -------------------------------------------------------------------------
    // 1. PRE-CONNECT & PRERENDER
    // -------------------------------------------------------------------------
    fun warmUp(context: Context) {
        // Pre-connect to DNS/Socket level
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROFILE_PRECONNECT)) {
            try {
                val profile = WebViewCompat.getDefaultProfile(context)
                profile.preconnect(Uri.parse(GEMINI_URL), 1)
            } catch (e: Exception) {
                // Feature check might pass but impl might fail on some devices
            }
        }

        // Prerender the URL in the background (Android 11+)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PRERENDER_WITH_URL)) {
            try {
                WebViewCompat.prerenderUrlAsync(
                    context,
                    GEMINI_URL,
                    null,
                    executor,
                    object : WebViewOutcomeReceiver<Void?, Throwable> {
                        override fun onResult(result: Void?) { /* Prerendering logic active */ }
                        override fun onError(error: Throwable) { /* Handle error or fallback */ }
                    }
                )
            } catch (e: Exception) {
                // Fallback gracefully
            }
        }
    }

    // -------------------------------------------------------------------------
    // 2. OPTIMIZED CONFIGURATION
    // -------------------------------------------------------------------------
    fun configureGeminiWebView(webView: WebView) {
        // Enable Hardware Acceleration explicitly
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Session & Cookie Persistence
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            
            // Performance oriented resource loading
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            
            loadsImagesAutomatically = true
            blockNetworkImage = false
            
            // Typical mobile optimized settings
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        }
    }
}
