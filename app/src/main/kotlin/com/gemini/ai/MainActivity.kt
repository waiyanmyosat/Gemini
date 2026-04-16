package com.gemini.ai

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ultra-immersive: Hide system bars at the window level
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            GeminiApp()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun GeminiApp() {
        var isLoading by remember { mutableStateOf(true) }
        var appLoadedOnce by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            
            // Native "Instant" Layer to avoid flicker
            AnimatedVisibility(
                visible = isLoading && !appLoadedOnce,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier.fillMaxSize().zIndex(2f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    CircularProgressIndicator(color = Color(0xFF4285F4), strokeWidth = 2.dp)
                }
            }

            // High Performance WebView Layer
            AndroidView(
                modifier = Modifier.fillMaxSize().zIndex(1f),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            
                            // Speed & Caching: Zero Network Latency where possible
                            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                            
                            // Performance Tuning
                            setRenderPriority(WebSettings.RenderPriority.HIGH)
                            javaScriptCanOpenWindowsAutomatically = true
                            
                            // Fullscreen Optimizations
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            
                            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                appLoadedOnce = true
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }
                        
                        loadUrl("https://gemini.google.com")
                        webView = this
                    }
                }
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView?.canGoBack() == true) {
            webView?.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView?.apply {
            load(null as String?) // Clear
            destroy()
        }
        super.onDestroy()
    }
}
