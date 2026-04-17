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
    // 1. PRE-CONNECT & PRERENDER (Call this in your Application class or Splash)
    // -------------------------------------------------------------------------
    fun warmUp(context: Context) {
        // Pre-connect to DNS/Socket level
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROFILE_PRECONNECT)) {
            try {
                val profile = WebViewCompat.getDefaultProfile(context)
                profile.preconnect(Uri.parse(GEMINI_URL), 1)
            } catch (e: Exception) {}
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
            } catch (e: Exception) {}
        }
    }

    // -------------------------------------------------------------------------
    // 2. OPTIMIZED CONFIGURATION (Apply to your WebView instance)
    // -------------------------------------------------------------------------
    fun configureSettings(webView: WebView) {
        // Enable Hardware Acceleration via LayoutParams if not already in Manifest
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Session & Cookie Persistence (Critical for avoiding login delays)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            // Core Performance
            javaScriptEnabled = true
            domStorageEnabled = true // Required for Gemini's state
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            
            // Resource Loading
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_ALWAYS
            
            // Speed up rendering
            loadsImagesAutomatically = true
            blockNetworkImage = false

            // Standard Browser properties
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false

            // Pop-up support
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        }
    }
}
